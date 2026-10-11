package com.jabramson.iperf3_network_tester_kmp.utils

import com.jabramson.iperf3_network_tester_kmp.runner.IperfCallback

/**
 * JNI bridge to real libiperf 3.21 (native/iperf3_jni.c), built without
 * OpenSSL/SCTP/server support — see IPERF3_NATIVE_INTEGRATION.md §6.6.
 *
 * The native side binds this via `RegisterNatives()` in JNI_OnLoad, keyed
 * on this exact package/class/method — see native/iperf3_jni.c.
 */
internal object NativeIperfBridge {
    init {
        System.loadLibrary("iperf3native")
    }


    @JvmStatic
    external fun nativeSetDebug(debug: Int)

    @JvmStatic
    external fun nativeSetTempDir(tempDir: String)

    @JvmStatic
    external fun nativeSetStallTimeoutSeconds(seconds: Int)

    @JvmStatic
    external fun nativeForceStop(callback: IperfCallback)

    @JvmStatic
    external fun nativeIperf3(arguments: Array<String>, callback: IperfCallback): Int
}
