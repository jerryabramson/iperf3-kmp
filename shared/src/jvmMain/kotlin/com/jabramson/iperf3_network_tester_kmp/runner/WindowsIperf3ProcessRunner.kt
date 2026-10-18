/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.runner

import com.jabramson.iperf3_network_tester_kmp.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.ProcessBuilder
import kotlinx.coroutines.launch

/**
 * Windows desktop can't use the in-process JNI path (NativeIperfBridge) the
 * way macOS/Linux desktop and Android do — see docs/desktop/WINDOWS_DESKTOP_APP.md
 * for the full history: `iperf3native.dll` would have to be built via
 * Cygwin's clang (no viable POSIX-compatible toolchain exists for
 * third_party/iperf's POSIX-heavy sources on Windows otherwise), and
 * loading a Cygwin-linked DLL into `java.exe` crashes reliably
 * (EXCEPTION_ACCESS_VIOLATION — the JVM's memory layout collides with
 * Cygwin's fixed-address shared-memory init).
 *
 * The intended design instead: run the real, officially-distributed
 * Windows `iperf3.exe` (Cygwin-linked, but as its own separate process —
 * `cygwin1.dll` living next to a small dedicated executable, never inside
 * an unrelated host process, exactly how it's actually shipped) via
 * `ProcessBuilder`, the same architecture pattern this project already
 * rejected once for Android in favor of the in-process JNI design — but
 * Windows has no in-process option available at all, so it's the only way
 * forward here specifically.
 *
 * The seam that makes this cheap: `IperfRunner.executeNativeIperf3Client`'s
 * `arguments` is already a ready-built argv array (constructed once, in
 * commonMain's Iperf3Executor.kt, in the exact CLI shape a real
 * `iperf3.exe -c host -p port ...` command line wants) — this class only
 * needs to replace "how do we execute" (spawn+pipe vs. a JNI call into C
 * threads), not "what do we ask iperf3 to do". `Iperf3OutputMonitor.kt`'s
 * text-output parsing is likewise unaffected, since real iperf3.exe's
 * stdout is the same textual format that already understands (this
 * project parses text output, not `--json-stream` — see CLAUDE.md).
 *
 * STUB — not yet implemented. IperfRunner (jvmMain) already routes to this
 * class at runtime when running on Windows; every function here is a
 * placeholder returning/no-op'ing gracefully (never throwing), so the
 * scaffolding is safe to build and run as-is before the real
 * ProcessBuilder logic exists.
 */
object WindowsIperf3ProcessRunner {

    // TODO: hold the temp dir path once ProcessBuilder execution needs one
    //  (may not be needed at all on Windows — unlike sandboxed
    //  Android/iOS, a normal Windows desktop process can write anywhere
    //  it's already permitted to; revisit once the real run() exists).
    private var tempDir: String? = null

    // TODO: wire into a real request, if iperf3.exe's own CLI flags need
    //  it (e.g. a verbose/-d style flag) — not necessarily the same
    //  concept as the native bridge's IPERF_LOG debug flag.
    private var debugEnabled = false

    // TODO: a ProcessBuilder-based run has no native stall-watchdog thread
    //  of its own (unlike iperf3_bridge.c's monitorThreadFunc) — this value
    //  needs a real Kotlin-side timer/coroutine watchdog once run() spawns
    //  a real process, or stalled Windows runs will hang indefinitely.
    private var stallTimeoutSeconds: Int = 5

    // Written from run()'s coroutine (a Dispatchers.IO thread), read from
    // forceStop() (called synchronously from whatever thread the Stop/
    // Cancel button's handler runs on - a different thread) - @Volatile
    // guarantees that write is visible to that read, the same reasoning
    // Iperf3Executor.kt's isIperfRunning field documents for the same
    // cross-thread pattern.
    @Volatile
    private var currentProcess: Process? = null

    /**
     * The iperf3.exe bundled with the app. The Compose Desktop plugin puts
     * everything staged under appResourcesRootDir/windows-x64/ into the
     * app's resources directory (next to cygwin1.dll, which iperf3.exe
     * needs in its own directory) and exposes it through the
     * `compose.application.resources.dir` system property - set both for
     * the installed .msi and for `:desktopApp:run`. Deliberately no
     * fallback to some other well-known location: a missing bundle should
     * fail loudly rather than run some stray iperf3.exe.
     */
    internal fun resolveIperf3Exe(): java.io.File? {
        val resourcesDir = System.getProperty("compose.application.resources.dir") ?: return null
        return java.io.File(resourcesDir, "iperf3.exe").takeIf { it.isFile }
    }

    fun setTempDir(tempDir: String?) {
        this.tempDir = tempDir
    }

    fun setDebug(debug: Int) {
        debugEnabled = debug != 0
    }

    fun setStallTimeoutSeconds(seconds: Int) {
        if (seconds > 0) {
            stallTimeoutSeconds = seconds
        }
    }

    /**
     * Hard-kills the running iperf3.exe (TerminateProcess on Windows - see
     * this class's own doc comment for why this is a hard kill, not the
     * graceful stop-flag approach iperf3_bridge_force_stop() uses on the
     * other platforms). No-ops with a distinct message, rather than
     * silently doing nothing, if called before run() has actually assigned
     * currentProcess yet - a real race if Stop is clicked fast enough
     * after Run.
     */
    fun forceStop(callback: IperfCallback) {
        val process = currentProcess
        if (process == null) {
            callback.onOutput("{iPerf3 Bridge} No iperf3 process is running yet - nothing to stop.")
            return
        }
        process.destroy()
        callback.onOutput("{iPerf3 Bridge} forcibly stopping iperf3 process.")
    }

    /**
     * STUB — see this class's own doc comment above for the intended
     * ProcessBuilder-based design. Returns gracefully (never throws) so
     * callers already written against the JNI path's contract (a
     * suspend fun returning iperf3's own result code, driving `callback`)
     * keep working unchanged once this is filled in for real.
     *
     * @return a negative placeholder result code — real iperf3 result
     *   codes are also negative on failure, 0 on success, so this reads as
     *   "failed" to any existing caller without needing special-casing.
     */
    suspend fun run(arguments: Array<String>, callback: IperfCallback): Int {

        val exe = resolveIperf3Exe()
        if (exe == null) {
            val msg = "Bundled iperf3.exe not found (compose.application.resources.dir=" +
                "${System.getProperty("compose.application.resources.dir")})"
            Log.e("iperf3Runner", msg)
            callback.onOutput("Failed to start iperf3 process: $msg")
            return 1
        }

        val fullArgumentArray: MutableList<String> = emptyList<String>().toMutableList()

        fullArgumentArray.add(exe.absolutePath)
        // arguments[0] is the "iperf3" argv[0] placeholder Iperf3Executor.kt
        // builds for the JNI path (which passes the whole array straight into
        // a C argv[], where argv[0] is expected and skipped during parsing).
        // ProcessBuilder's own first list element (`exe`, above) already
        // becomes the real argv[0] via CreateProcess, so reusing arguments[0]
        // verbatim here would hand iperf3.exe a spurious extra argument on
        // top of that - drop it.
        arguments.drop(1).forEach { fullArgumentArray.add(it) }

        val processBuilder = ProcessBuilder(fullArgumentArray)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)

        Log.d(tag = "iperf3Running", "Full command line: $fullArgumentArray")
        // -----------------------------------------------------------
        // 4️⃣ Launch, capture output, wait for termination
        // -----------------------------------------------------------

        try {
            currentProcess = withContext(Dispatchers.IO) {
                processBuilder.start()
            }
        } catch (e: Exception) {
            Log.e("iperf3Runner", "Failed to start iperf3 process: ${e.message}", e)
            callback.onOutput("Failed to start iperf3 process: ${e.message}, using simulated output")
            return 1
        }
        if (currentProcess == null) {
            Log.e("iperf3Runner", "Failed to start iperf3 process")
            callback.onOutput("Failed to start iperf3 process")
            return 1
        }


        fun readStream(
            input: InputStream,
            consumer: (String) -> Unit
        ) = BufferedReader(InputStreamReader(input)).use { reader ->
            var line: String? = null
            while (reader.readLine().also { line = it } != null) {
                // Never let a downstream processing failure (e.g. a
                // formatString()/native-format error, or anything else
                // consumer() might throw) stop this loop. iperf3.exe's OS
                // pipe buffer is finite - if nobody keeps draining it, the
                // process blocks on its own write() call and never exits,
                // which means the waitFor() below never returns either. A
                // dropped/unprocessed line is a far smaller problem than a
                // hung process, and this is exactly the failure mode that
                // produced a real hang (not just a crash) before this was
                // added: catching Throwable, not just Exception, since the
                // actual crash seen here (ExceptionInInitializerError) is a
                // java.lang.Error, which a narrower `catch (e: Exception)`
                // would not have caught at all.
                try {
                    consumer(line!!)
                } catch (e: Throwable) {
                    Log.e("iperf3Running", "Failed to process line \"$line\": ${e.message}", e)
                }
            }
        }

        return coroutineScope {
            val stdoutJob = launch {
                readStream(currentProcess!!.inputStream) { line ->
                    callback.onOutput(line)
                }
            }

            val stderrJob = launch {
                readStream(currentProcess!!.errorStream) { err ->
                    callback.onError(err)
                }
            }

            // Process.waitFor() is a plain blocking call, not a suspending
            // one - dispatching it through Dispatchers.IO explicitly (like
            // processBuilder.start() above) keeps it off whatever thread
            // this coroutineScope itself is running on.
            val exitValue = withContext(Dispatchers.IO) { currentProcess!!.waitFor() }
            stdoutJob.join()
            stderrJob.join()
            callback.onComplete()
            exitValue
        }
    }
}
