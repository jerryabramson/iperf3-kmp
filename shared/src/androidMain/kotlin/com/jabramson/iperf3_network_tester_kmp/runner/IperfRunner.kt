@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.jabramson.iperf3_network_tester_kmp.runner

import com.jabramson.iperf3_network_tester_kmp.utils.NativeIperfBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual object IperfRunner {
    actual fun setTempDir(tempDir: String) {
        NativeIperfBridge.nativeSetTempDir(tempDir)
    }

    actual fun setDebug(debug: Int) {
        NativeIperfBridge.nativeSetDebug(debug)
    }

    actual fun setStallTimeoutSeconds(seconds: Int) {
        NativeIperfBridge.nativeSetStallTimeoutSeconds(seconds)
    }

    actual fun forceStop(callback: IperfCallback) {
        NativeIperfBridge.nativeForceStop(callback)
    }

    actual suspend fun executeNativeIperf3Client(arguments: Array<String>, count: Int, callback: IperfCallback): Int {
        // count is unused here — real iperf3 runs however many intervals its
        // own --time/--omit arguments dictate, unlike the simulated iOS path.
        return withContext(Dispatchers.IO) {
            NativeIperfBridge.nativeIperf3(arguments, callback)
        }
    }
}
