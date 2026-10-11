package com.jabramson.iperf3_network_tester_kmp.utils

/**
 * Hand a CSV export off to the platform. Android and iOS open the native
 * Share sheet (Intent.ACTION_SEND / UIActivityViewController) so the user
 * can send the file to another app or save it to Files/Drive/etc; Desktop
 * has no share-sheet concept, so it opens a native Save File dialog
 * instead. See ShareCsv.<platform>.kt for the per-platform mechanics.
 *
 * Must be called from the main/UI thread -- presenting a UIViewController
 * on iOS requires it, and both the Android chooser Intent and the AWT save
 * dialog are UI operations too. The History screen's Export button calls
 * this synchronously from its onClick, since the CSV content is built from
 * data already held in memory (Iperf3RunViewModel.historyResultsStateFlow)
 * -- no coroutine/background dispatch needed.
 *
 * @param filename suggested file name, e.g. "iperf3-history-<timestamp>.csv"
 * @param csvContent the full CSV text to share/save (see [resultsToCsv])
 */
expect fun shareCsv(filename: String, csvContent: String)
