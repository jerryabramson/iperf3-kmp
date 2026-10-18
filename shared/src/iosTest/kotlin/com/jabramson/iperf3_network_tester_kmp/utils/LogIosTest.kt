/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.utils

import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * Regression test for a real crash (SIGSEGV / EXC_BAD_ACCESS), not a
 * hypothetical one: the original iOS platformLog() used NSLog's "%@" +
 * vararg pattern to safely log an arbitrary string, which crashed for real
 * when the hidden dev-mode gesture (Iperf3RunViewModel.onAppTitleTapped())
 * first logged its confirmation message on a real Simulator run - see
 * Log.ios.kt's doc comment for the full root-cause writeup. This exercises
 * every level, including a message that itself contains a literal "%" (the
 * exact case the "%@" pattern was originally trying to protect, and that
 * the fix's own escaping must still get right), plus the `e(...,
 * throwable)` path, which formats a second string (the stack trace)
 * through the same code.
 */
class LogIosTest {

    @AfterTest
    fun tearDown() {
        LogConfig.developerLoggingEnabled = false
    }

    @Test
    fun allLevelsLogWithoutCrashing() {
        LogConfig.developerLoggingEnabled = true
        Log.d("LogIosTest", "debug message")
        Log.i("LogIosTest", "info message")
        Log.w("LogIosTest", "warning message")
        Log.e("LogIosTest", "error message")
        Log.e("LogIosTest", "error with throwable", RuntimeException("boom"))
    }

    @Test
    fun messageContainingLiteralPercentDoesNotCrash() {
        LogConfig.developerLoggingEnabled = true
        Log.d("LogIosTest", "50% complete, format specifiers: %s %d %@ %n")
    }

    @Test
    fun disabledLoggingIsANoOp() {
        LogConfig.developerLoggingEnabled = false
        Log.d("LogIosTest", "should never actually log")
    }
}
