/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.utils

import android.content.Intent
import androidx.core.content.FileProvider
import com.jabramson.iperf3_network_tester_kmp.AndroidAppContextHolder
import java.io.File

/**
 * Writes the CSV into the app's cache dir and shares it via a FileProvider
 * content:// URI -- a raw file:// URI would throw FileUriExposedException
 * on modern Android. The provider is declared in androidApp's
 * AndroidManifest.xml (authority "${applicationId}.fileprovider") backed by
 * res/xml/file_paths.xml, exposing exactly that cache subdirectory.
 *
 * Uses the application Context (no Activity needed) -- FLAG_ACTIVITY_NEW_TASK
 * is required for startActivity() from a non-Activity context.
 */
actual fun shareCsv(filename: String, csvContent: String) {
    val context = AndroidAppContextHolder.requireContext()
    val exportDir = File(context.cacheDir, "csv_exports").apply { mkdirs() }
    val file = File(exportDir, filename)
    file.writeText(csvContent)

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(sendIntent, "Export iperf3 History").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}
