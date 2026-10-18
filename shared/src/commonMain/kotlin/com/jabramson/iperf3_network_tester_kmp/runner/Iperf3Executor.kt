/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.runner

import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

import com.jabramson.iperf3_network_tester_kmp.Iperf3KMPConstants
import com.jabramson.iperf3_network_tester_kmp.model.Iperf3RunningState
import com.jabramson.iperf3_network_tester_kmp.utils.Iperf3OutputMonitor
import com.jabramson.iperf3_network_tester_kmp.utils.Log
import com.jabramson.iperf3_network_tester_kmp.viewmodel.UiInputData
import com.jabramson.iperf3_network_tester_kmp.viewmodel.DefaultInputData.HOST_NAME
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Duration.Companion.seconds

/**
 * Manages the lifecycle and logic of running iPerf3-based network tests.
 */
class Iperf3Executor(
    var updateProgress: (Float, Long) -> Unit,
    var updateElapsedTime: (String) -> Unit,
    var stdout: (Iperf3RunningState, Boolean) -> Unit,
    var stderr: (Iperf3RunningState, String) -> Unit,
    private val onTestComplete: () -> Unit
) {
    val tag: String = "Iperf3Executor"

    // single instance state
    private val iperf3OutputMonitor = Iperf3OutputMonitor()

    /*
     * Written from startTest()'s callback thread (IperfRunner.executeNativeIperf3Client
     * dispatches to Dispatchers.IO on Android/JVM), read from cancelTest() —
     * typically called on the UI thread. @Volatile guarantees the writing is
     * visible to that read (no per-thread caching); it does NOT make
     * read-modify-write ops atomic, but every access here is a plain
     * single read or single write, so that's not needed.
     */
    @Volatile
    private var isIperfRunning = false

    /*
     * Guards the synthetic "invalid hostname" hint sequence in startTest()
     * below against a fast Run->Cancel->Run: cancelTest() bumps this on every
     * call (even when isIperfRunning is false, which it always is during that
     * branch -- it never reaches the real test's isIperfRunning = true at
     * line 227), invalidating whichever generation was in flight. Each step
     * of that branch re-checks its own captured generation before writing to
     * the shared iperf3OutputMonitor, so a cancelled, still-suspended
     * coroutine can't add stray messages even if it resumes mid-delay.
     */
    @OptIn(ExperimentalAtomicApi::class)
    private val generation = AtomicInt(0)

    fun setDebug(debug: Boolean) {
        IperfRunner.setDebug(if (debug) 1 else 0)
    }

    /**
      * Cancels a currently running iperf3 test and initiates forced shutdown of the runner.
      *
      * If no test is actively running, this method returns immediately without performing any action.
      * Otherwise, it triggers the force stop mechanism, handles output monitoring, updates progress,
      * invokes completion callbacks, and logs errors if they occur during the cancellation process.
      *
      * Deliberately not `suspend`, even though IperfRunner.forceStop()'s native call isn't guaranteed
      * to return immediately (it does a blocking write to the test's control socket) — this is called
      * from two different contexts, and one of them has no coroutine scope to suspend from at all: the
      * stall-detection path below (`onLine`, in startTest()) invokes this synchronously from a raw JNI
      * callback on the native reader thread, not from a coroutine. The other call site
      * (Iperf3RunViewModel.cancel()) already runs this inside viewModelScope.launch { }, so it could
      * tolerate suspend, but the reader-thread path can't — making this suspend isn't viable without a
      * larger restructuring of that callback chain.
      *
      * @return 0 if cancellation was initiated successfully; -1 if no test was running or an error occurred.
      */
    @OptIn(ExperimentalAtomicApi::class)
    fun cancelTest(): Int {
        var rc = 0
        generation.incrementAndFetch()
        if (!isIperfRunning) return -1

        stderr(iperf3OutputMonitor.getCurrentLineResult(), "❌ Test Cancelled by User or System")
        IperfRunner.forceStop(
            createIperfCallback(
                // No onTime here: iperf3_bridge_force_stop() (native/iperf3_bridge.c)
                // only ever invokes on_output — it never calls on_time — so this
                // callback is guaranteed unused on the force-stop path. Falls back
                // to createIperfCallback's no-op default.
                onLine =
                    {
                        val line = it.trim().removeSuffix("\n")
                        val newLineResult = iperf3OutputMonitor.processCancelLine(line)
                        stdout(newLineResult, true)
                        Log.d(tag, "forceStop: onLine: $line")
                    }, onError =
                    {
                        val err = it.trim().removeSuffix("\n").removePrefix("\n")
                        isIperfRunning = false
                        stderr(iperf3OutputMonitor.getCurrentLineResult(), "❌ Error: $err")
                        onTestComplete()
                        updateProgress(1.0f, 100)
                        rc = -1
                    },
                onComplete = {
                    isIperfRunning = false
                    onTestComplete()
                    updateProgress(1.0f, 100)
                }
            )
        )
        return rc


    }
    /**
      * Initiates and executes an iperf3 network performance test using the provided configuration parameters.
      * Validates input constraints such as duration and port range, configures the execution environment,
      * constructs command-line arguments, and runs the native iperf3 client while monitoring live output.
      * Handles real-time progress updates, detects stalls or cancellations, and returns the test outcome.
      *
      * @param params Configuration data containing target host details, timing, concurrency settings,
      *               and behavioral flags required to initialize and control the testing workflow.
      * @return 0 if the test completes successfully; -1 if validation fails, an exception occurs, or the
      * test is canceled or stalled.
      */
    @OptIn(ExperimentalAtomicApi::class)
    suspend fun startTest(params: UiInputData): Int {
        Log.d(tag, "iperf3 JNI Bridge Starting")

        if (params.hostName == HOST_NAME) {
            val myGeneration = generation.incrementAndFetch()
            iperf3OutputMonitor.reset(params)
            updateProgress(calcProgress(0.0.toFloat()), 0)
            stderr(iperf3OutputMonitor.getCurrentLineResult(), "❌ Error: Must supply a valid hostname")
            delay(duration = 1.seconds)
            if (generation.load() == myGeneration) {
                iperf3OutputMonitor.processCancelLine(Iperf3KMPConstants.LinkableMessages.IPERF3_HOMEPAGE)
                stdout(iperf3OutputMonitor.getCurrentLineResult(), true)
            }
            delay(duration = 2.seconds)
            if (generation.load() == myGeneration) {
                iperf3OutputMonitor.processCancelLine(Iperf3KMPConstants.LinkableMessages.PUBLIC_SERVER_LIST)
                stdout(iperf3OutputMonitor.getCurrentLineResult(), true)
            }
            delay(duration = 2.seconds)
            if (generation.load() == myGeneration) {
                onTestComplete()
                updateProgress(1.0f, 100)
            }
            return -1
        }

        /*
         * 1. Initialize the iperf3 parameters, do some basic validations
         */
        val reverse = if (params.isReverse)  "--reverse" else ""
        val flush = if (params.isForceFlush) "--forceflush" else ""
        val serverPort = params.portNumber
        val localTimeout = if (params.timeout != 0L) params.timeout else 3000L
        val parallelStreams = params.parallelStreams
        val durationSecs = params.durationSecs

        // We are purposely not validating host/ip addresses, we let iperf3 do that for us


        if (durationSecs <= 0 || durationSecs > (60 * 60 * 12)) {
            Log.e("Iperf3Runner", "Invalid duration: $durationSecs. Must be between 1 second to 12 hours.")
            stderr(iperf3OutputMonitor.getCurrentLineResult(), "Invalid duration: ${params.durationSecs}")
            updateProgress(1.0f, 100)
            return -1
        }

        if (serverPort !in 1..65535) {
            Log.e("Iperf3Runner", "Invalid Server Port Number: $serverPort. Must be between 1 and 65335.")
            stderr(iperf3OutputMonitor.getCurrentLineResult(), "Invalid port number: $serverPort")
            updateProgress(1.0f, 100)
            return -1

        }

        if (parallelStreams !in 1..Iperf3KMPConstants.MAX_PARALLEL_STREAMS) {
            stderr(
                iperf3OutputMonitor.getCurrentLineResult(),
                "Invalid number of streams: $parallelStreams (must be 1-${Iperf3KMPConstants.MAX_PARALLEL_STREAMS})"
            )
            updateProgress(1.0f, 100)
            return -1
        }

        /*
         *  2. Point iperf3 at a real, writable, app-owned temp directory. Real libiperf's
         *     per-stream mkstemp() buffer file needs this — its own built-in fallback
         *     (/data/local/tmp on Android) isn't writable inside a sandboxed app's process.
         *     See IPERF3_NATIVE_INTEGRATION.md for why this is still required under iperf3 3.21.
         */
        val tempDirectory: String = getPlatformTempDir()
        IperfRunner.setTempDir(tempDirectory)
        Log.i(tag, "tempDirectory: $tempDirectory")

        /*
         *  3. Set a reasonable stall value of 5 seconds.
         *     This is not exposed in the UI, but it is a fairly generic setting, so
         *     it belongs here in commonMain
         */
        val stallValue = 5
        IperfRunner.setStallTimeoutSeconds(stallValue)

        /*
         *  4. Construct the iperf3 command line arguments.
         *     **Note** that argv[0] must be set to the program name.
         */
        val currentArgs = arrayOf(
            "iperf3",
            "--client", params.hostName,
            "--port", params.portNumber.toString(),
            reverse,
            flush,
            "--parallel", parallelStreams.toString(),
            "--connect-timeout",
            localTimeout.toString(),
            "--time", params.durationSecs.toString(),
            "--omit", params.skip.toString(),
            //"-V"
        )
        Log.d(tag, "currentArgs: ${currentArgs.joinToString(",")}")

        /*
         * 5. Initialize the output monitor.
         */
        iperf3OutputMonitor.reset(params)
        updateProgress(calcProgress(0.0.toFloat()), 0)

        /*
         * 6. Run the test. This function is already `suspend`, and
         *    IperfRunner.executeNativeIperf3Client() already dispatches its blocking native
         *    call to Dispatchers.IO internally — no separate coroutine/launch
         *    is needed just to call it. The return value IS the result: iperf3's
         *    own return code (0 = success, negative = failure) is the single
         *    source of truth, not a separately-tracked flag mutated from inside
         *    the callbacks.
         */
        Log.d("Iperf3Executor: ", "startTest")
        isIperfRunning = true

        var lastIntervalCount = -1L
        var lastOmittedCount = -1L
        var lastNumberOfMessages = 0
        var cancelled = false
        val microStepMax: Float = (params.parallelStreams.toFloat() * params.durationSecs.toFloat())
        var microStepVal: Float
        var lastMicroStepVal = 0.0.toFloat()


        val rc = try {
            IperfRunner.executeNativeIperf3Client(
                currentArgs, params.durationSecs, createIperfCallback(
                    onTime = {
                        updateElapsedTime(it)
                    },
                    onLine =
                        {
                            val line = it.trim().removeSuffix("\n")
                            val newLineResult = iperf3OutputMonitor.processLine(line)
                            microStepVal = (newLineResult.microProgress / microStepMax)
                            if (microStepVal > lastMicroStepVal) {
                                val percent = (microStepVal * 100).toLong()
                                updateProgress(microStepVal, percent)
                                lastMicroStepVal = microStepVal

                            }
                            Log.d(tag, "Callback -> onLine(\"${newLineResult.rawOutputLine}\")")
                            if (newLineResult.intervalNumber > lastIntervalCount
                                || newLineResult.totalOmitted > lastOmittedCount
                                || newLineResult.messages.size > lastNumberOfMessages) {
                                stdout(newLineResult, newLineResult.messages.size > lastNumberOfMessages)

                                // only update progress if we have a new interval and not a skipped interval
                                //if (newLineResult.intervalNumber > lastIntervalCount) {
                                //    updateProgress(calcProgress(lastIntervalCount.toFloat() / params.durationSecs))
                                //}
                                lastIntervalCount = newLineResult.intervalNumber
                                lastOmittedCount = newLineResult.totalOmitted
                                lastNumberOfMessages = newLineResult.messages.size
                                if (!cancelled && newLineResult.consecutiveStalled >= 5) {
                                    Log.e(tag, "iperf3 has stalled for 5 consecutive intervals!")
                                    stderr(newLineResult, "❌ iperf3 has stalled for 5 consecutive intervals!")
                                    cancelTest()
                                    cancelled = true
                                }
                            }
                        }, onError =
                        {
                            /*
                             * Non-fatal warnings can come through here too (see
                             * native/iperf3_jni.c's stderr reader thread) — this
                             * only reports the message, it doesn't decide pass/fail;
                             * the return value below (iperf3's real return code) does.
                             */
                            val err = it.trim().removeSuffix("\n")
                            isIperfRunning = false
                            stderr(iperf3OutputMonitor.getCurrentLineResult(), "❌ Error: $err")
                            onTestComplete()
                            updateProgress(1.0f, 100)
                        },
                    onComplete = {
                        isIperfRunning = false
                        onTestComplete()
                        updateProgress(1.0f, 100)
                    }
                )
            )
        } catch (e: CancellationException) {
            // The ViewModel cancelled this run (Stop: Iperf3RunViewModel.cancel()
            // calls runJob?.cancel()). withContext() throws this once the native
            // call returns after the force stop; it's expected, not an error, and
            // must propagate so the coroutine finishes cancelling.
            throw e
        } catch (e: Exception) {
            stderr(iperf3OutputMonitor.getCurrentLineResult(), "⚠ Uncaught Exception: $e")
            e.printStackTrace()
            -1
        } finally {
            isIperfRunning = false
        }

        return rc
    }
}


/**
 * Creates an [IperfCallback] implementation that delegates output, error, and completion events
 * to the specified lambda functions.
 *
 * @param onLine Lambda invoked when iperf3 outputs a line of text. Defaults to no-op.
 * @param onError Lambda invoked when an error occurs during the test execution. Defaults to no-op.
 * @param onComplete Lambda invoked when the iperf3 test finishes successfully or fails. Defaults to no-op.
 * @return An [IperfCallback] instance that routes events to the provided lambdas.
 */
private fun createIperfCallback(
    onLine: (String) -> Unit = {},
    onError: (String) -> Unit = {},
    onTime: (String) -> Unit = {},
    onComplete: () -> Unit = {}
): IperfCallback {
    return object : IperfCallback {
        override fun onOutput(line: String) = onLine(line)
        override fun onError(error: String) = onError(error)
        override fun onTime(ms: String) = onTime(ms)
        override fun onComplete() = onComplete()
    }
}

/**
 * Clamps the provided progress value to a range between 0.02 and 0.98.
 * This ensures the progress indicator avoids absolute zero or one hundred percent,
 * which is useful for maintaining continuous visual feedback during network tests.
 *
 * @param p The original progress value to be bounded.
 * @return The clamped progress value constrained within 0.02 and 0.98.
 */
private fun calcProgress(p: Float): Float {
    val zeroProgress = 0.00.toFloat()
    val finishedProgress = 0.1.toFloat()
    var r = p
    if (p > finishedProgress) r = finishedProgress
    if (p < zeroProgress) r = zeroProgress
    return r
}