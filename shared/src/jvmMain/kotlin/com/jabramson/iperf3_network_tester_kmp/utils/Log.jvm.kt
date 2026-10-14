/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.utils

/** Desktop has no logcat equivalent - d/i go to stdout, w/e to stderr,
 * matching the usual severity/stream convention. */
internal actual fun platformLog(level: Char, tag: String, message: String, throwable: Throwable?) {
    when (level) {
        'D' -> println("D/$tag: $message")
        'I' -> println("I/$tag: $message")
        'W' -> System.err.println("W/$tag: $message")
        'E' -> {
            System.err.println("E/$tag: $message")
            throwable?.printStackTrace(System.err)
        }
    }
}
