/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.view

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Devices.PIXEL_6
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jabramson.iperf3_network_tester_kmp.utils.getHeading
//import com.jabramson.iperf3_network_tester_kmp.utils.getHeading


@Preview(name = "Debug Output Section", showBackground = true,
    device = PIXEL_6, showSystemUi = true)
@Composable
fun DebugOutputSection(
    isDebugging: Boolean = true,
    isOmitted: Boolean = getSampleUiState(true).iperf3RunningState.omitted,
    outputLines: MutableList<String> = getSampleUiState(true).outputLines,
    monoStyle: TextStyle = mesloMonoTextStyle(),
    preview: Boolean = true,
    isWide: Boolean = false,
    isTall: Boolean = false,
    isTiny: Boolean = false
) {
    if (!isDebugging) return
    val fontSize = if (isTiny) 10.sp
    else if (isTall || isWide) 11.sp
    else 10.sp


    val style = monoStyle.copy(fontSize = fontSize)
    val color =
        if (isOmitted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    // will always be below even the largest notch, so no extra padding needed
    if (outputLines.isNotEmpty()) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "iPerf3 Output",
                style = if (!isWide) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp)) {
            val density = LocalDensity.current
            val textMeasurer = rememberTextMeasurer()

            // Every row (and the heading) is printf-formatted to a fixed character
            // width, so a single real line tells us exactly how wide a column needs
            // to be. Fall back to the heading before any output has a first line.
            val sampleText = outputLines.firstOrNull() ?: getHeading(preview)
            val rowWidthPx = remember(sampleText, style) {
                textMeasurer.measure(sampleText, style).size.width
            }
            // Each column's own start/end padding plus its 1dp border on both sides.
            val columnChromeWidthPx = with(density) { (12.dp + 12.dp + 1.dp + 1.dp).toPx() }
            val columnWidthPx = (rowWidthPx + columnChromeWidthPx) //- 30 // hack
            val availableWidthPx = with(density) { maxWidth.toPx() }
            val columnCount = (availableWidthPx / columnWidthPx).toInt().coerceAtLeast(1)

            // Approximate row height from the item font size, with margin so we
            // never overestimate how many rows fit (that would force the "pinned"
            // columns to scroll, defeating the point of them).
            val rowHeightPx = with(density) { style.fontSize.toPx() * 1.7f }
            // Heading row's own top/bottom padding (4dp + 4dp) plus its 1dp divider.
            val headingReservePx = rowHeightPx + with(density) { 9.dp.toPx() }
            val availableRowsPx = with(density) { maxHeight.toPx() } - headingReservePx
            val visibleRowsPerColumn = (availableRowsPx / rowHeightPx).toInt().coerceAtLeast(1)

            // Every column but the last is pinned to a fixed slice of the most recent
            // remaining lines and never scrolls. The last column keeps everything
            // left over and scrolls on its own -- so the split points don't reshuffle
            // on every new line the way an even split of the whole history would.
            val columns = splitIntoColumns(outputLines, columnCount, visibleRowsPerColumn)

            Row {
                columns.forEach { columnLines ->
                    OutputBox(
                        modifier = Modifier.weight(1f)
                            .padding(start = 2.dp, end = 2.dp),
                        outputLines = columnLines,
                        style = style,
                        color = color,
                        preview = preview
                    )
                }
            }
        }
    }
}

/**
 * Splits [outputLines] (oldest first) into [columnCount] left-to-right buckets for
 * newest-to-oldest display: every column but the last holds up to [rowsPerColumn] of
 * the most recent remaining lines; the last column keeps everything left over.
 */
private fun splitIntoColumns(
    outputLines: List<String>,
    columnCount: Int,
    rowsPerColumn: Int
): List<MutableList<String>> {
    if (columnCount <= 1) return listOf(outputLines.toMutableList())
    val columns = mutableListOf<MutableList<String>>()
    var remainingEnd = outputLines.size
    repeat(columnCount - 1) {
        val take = minOf(rowsPerColumn, remainingEnd)
        val fromIndex = remainingEnd - take
        columns.add(outputLines.subList(fromIndex, remainingEnd).toMutableList())
        remainingEnd = fromIndex
    }
    columns.add(outputLines.subList(0, remainingEnd).toMutableList())
    return columns
}

@Composable
fun OutputBox(modifier: Modifier = Modifier,
              style: TextStyle,
              color: Color,
              outputLines: MutableList<String>,
              preview: Boolean = true) {

    Column(
        modifier = modifier.border(width = 1.dp, color = MaterialTheme.colorScheme.primaryFixedDim)
    ) {
        if (outputLines.isNotEmpty()) {
            Text(
                text = getHeading(preview),
                textAlign = TextAlign.Left,
                style = style,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 4.dp, bottom = 4.dp)
            )
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.primaryFixedDim)
            LazyColumn(modifier = Modifier.padding(bottom = 2.dp)) {
                items(outputLines.size) { index ->
                    val reverseIndex = outputLines.size - index - 1
                    val str =
                        outputLines[reverseIndex]
                    DebugOutputItem(
                        outputLines[reverseIndex],
                        style = style,
                        color = MaterialTheme.colorScheme.onSurface,
                        stalled = str.lowercase().contains("stalled"),
                        skipped = str.lowercase().contains("omitted")
                    )
                }
            }
        }
    }
}

@Composable
fun DebugOutputItem(text: String,
                    style: TextStyle,
                    color: Color,
                    stalled: Boolean = false,
                    skipped: Boolean = false) {
    Text(
        text = text,
        textAlign = TextAlign.Left,
        modifier = Modifier.fillMaxWidth().padding(start = 10.dp),
        style = style.copy(textDecoration = if (stalled) TextDecoration.LineThrough else TextDecoration.None),
        color = if (!stalled && !skipped) color else MaterialTheme.colorScheme.onErrorContainer

    )
}