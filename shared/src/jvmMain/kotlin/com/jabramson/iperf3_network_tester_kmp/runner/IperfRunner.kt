/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.jabramson.iperf3_network_tester_kmp.runner

import com.jabramson.iperf3_network_tester_kmp.utils.NativeIperfBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * jvm() has no OS-specific child source sets the way iosArm64/
 * iosSimulatorArm64 do — this one jvmMain covers macOS, Linux, and Windows
 * together, so telling them apart is a runtime check here, not a
 * compile-time source-set split. See WindowsIperf3ProcessRunner's own doc
 * comment for why Windows specifically can't use the same in-process JNI
 * path (NativeIperfBridge) the other two do.
 */
private fun isWindows(): Boolean =
    System.getProperty("os.name")?.lowercase()?.contains("win") == true

actual object IperfRunner {
    actual fun setTempDir(tempDir: String) {
        if (isWindows()) {
            WindowsIperf3ProcessRunner.setTempDir(tempDir)
        } else {
            NativeIperfBridge.nativeSetTempDir(tempDir)
        }
    }

    actual fun setDebug(debug: Int) {
        if (isWindows()) {
            WindowsIperf3ProcessRunner.setDebug(debug)
        } else {
            NativeIperfBridge.nativeSetDebug(debug)
        }
    }

    actual fun setStallTimeoutSeconds(seconds: Int) {
        if (isWindows()) {
            WindowsIperf3ProcessRunner.setStallTimeoutSeconds(seconds)
        } else {
            NativeIperfBridge.nativeSetStallTimeoutSeconds(seconds)
        }
    }

    actual fun forceStop(callback: IperfCallback) {
        if (isWindows()) {
            WindowsIperf3ProcessRunner.forceStop(callback)
        } else {
            NativeIperfBridge.nativeForceStop(callback)
        }
    }

    actual suspend fun executeNativeIperf3Client(arguments: Array<String>, count: Int, callback: IperfCallback): Int {
        // count is unused here — real iperf3 runs however many intervals its
        // own --time/--omit arguments dictate, unlike the simulated iOS path.
        return withContext(Dispatchers.IO) {
            if (isWindows()) {
                WindowsIperf3ProcessRunner.run(arguments, callback)
            } else {
                NativeIperfBridge.nativeIperf3(arguments, callback)
            }
        }
    }
}
