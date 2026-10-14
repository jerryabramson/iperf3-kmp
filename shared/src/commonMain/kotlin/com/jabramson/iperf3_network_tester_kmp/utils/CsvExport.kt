/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.utils

import com.jabramson.iperf3_network_tester_kmp.database.ResultDataEntity

private val CSV_HEADER = listOf(
    "guid", "startDateTime", "endDateTime", "direction",
    "localHost", "localPort", "destinationHost", "destinationPort",
    "duration", "parallelStreams", "skip", "reportedIterations",
    "maxbps", "minbps", "avgbps", "medianbps", "stdDevbps",
)

/**
 * Builds a CSV (RFC 4180-ish, CRLF line endings) rendering of [results] for
 * the History screen's Export button.
 *
 * The *bps columns are exported as the raw stored values (already divided
 * once by 1024 -- see [tobps]), not the human-formatted "12.34 Mbits/sec"
 * strings used on screen. That keeps the export a clean, re-importable/
 * spreadsheet-friendly data dump rather than a display artifact, and avoids
 * routing through the native printf-style formatter (see
 * [com.jabramson.iperf3_network_tester_kmp.utils.toHumanUnit])
 * for what's meant to be a raw data export anyway.
 */
fun resultsToCsv(results: List<ResultDataEntity>): String {
    val sb = StringBuilder()
    sb.append(CSV_HEADER.joinToString(",")).append("\r\n")
    for (r in results) {
        val fields = listOf(
            r.guid, r.startDateTime, r.endDateTime, r.direction,
            r.localHost, r.localPort.toString(), r.destinationHost, r.destinationPort.toString(),
            r.duration.toString(), r.parallelStreams.toString(), r.skip.toString(), r.reportedIterations.toString(),
            r.maxbps.toString(), r.minbps.toString(), r.avgbps.toString(), r.medianbps.toString(), r.stdDevbps.toString(),
        )
        sb.append(fields.joinToString(",") { csvField(it) }).append("\r\n")
    }
    return sb.toString()
}

/**
 * Quotes a CSV field only if it needs it (contains a comma, quote, or
 * newline), doubling any internal quotes per RFC 4180.
 */
private fun csvField(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"${value.replace("\"", "\"\"")}\""
    } else {
        value
    }
