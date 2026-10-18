/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.utils

import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets

/**
 * Drops the console window a Windows GUI launch would otherwise keep open.
 *
 * The Windows launcher is a console program (`console = true` in
 * desktopApp/build.gradle.kts) so CLI mode's output reaches the terminal and
 * the shell waits for it to finish. A Start-menu launch therefore opens a
 * console window as well; [detachForGui] gets rid of it before the GUI shows,
 * leaving a brief flash. No-op on macOS/Linux.
 *
 * Native side: native/win_console_jni.c, built into the same desktop library
 * as [NativeFormatterBridge] (which is what loads it).
 */
object WindowsConsole {
    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    private const val GUI_CHILD_ENV = "IPERF3KMP_GUI_CHILD"

    /**
     * Gets rid of the console window before the GUI opens. Returns true when
     * the caller should just exit, because a console-less copy of the app has
     * been started to show the GUI instead.
     *
     * `FreeConsole()` alone isn't enough: when Windows Terminal is the default
     * terminal (Windows 11), it keeps the tab open until the *process* exits.
     * So this starts the same launcher again with no console (Java's
     * ProcessBuilder uses CREATE_NO_WINDOW on Windows) and lets this process
     * exit, which closes the tab in Windows Terminal and the classic console
     * alike. [GUI_CHILD_ENV] marks the copy so it doesn't do the same. If that
     * fails, falls back to `FreeConsole()` and the GUI in this process.
     */
    fun detachForGui(): Boolean {
        if (!isWindows || System.getenv(GUI_CHILD_ENV) != null) return false
        val launcher = ProcessHandle.current().info().command().orElse(null)
        if (launcher != null && launcher.endsWith(".exe", ignoreCase = true) &&
            !File(launcher).name.equals("java.exe", ignoreCase = true)
        ) {
            try {
                ProcessBuilder(launcher)
                    .redirectInput(ProcessBuilder.Redirect.from(File("NUL")))
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .also { it.environment()[GUI_CHILD_ENV] = "1" }
                    .start()
                return true
            } catch (_: Exception) {
                // fall through to detaching in-process
            }
        }
        NativeFormatterBridge.ensureLoaded()
        freeConsole()
        return false
    }

    /**
     * For CLI mode: make the console UTF-8 with ANSI escapes enabled, and
     * re-wrap stdout/stderr as UTF-8. Without this, Java encodes output in the
     * console's OEM code page (437 by default), so every `─` rule and progress
     * glyph prints as `?`. The previous code page is put back on exit, so the
     * user's terminal isn't left changed. No-op on macOS/Linux.
     */
    fun prepareForCli() {
        if (!isWindows) return
        NativeFormatterBridge.ensureLoaded()
        val previousCodePage = enableUtf8Output()
        System.setOut(PrintStream(FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8))
        System.setErr(PrintStream(FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8))
        if (previousCodePage > 0) {
            Runtime.getRuntime().addShutdownHook(Thread { restoreOutputCodePage(previousCodePage) })
        }
    }

    @JvmStatic
    private external fun freeConsole(): Boolean

    @JvmStatic
    private external fun enableUtf8Output(): Int

    @JvmStatic
    private external fun restoreOutputCodePage(codePage: Int)
}
