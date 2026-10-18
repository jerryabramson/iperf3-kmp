// Copyright (c) 2026, Jerold Abramson
//
// This source code is licensed under the BSD 3-Clause License found in
// the LICENSE file in the root directory of this source tree.

/*
 * win_console_jni.c — Windows console handling for the desktop app:
 *  - freeConsole(): drop the console window when starting in GUI mode;
 *  - enableUtf8Output()/restoreOutputCodePage(): for CLI mode, switch the
 *    console to UTF-8 and turn on ANSI escape processing, so the box-drawing
 *    rules and progress glyphs don't arrive as '?' under the default OEM code
 *    page (437), and colors work in the legacy console too. Same technique as
 *    BandWidthTester's OS.setWindowsConsoleMode() (which uses JNA), except
 *    the previous code page is restored on exit.
 *
 * The Windows launcher is built as a *console* program (`console = true` in
 * desktopApp/build.gradle.kts) so the CLI mode's output reaches the terminal
 * and the shell waits for it. The cost is that a Start-menu/double-click
 * launch gets a console window too; main() calls freeConsole() before
 * opening the GUI, so that window only flashes briefly.
 *
 * Desktop-JVM only: compiled into the same library as native_format_jni.c by
 * :shared's compileNativeFormatJvm task (not by Android's :nativeformat CMake
 * build). That file owns the library's JNI_OnLoad/RegisterNatives, so this
 * one binds by the standard JNI symbol name instead -- "_1" is how JNI
 * escapes the underscores in this project's package name. Kotlin side:
 * com.jabramson.iperf3_network_tester_kmp.utils.WindowsConsole
 */

#include <jni.h>

#ifdef _WIN32
#include <windows.h>
#ifndef ENABLE_VIRTUAL_TERMINAL_PROCESSING
#define ENABLE_VIRTUAL_TERMINAL_PROCESSING 0x0004
#endif
#endif

/* Returns the console's previous output code page (0 if none / not Windows),
 * for restoreOutputCodePage(). Harmless when output isn't a console (e.g.
 * redirected to a file): the Win32 calls just fail. */
JNIEXPORT jint JNICALL
Java_com_jabramson_iperf3_1network_1tester_1kmp_utils_WindowsConsole_enableUtf8Output(JNIEnv *env, jclass clazz) {
    (void)env;
    (void)clazz;
#ifdef _WIN32
    UINT previous = GetConsoleOutputCP();
    SetConsoleOutputCP(CP_UTF8);
    DWORD ids[] = { STD_OUTPUT_HANDLE, STD_ERROR_HANDLE };
    for (int i = 0; i < 2; i++) {
        HANDLE h = GetStdHandle(ids[i]);
        DWORD mode = 0;
        if (h != NULL && h != INVALID_HANDLE_VALUE && GetConsoleMode(h, &mode)) {
            SetConsoleMode(h, mode | ENABLE_VIRTUAL_TERMINAL_PROCESSING);
        }
    }
    return (jint)previous;
#else
    return 0;
#endif
}

JNIEXPORT void JNICALL
Java_com_jabramson_iperf3_1network_1tester_1kmp_utils_WindowsConsole_restoreOutputCodePage(JNIEnv *env, jclass clazz, jint codePage) {
    (void)env;
    (void)clazz;
#ifdef _WIN32
    if (codePage > 0) SetConsoleOutputCP((UINT)codePage);
#else
    (void)codePage;
#endif
}

JNIEXPORT jboolean JNICALL
Java_com_jabramson_iperf3_1network_1tester_1kmp_utils_WindowsConsole_freeConsole(JNIEnv *env, jclass clazz) {
    (void)env;
    (void)clazz;
#ifdef _WIN32
    return FreeConsole() ? JNI_TRUE : JNI_FALSE;
#else
    return JNI_FALSE;
#endif
}
