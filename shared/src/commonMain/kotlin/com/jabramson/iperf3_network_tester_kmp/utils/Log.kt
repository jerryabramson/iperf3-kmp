/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.utils

/**
 * Developer-only diagnostic logging - NOT the same thing as the user-facing
 * "Output ON/Off" button (isDebugging/DebugOutputSection), which shows raw
 * iperf3 text output inside the app's own UI and is a real end-user
 * feature. This writes to the platform's system log (logcat/NSLog/stderr) -
 * places a typical end user never looks - so it stays off by default and is
 * only reachable via the hidden developer-mode unlock gesture (tap the app
 * title 7 times, mirroring Android's own "tap the build number" Developer
 * Options convention) - see Iperf3RunViewModel.onAppTitleTapped().
 */
object LogConfig {
    var developerLoggingEnabled: Boolean = false
}

/**
 * Minimal expect/actual replacement for `android.util.Log`, which isn't
 * visible outside androidMain - lets commonMain code log without an
 * Android-only import breaking iOS/desktop compilation. Mirrors Android's
 * own `Log.d`/`i`/`w`/`e` surface so call sites read the same on every
 * platform. Only `platformLog` itself (the "how do I actually write this
 * out" part) is expect/actual - the enable/disable gate lives here, once,
 * in commonMain, rather than being repeated per platform.
 */
object Log {
    fun d(tag: String, message: String) {
        if (LogConfig.developerLoggingEnabled) platformLog('D', tag, message, null)
    }

    fun i(tag: String, message: String) {
        if (LogConfig.developerLoggingEnabled) platformLog('I', tag, message, null)
    }

    fun w(tag: String, message: String) {
        if (LogConfig.developerLoggingEnabled) platformLog('W', tag, message, null)
    }

    /**
     * Errors are always logged
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        platformLog('E', tag, message, throwable)
    }
}

internal expect fun platformLog(level: Char, tag: String, message: String, throwable: Throwable?)
