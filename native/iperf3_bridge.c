// Copyright (c) 2026, Jerold Abramson
//
// This source code is licensed under the BSD 3-Clause License found in
// the LICENSE file in the root directory of this source tree.

/*
 * iperf3_bridge.c — core of the JNI/cinterop split (see iperf3_bridge.h and
 * IPERF3_NATIVE_INTEGRATION.md §8.4). This file owns test lifecycle,
 * background-thread management, stall detection, and the stderr
 * "warning: "-prefix filter — all logic that used to live directly in
 * native/iperf3_jni.c — but has zero JNI (or Kotlin/Native) types anywhere
 * in it. Every point where the original code called back into Kotlin via
 * raw JNI now goes through the struct IperfCallbacks function pointers
 * instead; native/iperf3_jni.c is the thin JNI adapter that implements
 * those function pointers for Android/desktop.
 */
#include "iperf3_bridge.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <pthread.h>
#include <limits.h>
#include <errno.h>
#include <time.h>
#include <getopt.h> /* optind, optreset (BSD/bionic) — see the getopt reset note below */
#include <signal.h>
#ifdef __GLIBC__
#include <stdio_ext.h> /* __fsetlocking — see openPipeForReading() */
#endif

#include "iperf.h"
#include "iperf_api.h"

#ifndef PATH_MAX
#define PATH_MAX 4096
#endif
static int g_debug_flag = 0;
static FILE *g_origStderr = NULL;

#if defined(__ANDROID__)
#include <android/log.h>
#define IPERF_LOG(...) if (g_debug_flag != 0) __android_log_print(ANDROID_LOG_INFO, "iperf3_bridge", __VA_ARGS__)
#else
#define IPERF_LOG(fmt, ...) \
    if (g_debug_flag != 0 && g_origStderr != NULL) fprintf(g_origStderr, "IPERF_LOG - iperf3_bridge: " fmt "\n" __VA_OPT__(,) __VA_ARGS__)
#endif

/* Single global test: this app never runs concurrent iperf3 tests. */
static struct iperf_test *global_test = NULL;
static pthread_t reader_thread;
static pthread_t stderr_reader_thread;
static pthread_t monitor_thread;
static volatile int stop_requested = 0;
static char g_temp_dir[PATH_MAX];
static int g_temp_dir_set = 0;

/* Seconds of no-progress (see monitorThreadFunc) before the watchdog aborts
 * a test that's already started transferring data. Settable via
 * iperf3_bridge_set_stall_timeout_seconds(); defaults to 5 if never
 * called. */
static int g_stall_timeout_seconds = 5;

struct ThreadArgs {
    const struct IperfCallbacks *callbacks;
    int pipe_fd; /* unused by the monitor thread */
};


void iperf3_bridge_set_debug(int debug) {
    IPERF_LOG("iperf3_bridge_set_debug: ENTER");
    g_debug_flag = debug;
    IPERF_LOG("iperf3_bridge_set_debug: EXIT");
}

void iperf3_bridge_set_temp_dir(const char *temp_dir) {
    IPERF_LOG("iperf3_bridge_set_temp_dir: ENTER");
    if (temp_dir == NULL) {
        g_temp_dir_set = 0;
        return;
    }
    strncpy(g_temp_dir, temp_dir, sizeof(g_temp_dir) - 1);
    g_temp_dir[sizeof(g_temp_dir) - 1] = '\0';
    g_temp_dir_set = (strlen(g_temp_dir) > 0);
    IPERF_LOG("iperf3_bridge_set_temp_dir: EXIT");
}

void iperf3_bridge_set_stall_timeout_seconds(int seconds) {
    if (seconds > 0) {
        g_stall_timeout_seconds = seconds;
    }
}

/* Called exactly once, at process/app startup — JNI_OnLoad (iperf3_jni.c)
 * for Android/desktop, NativeIperfBridge's init{} block for iOS (see
 * shared/src/iosMain/.../utils/NativeIperfBridge.kt) — before anything else
 * in this file can possibly run. That ordering matters: this must capture
 * the real stderr fd before iperf3_bridge_native_iperf3() ever dup2()s
 * STDERR_FILENO to its warning-capture pipe for a test run, or a debug
 * IPERF_LOG line written mid-run would otherwise go straight into that pipe
 * instead of the terminal (stderrReaderThreadFunc only forwards lines
 * prefixed "warning: ", so anything else - including our own debug output -
 * was silently dropped there). The guard below also makes this safe to call
 * again as defense-in-depth, but it's not meant to be relied on for
 * ordering - there's exactly one deliberate call site per platform. */
void initOrigStderr(void) {
    if (g_origStderr != NULL) return;

    // 1. Duplicate original stderr descriptor safely before changing anything
    int saved_stderr_fd = dup(STDERR_FILENO);
    if (saved_stderr_fd < 0) {
        perror("dup failed");
        return;
    }

    // 2. Open a brand-new FILE stream using the original console descriptor
    g_origStderr = fdopen(saved_stderr_fd, "w");
    if (!g_origStderr) {
        perror("fdopen failed");
        return;
    }
    // Turn off buffering so it behaves like standard stderr
    setbuf(g_origStderr, NULL);
}

void iperf3_bridge_force_stop(const struct IperfCallbacks *callbacks) {
    stop_requested = 1;
    if (callbacks->on_output) {
        callbacks->on_output(callbacks->ctx, "{iPerf3 Bridge} Requested graceful stop of iPerf3 test.");
    }
    if (global_test != NULL && !global_test->done) {
        global_test->done = 1;
        iperf_set_send_state(global_test, IPERF_DONE);
    }
}

/* `last_progress` tracks the last time we saw real forward progress, not
 * just wall-clock time since this thread started — see below for why.
 * (Earlier bug, now fixed: it used to get reassigned every loop iteration
 * regardless of progress, which meant the "5+ seconds" stall check could
 * never fire at all. That specific case — an invalid --client host failing
 * DNS resolution almost instantly — is now handled by
 * iperf3_bridge_native_iperf3 setting global_test->done unconditionally once
 * iperf_run_client() returns.)
 */
static void *monitorThreadFunc(void *args_ptr) {
    /* CLOCK_MONOTONIC, not time()/CLOCK_REALTIME: time() only has one-second
     * resolution and can jump backwards/forwards with wall-clock adjustments
     * (NTP sync, DST, user changing the clock) — useless for a sub-second
     * elapsed-time display. CLOCK_MONOTONIC is nanosecond-resolution (actual
     * hardware precision is coarser, but far better than 1s) and strictly
     * non-decreasing, which is what an elapsed-time stopwatch needs. */
    struct timespec start_ts;
    struct timespec sleep_time;

    clock_gettime(CLOCK_MONOTONIC, &start_ts);
    IPERF_LOG("monitorThreadFunc: ENTER, start_time = %ld.%09ld", (long) start_ts.tv_sec, start_ts.tv_nsec);
    struct ThreadArgs *args = (struct ThreadArgs *) args_ptr;
    const struct IperfCallbacks *cb = args->callbacks;
    if (cb->on_thread_attach) cb->on_thread_attach(cb->ctx);

    time_t last_progress = 0;
    time(&last_progress);
    uint64_t last_bytes_seen = 0;
    int seen_any_progress = 0;
    while (global_test && !global_test->done) {
        /* Startup — DNS resolution, the TCP handshake, and (for --parallel
         * > 1) establishing every additional data stream — can legitimately
         * take a while against a distant/high-RTT server, well past 5
         * seconds, with zero bytes transferred the whole time. That's not a
         * stall. So: don't apply the stall cap at all until we've seen
         * bytes_sent/bytes_received move at least once — before that, the
         * only bound on how long startup is allowed to take is iperf3's own
         * --connect-timeout (already user-configured). Once real data has
         * moved at least once, the test is unambiguously running, and
         * g_stall_timeout_seconds with no further progress is a reasonable
         * stall threshold for that phase.
         */
        uint64_t total_bytes = (uint64_t) atomic_load(&global_test->bytes_sent)
            + (uint64_t) atomic_load(&global_test->bytes_received);
        time_t now = 0;
        time(&now);
        if (total_bytes > last_bytes_seen) {
            last_bytes_seen = total_bytes;
            seen_any_progress = 1;
            last_progress = now;
        } else if (seen_any_progress && (now - last_progress > g_stall_timeout_seconds)) {
            char msg[80];
            snprintf(msg, sizeof(msg), "{iPerf3 Bridge} ❌ Stalled for %d+ seconds, aborting!", g_stall_timeout_seconds);
            if (cb->on_error) cb->on_error(cb->ctx, msg);
            if (global_test && !global_test->done) {
                global_test->done = 1;
                iperf_set_send_state(global_test, IPERF_DONE);
            }
            break;
        } else if (!seen_any_progress && ((now - last_progress) > (g_stall_timeout_seconds * 2))) {
            // conservatively, if a connection takes longer than 10 seconds (typical) we are not going to succeed
            char msg[80];
            snprintf(msg, sizeof(msg), "{iPerf3 Bridge} ❌ Connection timed out for %d+ seconds, aborting!",
                    g_stall_timeout_seconds);
            if (cb->on_error) cb->on_error(cb->ctx, msg);
            if (global_test && !global_test->done) {
                global_test->done = 1;
                iperf_set_send_state(global_test, IPERF_DONE);
            }
            break;
        }
        if (cb->on_time) {
            char elapsed_str[80];
            /* 100 updates in 1 second == a 10ms cadence between callbacks.
             * tv_nsec is nanoseconds, so 10ms is 10 * 1,000,000 ns — NOT
             * 10 (that was the original bug: a near-zero sleep meant all
             * 100 callbacks fired back-to-back instead of spread across a
             * second, hammering the JNI bridge with a tight-loop burst of
             * calls). */
            sleep_time.tv_sec = 0;
            sleep_time.tv_nsec = 10L * 1000L * 1000L;

            for (int i = 0; i < 100; i++) {
                /* Re-check every tick, not just once per second: a run that
                 * fails fast (e.g. "Connection refused") would otherwise keep
                 * reporting elapsed time for up to a second after it ended,
                 * re-showing the elapsed-time row after the error and holding
                 * up iperf3_bridge_native_iperf3()'s pthread_join (so the UI
                 * kept showing Stop). global_test can't be freed here: that
                 * only happens after this thread is joined. */
                if (global_test->done) break;
                struct timespec now_ts;
                clock_gettime(CLOCK_MONOTONIC, &now_ts);
                int64_t elapsed_ns = (int64_t) (now_ts.tv_sec - start_ts.tv_sec) * 1000000000LL
                    + (now_ts.tv_nsec - start_ts.tv_nsec);
                snprintf(elapsed_str, sizeof(elapsed_str), "%.3f", (double) elapsed_ns / 1e9);
                cb->on_time(cb->ctx, elapsed_str);

                /* Deliberately not IPERF_LOG'd per-iteration — at 100
                 * calls/sec that would flood stderr (IPERF_LOG is a real
                 * fprintf on non-Android builds, not a no-op). */

                // nanosleep returns 0 on success, -1 on error (e.g., signal interrupt)
                if (nanosleep(&sleep_time, NULL) != 0) {
                    IPERF_LOG("nanosleep failed");
                    break;
                }
            }
        }
        //sleep(1);
    }

    if (cb->on_thread_detach) cb->on_thread_detach(cb->ctx);
    free(args);
    IPERF_LOG("monitorThreadFunc: EXIT");
    return NULL;
}

/* fdopen()s a reader thread's pipe with stdio's internal locking turned off.
 * glibc's exit() flushes every open FILE -- read streams included -- and
 * takes each one's lock first, while fgets() holds that same lock for as
 * long as it sits blocked in read(). If the JVM exits mid-test (Ctrl-C in
 * the CLI), the iperf3 thread that would write the next line or close the
 * pipe is parked inside a JNI callback (block_if_vm_exited) and never
 * does, so exit() waits on the reader's lock forever and the process
 * hangs. Each of these FILEs is only ever touched by its own reader
 * thread, so the lock guards nothing; FSETLOCKING_BYCALLER also makes
 * exit()'s flush-all skip it. Not needed off glibc (macOS/iOS/bionic). */
static FILE *openPipeForReading(int fd) {
    FILE *fp = fdopen(fd, "r");
#ifdef __GLIBC__
    if (fp != NULL) __fsetlocking(fp, FSETLOCKING_BYCALLER);
#endif
    return fp;
}

/* Reads iperf3's text output from the pipe and forwards each line via
 * on_output — runs on its own thread since iperf_run_client() blocks the
 * calling thread for the test duration. */
static void *readerThreadFunc(void *args_ptr) {
    struct ThreadArgs *args = (struct ThreadArgs *) args_ptr;
    const struct IperfCallbacks *cb = args->callbacks;
    if (cb->on_thread_attach) cb->on_thread_attach(cb->ctx);

    char buffer[1024];
    FILE *fp = openPipeForReading(args->pipe_fd);
    while (fp != NULL && fgets(buffer, sizeof(buffer), fp)) {
        if (cb->on_output) cb->on_output(cb->ctx, buffer);
    }
    if (fp != NULL) {
        fclose(fp);
    }

    if (cb->on_thread_detach) cb->on_thread_detach(cb->ctx);
    free(args);
    return NULL;
}

/* Some iperf3 diagnostics (warning(), iperf_api.c) write straight to the
 * process's real stderr instead of through test->outfile — e.g. malformed
 * JSON/argument warnings — so redirecting only test->outfile (as
 * readerThreadFunc's pipe does) misses them entirely. This thread reads a
 * second pipe that iperf3_bridge_native_iperf3 dup2()s the process's real stderr
 * fd into for the run's duration, and forwards each line via on_error.
 * Note: warning() lines aren't necessarily fatal to the test itself, but
 * on_error is what platform adapters use to mark a run failed — routing
 * stderr here means any such warning will be treated as a run failure.
 *
 * dup2()ing fd 2 redirects the *whole process's* stderr, not just iperf3's
 * own writes — on Android in particular, other threads sharing the process
 * (e.g. Skia's GL renderer backing Compose) can write their own unrelated
 * diagnostics to stderr during a run (observed: "s_glBindAttribLocation:
 * bind attrib ... name position" spam from shader setup), which would
 * otherwise get misforwarded through on_error and wrongly fail an
 * otherwise-successful test. warning() (iperf_api.c) always writes with a
 * fixed "warning: " prefix, so only lines matching that prefix are treated
 * as real iperf3 warnings; everything else on this pipe is silently
 * dropped. */
static void *stderrReaderThreadFunc(void *args_ptr) {
    struct ThreadArgs *args = (struct ThreadArgs *) args_ptr;
    const struct IperfCallbacks *cb = args->callbacks;
    if (cb->on_thread_attach) cb->on_thread_attach(cb->ctx);

    static const char WARNING_PREFIX[] = "warning: ";
    char buffer[1024];
    FILE *fp = openPipeForReading(args->pipe_fd);
    while (fp != NULL && fgets(buffer, sizeof(buffer), fp)) {
        if (strncmp(buffer, WARNING_PREFIX, sizeof(WARNING_PREFIX) - 1) != 0) {
            continue;
        }
        if (cb->on_error) cb->on_error(cb->ctx, buffer);
    }
    if (fp != NULL) {
        fclose(fp);
    }

    if (cb->on_thread_detach) cb->on_thread_detach(cb->ctx);
    free(args);
    return NULL;
}

int iperf3_bridge_native_iperf3(char **argv, int argc, const struct IperfCallbacks *callbacks) {
    /* iperf3's own CLI ignores SIGPIPE around iperf_run_client() (see
     * third_party/iperf/src/main.c's run()) "to simplify error handling" —
     * a write()/send() on a socket the peer already closed (e.g. the user
     * killing the iperf3 server mid-upload) otherwise raises SIGPIPE, whose
     * default disposition kills the whole process. We call the library
     * functions directly and skip main.c's run() entirely, so that
     * protection never happened here. This isn't only about
     * iperf_run_client() itself, though: iperf3_bridge_force_stop() below
     * can call iperf_set_send_state(), which does its own Nwrite() to the
     * control socket, and it can fire from another thread at any point
     * relative to a run — before iperf_run_client() starts, mid-run
     * (concurrently with a stall-triggered auto-cancel), or in the
     * post-return cleanup window before global_test is freed. Narrowly
     * scoping the ignore to just the iperf_run_client() call (an earlier,
     * now-abandoned version of this fix did that, restoring SIG_DFL right
     * after it returned) left exactly that force_stop() race open — a
     * concurrent stop landing in the gap between iperf_run_client()
     * returning and global_test->done being set. Set once, never restored:
     * this is a long-lived GUI app process, not a one-shot CLI that needs
     * to hand default signal behavior back to a shell afterward, so there's
     * no real downside to leaving it ignored for the process's lifetime.
     * On Android/desktop this was silently masked all along — the JVM
     * already globally ignores SIGPIPE at startup, so JNI-loaded native
     * code inherits that — but iOS has no JVM, so it was fully exposed. */
    signal(SIGPIPE, SIG_IGN);

    stop_requested = 0;
    IPERF_LOG("iperf3_bridge_native_iperf3: ENTER, prior global_test=%p", (void *) global_test);

    if (argc > 64) argc = 64;

    /* ----- Create and initialize the iperf test ----- */
    global_test = iperf_new_test();
    if (global_test == NULL) {
        if (callbacks->on_error) {
            callbacks->on_error(callbacks->ctx, "{iPerf3 Bridge} ❌ Failed to create iPerf3 test!");
        }
        return -1;
    }
    iperf_defaults(global_test);

    /* ----- Redirect iperf3's text output through a pipe ----- */
    int pipefd[2];
    if (pipe(pipefd) < 0) {
        if (callbacks->on_error) {
            callbacks->on_error(callbacks->ctx, "{iPerf3 Bridge} ❌ Failed to create output pipe!");
        }
        iperf_free_test(global_test);
        global_test = NULL;
        return -1;
    }
    FILE *outfile = fdopen(pipefd[1], "w");
    setvbuf(outfile, NULL, _IOLBF, 0);
    global_test->outfile = outfile;

    /* ----- Also capture warning()'s direct writes to the real stderr -----
     * See stderrReaderThreadFunc's header comment for the full rationale.
     * dup2() replaces the process-wide stderr fd for the duration of this
     * run; the original is saved and restored afterward since this affects
     * the whole process, not just this call. Non-fatal if it can't be set
     * up (pipe()/dup() exhaustion is rare and this is a diagnostics-only
     * feature) — the test still runs, it just won't have this extra
     * warning channel. */
    int errpipefd[2];
    int saved_stderr_fd = -1;
    if (pipe(errpipefd) == 0) {
        saved_stderr_fd = dup(STDERR_FILENO);
        if (saved_stderr_fd >= 0) {
            dup2(errpipefd[1], STDERR_FILENO);
        }
        close(errpipefd[1]);
    } else {
        errpipefd[0] = -1;
    }

    /* ----- Point iperf3's per-stream mkstemp() buffer at a writable dir -----
     * Sandboxed mobile apps can't write to iperf3's built-in fallback
     * (/data/local/tmp on Android, and iOS has no equivalent world-writable
     * path at all) — this must be an app-owned directory, passed in from
     * Kotlin via iperf3_bridge_set_temp_dir(). See
     * IPERF3_NATIVE_INTEGRATION.md for why this is still required under
     * 3.21 (test->tmp_template, not the removed test->tempDir field). */
    char tmp_template[PATH_MAX];
    if (g_temp_dir_set) {
        snprintf(tmp_template, sizeof(tmp_template), "%s/iperf3.XXXXXX", g_temp_dir);
        iperf_set_test_template(global_test, tmp_template);
    }

    /* ----- Reset getopt state before every parse -----
     * This app calls iperf_parse_arguments() repeatedly in the same
     * process (once per test run) — something iperf3's own CLI (main.c)
     * never does, since it only parses argv once at process startup. Its
     * end-of-parse reset (iperf_api.c, end of iperf_parse_arguments) only
     * sets `optreset = 1` under `#ifdef __APPLE__`; bionic's own getopt.h
     * documents that Android callers must set `optreset = 1` too (setting
     * only `optind` back is documented as NOT sufficient there) — without
     * this, a second run's argv is parsed starting from wherever the
     * previous run's getopt cursor was left, silently dropping/misreading
     * the new host/port/etc. Resetting explicitly here, before every call,
     * covers this regardless of what iperf3's own post-parse reset does. */
#if defined(__ANDROID__) || defined(__APPLE__) || defined(__FreeBSD__) || defined(__OpenBSD__) || defined(__NetBSD__)
    optreset = 1;
#endif
    optind = 1;

    /* ----- Reset i_errno before every parse -----
     * Filed upstream: https://github.com/esnet/iperf/issues/2063
     * i_errno (iperf_api.h) is a bare global, and helpers like unit_atoi()/
     * unit_atof() (units.c) only ever *set* it on their own failure — they
     * never clear it on success. iperf_parse_arguments() checks
     * `if (i_errno != 0) return -1;` right after calling them, trusting
     * i_errno was 0 beforehand. That's true the first time any process
     * calls this but false for every run after the first one in this app,
     * since a normal test's own completion leaves i_errno holding
     * IESTREAMREAD (an ordinary "peer closed the connection, we're done"
     * condition, not a real failure) — which then gets misread as an
     * immediate parse failure on the very next run. */
    i_errno = 0;

    /* ----- Parse iperf3 arguments ----- */
    IPERF_LOG("iperf3_bridge_native_iperf3: argc=%d, argv[0..]=%s %s %s %s %s ...",
        argc,
        argc > 0 ? argv[0] : "", argc > 1 ? argv[1] : "", argc > 2 ? argv[2] : "",
        argc > 3 ? argv[3] : "", argc > 4 ? argv[4] : "");
    int parse_result = iperf_parse_arguments(global_test, argc, argv);
    IPERF_LOG("iperf3_bridge_native_iperf3: parse_result=%d, i_errno=%d, role=%c, server_hostname=%s, port=%d",
        parse_result, i_errno, global_test->role,
        global_test->server_hostname ? global_test->server_hostname : "(null)",
        global_test->server_port);
    if (parse_result < 0) {
        fclose(outfile);
        if (saved_stderr_fd >= 0) {
            dup2(saved_stderr_fd, STDERR_FILENO);
            close(saved_stderr_fd);
        }
        if (errpipefd[0] >= 0) {
            close(errpipefd[0]);
        }
        if (callbacks->on_error) {
            callbacks->on_error(callbacks->ctx, iperf_strerror(i_errno));
        }
        iperf_free_test(global_test);
        global_test = NULL;
        return parse_result;
    }

    /* ----- Start the reader thread, then run the test (blocks this thread) ----- */
    struct ThreadArgs *reader_args = malloc(sizeof(struct ThreadArgs));
    reader_args->callbacks = callbacks;
    reader_args->pipe_fd = pipefd[0];
    int create_rc = pthread_create(&reader_thread, NULL, readerThreadFunc, reader_args);
    IPERF_LOG("iperf3_bridge_native_iperf3: pthread_create(reader) rc=%d", create_rc);

    /* Start stderr reader thread (only if the pipe/dup2 above worked) */
    int stderr_reader_started = 0;
    if (errpipefd[0] >= 0) {
        struct ThreadArgs *stderr_reader_args = malloc(sizeof(struct ThreadArgs));
        stderr_reader_args->callbacks = callbacks;
        stderr_reader_args->pipe_fd = errpipefd[0];
        int stderr_create_rc = pthread_create(&stderr_reader_thread, NULL, stderrReaderThreadFunc, stderr_reader_args);
        IPERF_LOG("iperf3_bridge_native_iperf3: pthread_create(stderr_reader) rc=%d", stderr_create_rc);
        stderr_reader_started = (stderr_create_rc == 0);
    }

    /* Start monitor thread */
    struct ThreadArgs *monitor_args = malloc(sizeof(struct ThreadArgs));
    monitor_args->callbacks = callbacks;
    monitor_args->pipe_fd = -1; /* unused by the monitor thread */
    IPERF_LOG("iperf3_bridge_native_iperf3: starting monitor thread ...");
    int monitor_create_rc = pthread_create(&monitor_thread, NULL, monitorThreadFunc, monitor_args);
    IPERF_LOG("iperf3_bridge_native_iperf3: pthread_create(monitor) rc=%d", monitor_create_rc);

    /* Notify start */
    if (callbacks->on_output) {
        callbacks->on_output(callbacks->ctx, "{iPerf3 Bridge} ▶ iPerf3 client request.");
    }

    IPERF_LOG("iperf3_bridge_native_iperf3: calling iperf_run_client...");
    int result = iperf_run_client(global_test);
    /* Captured immediately — errno is clobbered by the very next syscall
     * (logging, cleanup, ...), and iperf_strerror() below only appends
     * strerror(errno) for a handful of its codes (IESTREAMWRITE,
     * IESENDMESSAGE, IECTRLWRITE — see iperf_error.c's perr flag), which is
     * useless if errno no longer holds what it held when the underlying
     * write() actually failed. This is what write()/send() to a socket the
     * peer already closed returns once SIGPIPE is ignored (see this
     * function's own ignore-SIGPIPE comment above) — EPIPE, not a signal. */
    int saved_errno = errno;
    if (result < 0) {
        IPERF_LOG("iperf3_bridge_native_iperf3: iperf_run_client returned %d, i_errno=%d (%s), errno=%d (%s)",
                result, i_errno, iperf_strerror(i_errno), saved_errno, strerror(saved_errno));
    }
    /* iperf_run_client() returning at all — success or failure — means the
     * run is over; most paths inside it already set global_test->done
     * themselves, but at least one fast-failure path (e.g. a malformed
     * --client host failing DNS resolution almost instantly) doesn't. Set
     * it explicitly here so monitorThreadFunc's loop exits on its very next
     * poll instead of waiting for its own stall timeout to independently
     * rediscover the same fact. */
    global_test->done = 1;

    /* Test finished cleanly? */
    const char *final_msg = "{iPerf3 Bridge} ✅ Test completed successfully.";
    if (stop_requested) {
        final_msg = "{iPerf3 Bridge} ⏹ Test stopped due to stop request.";
    } else if (result < 0) {
        final_msg = "{iPerf3 Bridge} ❌ Test failed to complete successfully!";
    }
    if (callbacks->on_output) {
        callbacks->on_output(callbacks->ctx, final_msg);
    }

    if (result < 0) {
        if (callbacks->on_error) {
            if (saved_errno == EPIPE) {
                char errbuf[512];
                snprintf(errbuf, sizeof(errbuf),
                        "%s (SIGPIPE/EPIPE: a socket write failed because the peer closed "
                        "the connection — the iperf3 server likely stopped or crashed mid-test)",
                        iperf_strerror(i_errno));
                callbacks->on_error(callbacks->ctx, errbuf);
            } else {
                callbacks->on_error(callbacks->ctx, iperf_strerror(i_errno));
            }
        }
        /* The iperf return code of -1 is a catchall, the i_errno is a
         * better return value. */
        if (result == -1) {
            result = i_errno;
        }
    }

    /* Join the monitor thread BEFORE freeing/NULLing global_test — it
     * dereferences global_test directly in its loop, so this ordering is
     * what actually makes that safe (not just the `global_test &&` null
     * checks, which don't protect against a concurrent free()). By the
     * time iperf_run_client() returns, global_test->done is already 1 in
     * the overwhelming majority of cases, so this join returns immediately;
     * the monitor thread's own stall timeout is the worst-case bound. */
    int monitor_join_rc = pthread_join(monitor_thread, NULL);
    IPERF_LOG("iperf3_bridge_native_iperf3: pthread_join(monitor) rc=%d", monitor_join_rc);

    iperf_free_test(global_test);
    global_test = NULL;
    fclose(outfile); /* unblocks the reader thread's fgets() with EOF */

    /* Restore the process's real stderr before joining the stderr reader
     * thread — dup2()ing the saved fd back over STDERR_FILENO closes the
     * pipe's write end (since STDERR_FILENO was its only remaining
     * reference after the earlier close(errpipefd[1])), which is what
     * unblocks that thread's fgets() with EOF, same as fclose(outfile)
     * does for the normal reader thread above. */
    if (saved_stderr_fd >= 0) {
        dup2(saved_stderr_fd, STDERR_FILENO);
        close(saved_stderr_fd);
    }
    if (stderr_reader_started) {
        int stderr_join_rc = pthread_join(stderr_reader_thread, NULL);
        IPERF_LOG("iperf3_bridge_native_iperf3: pthread_join(stderr_reader) rc=%d", stderr_join_rc);
    }

    int join_rc = pthread_join(reader_thread, NULL);
    IPERF_LOG("iperf3_bridge_native_iperf3: pthread_join(reader) rc=%d", join_rc);

    stop_requested = 0;
    if (callbacks->on_complete) {
        callbacks->on_complete(callbacks->ctx);
    }

    IPERF_LOG("iperf3_bridge_native_iperf3: EXIT, prior global_test=%p", (void *) global_test);
    return result;
}
