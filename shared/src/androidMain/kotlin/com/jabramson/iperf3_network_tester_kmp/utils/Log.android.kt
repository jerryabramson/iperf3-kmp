package com.jabramson.iperf3_network_tester_kmp.utils

internal actual fun platformLog(level: Char, tag: String, message: String, throwable: Throwable?) {
    when (level) {
        'D' -> android.util.Log.d(tag, message)
        'I' -> android.util.Log.i(tag, message)
        'W' -> android.util.Log.w(tag, message)
        'E' -> if (throwable != null) {
            android.util.Log.e(tag, message, throwable)
        } else {
            android.util.Log.e(tag, message)
        }
    }
}
