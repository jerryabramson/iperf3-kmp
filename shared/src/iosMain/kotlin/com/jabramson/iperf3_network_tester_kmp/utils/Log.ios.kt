/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.utils

import com.jabramson.iperf3_network_tester_kmp.nativelog.native_log_write
import kotlinx.cinterop.ExperimentalForeignApi

/** Calls into native/native_log.h's native_log_write() (os_log(), a plain
 * C-callable function) rather than Kotlin/Native's own NSLog(format:
 * String, vararg args: Any?) cinterop binding.
 *
 * That binding doesn't reliably marshal a Kotlin String across NSLog's
 * variadic/objc_msgSend ABI boundary - confirmed by a real crash (SIGSEGV /
 * EXC_BAD_ACCESS inside CoreFoundation's format-string handling,
 * regression-tested in LogIosTest) the first time this actually ran, when
 * the hidden dev-mode gesture (Iperf3RunViewModel.onAppTitleTapped())
 * logged its confirmation message. The original version of this file
 * worked around it by escaping "%" and passing the message as NSLog's sole
 * (non-vararg) argument - safer, but still routed the actual write through
 * that same risky binding. native_log_write() sidesteps it entirely: no
 * variadic call ever crosses into Kotlin, and os_log's fixed internal
 * format string ("%{public}s") treats the message as opaque data, so
 * there's nothing to escape at all. See native_log.h's doc comment for the
 * full rationale - this is the same "variadic/format-string work happens
 * in C, Kotlin only ever passes a plain non-variadic string" rule
 * native_format.h already established for printf-style formatting. */
@OptIn(ExperimentalForeignApi::class)
internal actual fun platformLog(level: Char, tag: String, message: String, throwable: Throwable?) {
    native_log_write("$level/$tag: $message")
    if (level == 'E') {
        throwable?.let { native_log_write(it.stackTraceToString()) }
    }
}
