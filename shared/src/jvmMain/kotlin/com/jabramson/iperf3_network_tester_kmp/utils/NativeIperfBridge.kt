/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.utils

import com.jabramson.iperf3_network_tester_kmp.runner.IperfCallback
import java.io.File

/**
 * JNI bridge to real libiperf 3.21 (native/iperf3_jni.c), built without
 * OpenSSL/SCTP/server support — see IPERF3_NATIVE_INTEGRATION.md §6.6.
 *
 * Unlike Android, desktop JVMs can't System.loadLibrary() a library baked
 * into an AAR, so the native lib for the *current* host OS/arch is compiled
 * at build time (see shared/build.gradle.kts' compileIperf3Jvm task),
 * bundled as a jvmMain resource, and extracted to a temp file at startup —
 * same pattern as NativeFormatterBridge.kt.
 *
 * The native side binds this via `RegisterNatives()` in JNI_OnLoad, keyed
 * on this exact package/class/method — see native/iperf3_jni.c.
 */
internal object NativeIperfBridge {
    init {
        val osName = System.getProperty("os.name").lowercase().let {
            when {
                it.contains("mac") -> "macos"
                it.contains("win") -> "windows"
                else -> "linux"
            }
        }
        val archName = System.getProperty("os.arch").lowercase().let {
            when (it) {
                "aarch64", "arm64" -> "aarch64"
                else -> "x86_64"
            }
        }
        val libFileName = when (osName) {
            "macos" -> "libiperf3native.dylib"
            "windows" -> "iperf3native.dll"
            else -> "libiperf3native.so"
        }
        val resourcePath = "/iperf3native/$osName-$archName/$libFileName"
        val resourceStream = NativeIperfBridge::class.java.getResourceAsStream(resourcePath)
            ?: error("Native library resource not found: $resourcePath (built for a different host OS/arch?)")

        val tempFile = File.createTempFile("libiperf3native", "." + libFileName.substringAfterLast('.'))
        tempFile.deleteOnExit()
        resourceStream.use { input -> tempFile.outputStream().use { input.copyTo(it) } }

        System.load(tempFile.absolutePath)
    }

    @JvmStatic
    external fun nativeSetTempDir(tempDir: String)

    @JvmStatic
    external fun nativeSetDebug(debug: Int)

    @JvmStatic
    external fun nativeSetStallTimeoutSeconds(seconds: Int)

    @JvmStatic
    external fun nativeForceStop(callback: IperfCallback)

    @JvmStatic
    external fun nativeIperf3(arguments: Array<String>, callback: IperfCallback): Int
}
