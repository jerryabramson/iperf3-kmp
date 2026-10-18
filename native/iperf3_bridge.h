// Copyright (c) 2026, Jerold Abramson
//
// This source code is licensed under the BSD 3-Clause License found in
// the LICENSE file in the root directory of this source tree.

/*
 * iperf3_bridge.h — platform-agnostic core of the JNI/cinterop split
 * described in IPERF3_NATIVE_INTEGRATION.md §8.4.
 *
 * All of the threading, stall-detection, and stderr-warning-filtering logic
 * that used to live directly in native/iperf3_jni.c now lives in
 * iperf3_bridge.c instead, with zero JNI (or Kotlin/Native) types anywhere
 * in that file — platform glue talks to it only through the callback-struct
 * interface declared here, the same "keep the interop payload as simple as
 * possible" principle native_format.h already established (see
 * NATIVE_FORMATTING_JUSTIFICATION.md).
 *
 * Two platform adapters consume this:
 *   - native/iperf3_jni.c — thin JNI adapter, Android + desktop JVM.
 *   - shared/src/iosMain/.../utils/IperfNativeBridge.kt — Kotlin/Native
 *     cinterop consumer, built against this same header directly (like
 *     native_format.h), linked against a static lib compiled from this file
 *     plus third_party/iperf/src (see shared/build.gradle.kts).
 */
#ifndef IPERF3_BRIDGE_H
#define IPERF3_BRIDGE_H

#ifdef __cplusplus
extern "C" {
#endif

/*
 * `ctx` is opaque to this file — each platform adapter defines its own
 * shape and owns its lifetime.
 *
 * `on_thread_attach`/`on_thread_detach` exist only because this bridge, not
 * the platform adapter, owns creation of the background monitor/output/
 * stderr-reader threads: JNI requires each new pthread to
 * AttachCurrentThread/DetachCurrentThread before/after it can call back
 * into Kotlin, so the JNI adapter needs a hook at each new thread's start
 * and end to do that. Kotlin/Native's cinterop has no such requirement (its
 * default memory model allows calling into Kotlin from any thread), so the
 * iOS adapter leaves both NULL — iperf3_bridge.c always checks for NULL
 * before calling either.
 */
struct IperfCallbacks {
    void *ctx;
    void (*on_thread_attach)(void *ctx);
    void (*on_thread_detach)(void *ctx);
    void (*on_output)(void *ctx, const char *line);
    void (*on_time)(void *ctx, const char *ms);
    void (*on_error)(void *ctx, const char *line);
    void (*on_complete)(void *ctx);
};

void iperf3_bridge_set_debug(int debug);

/* Captures the real stderr fd before anything in this file can redirect it.
 * Call exactly once, at process/app startup, before any other function in
 * this header - see the doc comment on this function's definition in
 * iperf3_bridge.c for why the ordering matters. Each platform adapter has
 * exactly one deliberate call site: JNI_OnLoad (iperf3_jni.c) for
 * Android/desktop, NativeIperfBridge's init{} block for iOS. */
void initOrigStderr(void);

void iperf3_bridge_set_temp_dir(const char *temp_dir);

/* Configures the stall watchdog's no-progress abort threshold (default 5).
 * Non-positive values are ignored — the previous value stays in effect. */
void iperf3_bridge_set_stall_timeout_seconds(int seconds);

/* Requests a graceful stop of whatever test iperf3_bridge_native_iperf3() is
 * currently blocked in. Safe to call from a different thread than the one
 * blocked in iperf3_bridge_native_iperf3() — that's the whole point. `callbacks`
 * only needs on_output/ctx populated (used for a single status message);
 * on_thread_attach/detach are irrelevant here since this doesn't spawn a
 * thread. */
void iperf3_bridge_force_stop(const struct IperfCallbacks *callbacks);

/* Blocks the calling thread for the test's duration, driving iperf3's own
 * client run loop and forwarding output/errors through `callbacks` from
 * background threads this function creates and joins before returning.
 * Returns iperf3's own result code (0 success, negative failure).
 *
 * Not reentrant across concurrent calls — this app only ever runs one test
 * at a time (see the single `global_test` note in the .c file), same
 * assumption the original JNI-only implementation made. */
int iperf3_bridge_native_iperf3(char **argv, int argc, const struct IperfCallbacks *callbacks);

#ifdef __cplusplus
}
#endif

#endif /* IPERF3_BRIDGE_H */
