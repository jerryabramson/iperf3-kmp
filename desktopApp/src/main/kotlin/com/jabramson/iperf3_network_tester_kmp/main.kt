package com.jabramson.iperf3_network_tester_kmp

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.Window


import androidx.compose.ui.window.application
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
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "iperf3KMP",
        icon = painterResource(Res.drawable.app_icon),
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(800, 600) // pixels!
        }
        App()
    }
}