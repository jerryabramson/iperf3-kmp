package com.jabramson.iperf3_network_tester_kmp

import platform.UIKit.UIViewController

/**
 * Stashed once from MainViewController() -- there's no DI framework in this
 * app (mirrors AndroidAppContextHolder's role on Android). Presenting a
 * native share sheet (UIActivityViewController, see
 * utils/ShareCsv.ios.kt) needs a UIViewController to present from, and this
 * app has exactly one for its whole lifetime.
 */
object IosRootViewControllerHolder {
    private var controller: UIViewController? = null

    fun init(viewController: UIViewController) {
        controller = viewController
    }

    fun requireController(): UIViewController =
        controller ?: error(
            "IosRootViewControllerHolder not initialized — MainViewController() should have set it."
        )
}
