package com.jabramson.iperf3_network_tester_kmp.utils

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Desktop has no share-sheet concept (see the commonMain shareCsv doc), so
 * this opens a native AWT Save File dialog instead. FileDialog.file
 * pre-fills the suggested name; the user picks the actual save location.
 *
 * FileDialog.isVisible = true blocks the calling thread in a native modal
 * event loop until the user picks a location or cancels -- standard/expected
 * for a synchronous "Save As..." dialog invoked from a button click (which
 * already runs on the AWT/Compose Desktop UI thread), not a bug.
 */
actual fun shareCsv(filename: String, csvContent: String) {
    val dialog = FileDialog(null as Frame?, "Export iperf3 History", FileDialog.SAVE)
    dialog.file = filename
    dialog.isVisible = true
    val dir = dialog.directory
    val name = dialog.file
    if (dir != null && name != null) {
        File(dir, name).writeText(csvContent)
    }
}
