/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.runner

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Exercises the real libiperf 3.21 JNI path (NativeIperfBridge ->
 * native/iperf3_jni.c), the same one Iperf3Executor drives in the real app —
 * not a mock. Requires a system `iperf3` binary to spawn a local server
 * against; soft-skips (prints and returns) if one isn't on PATH, since CI/dev
 * machines without it shouldn't fail the whole suite over an environment gap.
 */
class IperfRunnerJvmTest {

    @Test
    fun runIperfLiveAgainstRealLocalServer() {
        val iperf3Binary = findIperf3Binary()
        if (iperf3Binary == null) {
            println("Skipping IperfRunnerJvmTest: no iperf3 binary found on PATH")
            return
        }

        val port = 15303
        val server = ProcessBuilder(iperf3Binary, "-s", "-p", port.toString(), "-1")
            .redirectErrorStream(true)
            .start()
        try {
            Thread.sleep(500)

            val outputLines = mutableListOf<String>()
            var completed = false
            var errorMessage: String? = null

            runBlocking {
                IperfRunner.setTempDir(System.getProperty("java.io.tmpdir"))
                IperfRunner.executeNativeIperf3Client(
                    arrayOf(
                        "iperf3", "--client", "127.0.0.1", "--port", port.toString(),
                        "--connect-timeout", "2000", "--time", "1"
                    ),
                    count = 1,
                    callback = object : IperfCallback {
                        override fun onOutput(line: String) {
                            outputLines.add(line)
                        }

                        override fun onError(error: String) {
                            errorMessage = error
                        }

                        override fun onTime(ms: String) {}

                        override fun onComplete() {
                            completed = true
                        }
                    }
                )
            }

            assertTrue(errorMessage == null, "Expected no error, got: $errorMessage")
            assertTrue(completed, "Expected onComplete() to be called")
            assertTrue(
                outputLines.any { it.contains("Bitrate") || it.contains("bits/sec", ignoreCase = true) },
                "Expected real bandwidth output, got: $outputLines"
            )
        } finally {
            server.destroy()
        }
    }

    private fun findIperf3Binary(): String? {
        val candidates = listOf("/usr/local/bin/iperf3", "/opt/homebrew/bin/iperf3", "iperf3")
        for (candidate in candidates) {
            if (candidate == "iperf3" || File(candidate).canExecute()) {
                return candidate
            }
        }
        return null
    }
}
