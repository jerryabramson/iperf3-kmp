/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.Window


import androidx.compose.ui.window.application
import com.jabramson.iperf3_network_tester_kmp.cli.CliRunner
import com.jabramson.iperf3_network_tester_kmp.utils.WindowsConsole
import iperf3kmp.shared.generated.resources.Res
import iperf3kmp.shared.generated.resources.app_icon
import org.jetbrains.compose.resources.painterResource
import java.awt.Dimension


/*
 * Minimum Window Size
 * --------------------
 * Modern approach — Window API v2 (Compose Multiplatform 1.12+)
 * The redesigned Window() composable in the androidx.compose.ui.window.v2 package
 * accepts minSize and maxSize parameters directly, so it's fully declarative:
 *
 * import androidx.compose.ui.window.v2.Window
 * ```kotlin
 * fun main() = application {
 *   Window(
 *       onCloseRequest = ::exitApplication,
 *       state = rememberWindowState(),
 *       minSize = DpSize(400.dp, 300.dp), // user can't shrink below this
 *       maxSize = DpSize(1600.dp, 900.dp) // optional upper bound
 *   ) {
 *       // content
 *   }
 * }
 * ```
 *
 * Older versions — set it on the underlying Swing window
 * Compose for Desktop is built on Swing/AWT, and older Window()
 * composables have no min-size parameter. You can reach the underlying
 * ComposeWindow (a JFrame) via the window property in composable scope
 */
/**
 * With command-line arguments, runs a real iperf3 test in CLI mode (see
 * `cli/CliRunner.kt`) and exits -- no Compose window at all. With none,
 * launches the GUI exactly as before. The same jpackage-built launcher
 * binary serves both uses, the same way `java -jar BandWidthTester.jar
 * <server>` does for https://github.com/jerryabramson/BandWidthTester, the
 * standalone iperf3 CLI wrapper CLI mode is modeled on.
 */
fun main(args: Array<String>) {
    if (args.isNotEmpty()) {
        // Windows: UTF-8 + ANSI console, or rules/glyphs print as '?'. No-op elsewhere.
        WindowsConsole.prepareForCli()
        kotlin.system.exitProcess(CliRunner.run(args))
    }
    // Windows' launcher is a console program (for CLI mode); get rid of that
    // console before the GUI opens. True means a console-less copy of the app
    // was started to show the GUI, so this one exits. No-op elsewhere.
    if (WindowsConsole.detachForGui()) kotlin.system.exitProcess(0)
    runGui()
}

private fun runGui() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "iperf3KMP",
        icon = painterResource(Res.drawable.app_icon),
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(840, 600) // pixels!
        }
        App()
    }
}