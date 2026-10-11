package com.jabramson.iperf3_network_tester_kmp.runner

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Confirms run() degrades gracefully (never throws/hangs) when the bundled
 * iperf3.exe can't actually be started - the path this project's own
 * macOS/Linux CI/dev machines always hit, since no bundled exe exists outside a packaged/`run` Windows app, not a
 * Windows resources dir. Runs on any host since WindowsIperf3ProcessRunner itself
 * doesn't check the host OS - only IperfRunner's routing does.
 */
class WindowsIperf3ProcessRunnerTest {

    private class RecordingCallback : IperfCallback {
        val outputs = mutableListOf<String>()
        val errors = mutableListOf<String>()
        var completed = false
        override fun onOutput(line: String) { outputs.add(line) }
        override fun onError(error: String) { errors.add(error) }
        override fun onTime(ms: String) {}
        override fun onComplete() { completed = true }
    }

    @Test
    fun runReportsStartFailureGracefullyWhenExeIsMissing() = runBlocking {
        val callback = RecordingCallback()
        val rc = WindowsIperf3ProcessRunner.run(arrayOf("iperf3", "--client", "example.com"), callback)

        assertEquals(1, rc, "should report the documented start-failure code")
        assertTrue(
            callback.outputs.any { it.contains("Failed to start iperf3 process") },
            "should explain the start failure via onOutput"
        )
    }

    @Test
    fun forceStopDoesNotThrowWithNoRunInProgress() {
        val callback = RecordingCallback()
        WindowsIperf3ProcessRunner.forceStop(callback)
    }

    @Test
    fun setStallTimeoutSecondsIgnoresNonPositiveValues() {
        // Just confirming these control setters don't throw - the runner
        // doesn't expose its stored state to assert against.
        WindowsIperf3ProcessRunner.setStallTimeoutSeconds(0)
        WindowsIperf3ProcessRunner.setStallTimeoutSeconds(-5)
        WindowsIperf3ProcessRunner.setStallTimeoutSeconds(10)
    }
}
