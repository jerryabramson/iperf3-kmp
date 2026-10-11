package com.jabramson.iperf3_network_tester_kmp.utils

/**
 * Desktop JVM implementation of formatString(), calling into the native
 * `format_string()` in native/native_format.h via JNI (see
 * NativeFormatterBridge.kt and native/native_format_jni.c). This is the
 * same C function Android reaches via its own JNI build and iOS reaches
 * via cinterop — desktop has no logic of its own beyond marshaling.
 */
actual fun formatString(format: String, vararg args: Any): String {
    val result = try {
        NativeFormatterBridge.nativeFormat(format, arrayOf<Any?>(*args))
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Format string error: $format - ${e.message}", e)
    }

    return if (result.length > 4096) result.substring(0, 4096) else result
}
