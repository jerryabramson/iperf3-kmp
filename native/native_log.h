// Copyright (c) 2026, Jerold Abramson
//
// This source code is licensed under the BSD 3-Clause License found in
// the LICENSE file in the root directory of this source tree.

/*
 * native_log.h — routes iOS's Log.ios.kt actual through a real, C-native
 * variadic call instead of Kotlin/Native's NSLog(format: String,
 * vararg args: Any?) cinterop binding.
 *
 * That binding doesn't reliably marshal a Kotlin String across NSLog's
 * variadic/objc_msgSend ABI boundary — confirmed by a real crash (SIGSEGV /
 * EXC_BAD_ACCESS inside CoreFoundation's format-string handling) the first
 * time the app actually called it. This applies the same rule
 * native_format.h already established for printf-style formatting: do any
 * variadic/format-string work entirely in C, and cross into Kotlin only
 * with a plain, non-variadic `const char *`. See native_format.h's own doc
 * comment, NATIVE_FORMATTING_JUSTIFICATION.md, and PRINTF_FORMATTING_CASE_STUDY.md
 * for the original instance of this same lesson.
 *
 * os_log(), not NSLog() — it's Apple's modern replacement, is a plain
 * C-callable function (no Objective-C runtime/NSString bridging needed at
 * all, unlike NSLog), and still reaches Console.app/the unified system log
 * on a real device without an attached debugger, which is the whole reason
 * this exists (see Log.ios.kt's own doc comment). `%{public}s` marks the
 * message as public - os_log redacts dynamic string content as "<private>"
 * by default outside a debug/developer configuration, which would defeat
 * the point of a real-device diagnostic log.
 *
 * `static inline` and fully defined here (like native_format.h) so
 * Kotlin/Native's cinterop, which compiles headers directly, needs no
 * separate library to link. Not needed on Android/desktop - their Log
 * actuals (android.util.Log, println) never cross this kind of FFI
 * boundary, so they were never at risk of this bug.
 */
#ifndef NATIVE_LOG_H
#define NATIVE_LOG_H

#if defined(__APPLE__)

#include <os/log.h>

/* All of the app's own log lines go under one subsystem, so they can be
 * picked out of the system's own noise (keyboard, RunningBoard, ...) with a
 * single Xcode console / Console.app filter:
 *     subsystem:com.jabramson.iperf3-network-tester-kmp
 * OS_LOG_DEFAULT has no subsystem, which left text matching as the only
 * filter, and that missed lines (e.g. stack traces) that don't contain a
 * recognisable word. Categories: "app" for Kotlin's Log (Log.ios.kt),
 * "bridge" for iperf3_bridge.c's IPERF_LOG. */
#define NATIVE_LOG_SUBSYSTEM "com.jabramson.iperf3-network-tester-kmp"

/* Lazily created once per category. Two threads racing the first call can
 * both call os_log_create(); that's harmless (equivalent handles, one is
 * simply never released). */
static inline os_log_t native_log_handle(int bridge) {
    static os_log_t app_log = NULL;
    static os_log_t bridge_log = NULL;
    if (bridge) {
        if (bridge_log == NULL) bridge_log = os_log_create(NATIVE_LOG_SUBSYSTEM, "bridge");
        return bridge_log;
    }
    if (app_log == NULL) app_log = os_log_create(NATIVE_LOG_SUBSYSTEM, "app");
    return app_log;
}

static inline void native_log_write(const char *message) {
    os_log(native_log_handle(0), "%{public}s", message);
}

static inline void native_log_write_bridge(const char *message) {
    os_log(native_log_handle(1), "%{public}s", message);
}

#endif /* __APPLE__ */

#endif /* NATIVE_LOG_H */
