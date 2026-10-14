/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.jabramson.iperf3_network_tester_kmp.runner

/**
 * Runs an iperf3 client test. Android/JVM actuals call real libiperf 3.21
 * via NativeIperfBridge (native/iperf3_jni.c); iOS still simulates (native
 * integration not yet done there — see IPERF3_NATIVE_INTEGRATION.md).
 */
expect object IperfRunner {
    fun setTempDir(tempDir: String)

    /** Seconds of no-progress the native watchdog tolerates once a test has
     * actually started transferring data before treating it as stalled and
     * aborting (see native/iperf3_jni.c's monitorThreadFunc). Android/JVM
     * only — a no-op on iOS's simulation, which has no such watchdog. */
    fun setStallTimeoutSeconds(seconds: Int)

    fun setDebug(debug: Int)

    fun forceStop(callback: IperfCallback)

    /** @return iperf3's own return code (0 = success, negative = failure) —
     * Android/JVM propagate the real libiperf result; iOS's simulation
     * (no real test to succeed/fail) always returns 0. */
    suspend fun executeNativeIperf3Client(arguments: Array<String>, count: Int, callback: IperfCallback): Int
}
