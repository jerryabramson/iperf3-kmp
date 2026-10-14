/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.utils

/**
 * Android implementation of formatString(), calling into the native
 * `format_string()` in native/native_format.h via JNI (see
 * NativeFormatterBridge.kt and native/native_format_jni.c). This is the
 * same C function iOS reaches via cinterop and desktop reaches via its own
 * JNI build — Android has no logic of its own beyond marshaling.
 */
actual fun formatString(format: String, vararg args: Any): String {
    val result = try {
        NativeFormatterBridge.nativeFormat(format, arrayOf<Any?>(*args))
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Format string error: $format - ${e.message}", e)
    }

    return if (result.length > 4096) result.substring(0, 4096) else result
}
