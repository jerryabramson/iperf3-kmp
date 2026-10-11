package com.jabramson.iperf3_network_tester_kmp.utils

import com.jabramson.iperf3_network_tester_kmp.IosRootViewControllerHolder
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.popoverPresentationController
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite

/**
 * Writes the CSV into NSTemporaryDirectory() (as a real file, so the share
 * sheet/recipient apps see a proper "<name>.csv" with the right extension,
 * not just raw text) and presents UIActivityViewController from the app's
 * one root view controller (see IosRootViewControllerHolder).
 *
 * Writes via plain POSIX fopen/fwrite rather than NSString/NSData -- this
 * stub set doesn't expose an NSString(string:) initializer or a matching
 * NSData byte-buffer factory, and POSIX file I/O is always available in
 * Kotlin/Native with no such gaps.
 *
 * popoverPresentationController.sourceView is set unconditionally: it's
 * required on iPad (where the share sheet is a popover, not a full-screen
 * modal) and harmlessly ignored on iPhone.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun shareCsv(filename: String, csvContent: String) {
    val path = NSTemporaryDirectory() + filename
    val bytes = csvContent.encodeToByteArray()
    val file = fopen(path, "wb") ?: error("Failed to open $path for writing")
    try {
        bytes.usePinned { pinned ->
            fwrite(pinned.addressOf(0), 1u, bytes.size.toULong(), file)
        }
    } finally {
        fclose(file)
    }

    val fileUrl = NSURL.fileURLWithPath(path)
    val activityViewController = UIActivityViewController(
        activityItems = listOf(fileUrl),
        applicationActivities = null,
    )
    val rootViewController = IosRootViewControllerHolder.requireController()
    activityViewController.popoverPresentationController?.sourceView = rootViewController.view
    rootViewController.presentViewController(activityViewController, animated = true, completion = null)
}
