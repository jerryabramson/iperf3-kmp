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

static inline void native_log_write(const char *message) {
    os_log(OS_LOG_DEFAULT, "%{public}s", message);
}

#endif /* __APPLE__ */

#endif /* NATIVE_LOG_H */
