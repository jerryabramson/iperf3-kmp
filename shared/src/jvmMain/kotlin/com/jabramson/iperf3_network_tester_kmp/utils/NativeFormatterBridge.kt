/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.utils

import java.io.File

/**
 * JNI bridge to the C `format_string()` in native/native_format.h — the
 * single source of truth for printf-style formatting, shared with Android
 * (its own JNI build via :nativeformat) and iOS (cinterop).
 *
 * Unlike Android, desktop JVMs can't System.loadLibrary() a library baked
 * into an AAR, so the native lib for the *current* host OS/arch is compiled
 * at build time (see shared/build.gradle.kts' compileNativeFormatJvm task),
 * bundled as a jvmMain resource, and extracted to a temp file at startup.
 *
 * The native side binds this via `RegisterNatives()` in JNI_OnLoad, keyed
 * on this exact package/class/method — see native/native_format_jni.c.
 */
internal object NativeFormatterBridge {
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
            "macos" -> "libnativeformat.dylib"
            "windows" -> "nativeformat.dll"
            else -> "libnativeformat.so"
        }
        val resourcePath = "/nativeformat/$osName-$archName/$libFileName"
        val resourceStream = NativeFormatterBridge::class.java.getResourceAsStream(resourcePath)
            ?: error("Native library resource not found: $resourcePath (built for a different host OS/arch?)")

        val tempFile = File.createTempFile("libnativeformat", "." + libFileName.substringAfterLast('.'))
        tempFile.deleteOnExit()
        resourceStream.use { input -> tempFile.outputStream().use { input.copyTo(it) } }

        System.load(tempFile.absolutePath)
    }

    /** Forces the `init` block above to run, for other natives built into the
     * same library (see [WindowsConsole]). */
    fun ensureLoaded() {}

    @JvmStatic
    external fun nativeFormat(format: String, args: Array<Any?>): String
}
