/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

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
