package com.jabramson.iperf3_network_tester_kmp

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    val viewController = ComposeUIViewController { App() }
    // Stashed for utils/ShareCsv.ios.kt -- see IosRootViewControllerHolder.
    IosRootViewControllerHolder.init(viewController)
    return viewController
}
