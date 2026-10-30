/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.view


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialogDefaults.titleContentColor
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.AndroidUiModes.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.AndroidUiModes.UI_MODE_TYPE_NORMAL
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jabramson.iperf3_network_tester_kmp.database.ResultDataEntity
import com.jabramson.iperf3_network_tester_kmp.database.toEntity
import com.jabramson.iperf3_network_tester_kmp.ui.theme.Iperf3NetworkTesterTheme
import com.jabramson.iperf3_network_tester_kmp.ui.theme.mesloFontBoldFamily
import com.jabramson.iperf3_network_tester_kmp.utils.Units
import com.jabramson.iperf3_network_tester_kmp.utils.formatGeneric
import com.jabramson.iperf3_network_tester_kmp.utils.formatIterations
import com.jabramson.iperf3_network_tester_kmp.utils.formatTime
import com.jabramson.iperf3_network_tester_kmp.utils.toHumanUnit
import com.jabramson.iperf3_network_tester_kmp.utils.toWholeNumber
import kotlin.math.round

/**
 * Screen listing every result saved to the RoomDB, most recent first (the
 * order [com.jabramson.iperf3_network_tester_kmp.database.ResultDataRepository.getAllResults]
 * already returns them in).
 * Reached via the top bar's History button (see [IperfTopBar]/[HistoryButton]),
 * which only appears once there's at least one row to show.
 *
 * @param results the saved rows to display
 * @param onBack navigate back to the Run Test screen
 * @param onDelete delete a single row, identified by its guid
 * @param onExport export every row in [results] as a CSV (see
 *   [com.jabramson.iperf3_network_tester_kmp.viewmodel.Iperf3RunViewModel.exportHistoryCsv])
 *   -- disabled when [results] is empty
 */
@Composable
fun HistoryScreen(
    results: List<ResultDataEntity> = emptyList(),
    isCompact: Boolean = false,
    onBack: () -> Unit = {},
    onDelete: (String) -> Unit = {},
    onExport: () -> Unit = {},
    preview: Boolean = false,
) {
    val barModifier = if (isCompact) Modifier.height(48.dp) else Modifier
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                modifier = barModifier,
                colors = TopAppBarDefaults.topAppBarColors(

                    containerColor = topBarContainerColor(),
                    titleContentColor = if (isDarkScheme()) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onPrimaryContainer
                ),
                navigationIcon = {
                    Button(
                        shape = MaterialTheme.shapes.medium,
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        contentPadding = if (isCompact) PaddingValues(horizontal = 2.dp, vertical = 2.dp) else ButtonDefaults.ContentPadding,
                        modifier = if (isCompact) Modifier.height(28.dp) else Modifier.padding(end = 10.dp),
                    ) {
                        val style = if (isCompact) mesloMonoTextStyle().copy(fontSize = 8.sp) else MaterialTheme.typography.titleMedium
                        Text(text = "Back", color = MaterialTheme.colorScheme.primaryContainer, style = style)
                    }
                },
                title = {
                    Text(
                        style = if (isCompact) MaterialTheme.typography.titleLargeEmphasized else MaterialTheme.typography.titleMedium,
                        text = "iperf3 Results",
                        maxLines = 1,
                    )
                },
                actions = {
                    Button(
                        shape = MaterialTheme.shapes.medium,
                        onClick = onExport,
                        enabled = results.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        contentPadding = if (isCompact) PaddingValues(horizontal = 2.dp, vertical = 2.dp) else ButtonDefaults.ContentPadding,
                        modifier = if (isCompact) Modifier.height(28.dp) else Modifier.padding(end = 10.dp),
                    ) {
                        val style = if (isCompact) mesloMonoTextStyle().copy(fontSize = 8.sp) else MaterialTheme.typography.titleMedium
                        Text(text = "Export", color = MaterialTheme.colorScheme.primaryContainer, style = style)
                    }
                }
            )
        }
    ) { padding ->
        if (results.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "No saved results yet",
                    style = mesloMonoItalicTextStyle(),
                    modifier = Modifier.padding(top = 40.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(results, key = { it.guid }) { result ->
                    HistoryRow(result = result, onDelete = { onDelete(result.guid) }, preview = preview)
                }
            }
        }
    }
}

/**
 * A single saved-result card: minimal details (direction/host, start time,
 * average bandwidth) always visible; tapping the card toggles an expanded
 * section with the rest of the saved fields. The Delete action stays visible
 * either way, but is disabled while expanded so it can't be tapped by
 * accident while reading details (see [expanded]).
 */
@Composable
private fun HistoryRow(
    result: ResultDataEntity,
    onDelete: () -> Unit,
    preview: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val monoStyle = mesloMonoTextStyle()
    val upload = result.direction == "Upload"

    val monoItalicStyle = mesloMonoItalicTextStyle()
    val monoBoldStyle = mesloMonoBoldTextStyle()

    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
    ) {
        Column(modifier = Modifier.padding(start =9.dp, end =  9.dp, top =  10.dp, bottom =  4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val arrow = if (upload) " ▶" else " ◀"
                val nl = if (result.ipv6) "\n" else " "
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.direction,
                        style = if (upload) monoBoldStyle.copy(fontSize = 12.sp) else monoItalicStyle.copy(fontSize =  12.sp),
                        color = if (upload) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = "${result.localHost}:${result.localPort}$nl$arrow$nl${result.destinationHost}:${result.destinationPort}",
                        style = if (upload) monoBoldStyle.copy(fontSize = 12.sp) else monoItalicStyle.copy(fontSize =  12.sp),
                        color = if (upload) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary,
                        textAlign = TextAlign.Left,
                    )
                    if (!expanded) {
                        Text(
                            text = "   Date: ${result.startDateTime}",
                            style = monoStyle.copy(fontSize = 12.sp),
                            textAlign = TextAlign.Left,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = "Runtime: ${formatTime(result.rawTime, preview)}",
                            style = monoStyle.copy(fontSize = 12.sp),
                            textAlign = TextAlign.Left,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = formatSavedBandwidth(prefix =  "Average", storedKbps = result.avgbps, preview = preview),
                            style = monoStyle.copy(fontSize = 12.sp),
                            textAlign = TextAlign.Left,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                if (!expanded) {
                    Button(
                        shape = MaterialTheme.shapes.medium,
                        onClick = onDelete,
                        enabled = !expanded,
                        // Same "destructive action" color pattern as RunButton's
                        // Stop state -- onErrorContainer container, surface text.
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "Delete",
                            color = MaterialTheme.colorScheme.surface,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 4.dp, start = 15.dp, end = 15.dp)) {
                    HistoryDetailLine(
                        "Local  Host:Port",
                        "${result.localHost}:${result.localPort}",
                        result.ipv6
                    )
                    HistoryDetailLine("Remote Host:Port",
                        "${result.destinationHost}:${result.destinationPort}",
                        result.ipv6
                    )
                    HistoryDetailLine("Start Date/Time", result.startDateTime, preview)
                    HorizontalDivider(modifier = Modifier.fillMaxWidth())
                    Column(modifier = Modifier.padding(top = 4.dp, start = 10.dp, end = 10.dp)) {
                        HistoryDetailLine("Direction", result.direction)
                        HistoryDetailLine("Duration", formatTime(result.duration, preview))
                        HistoryDetailLine("Parallel Streams", formatGeneric(result.parallelStreams, "Streams", preview))
                        HistoryDetailLine("Skip", formatGeneric(result.skip, "Omitted", preview))

                        HorizontalDivider(modifier = Modifier.fillMaxWidth())

                        HistoryDetailLine("Iterations", formatIterations(result.reportedIterations, preview))
                        if (result.stalled > 0)
                            HistoryDetailLine("Errors", formatGeneric(result.stalled, "Stalled", preview))
                        if (result.rawTime > 0)
                            HistoryDetailLine("Runtime", formatTime(result.rawTime, preview))
                        HistoryDetailLine("Average", formatSavedBandwidth(storedKbps = result.avgbps, preview = preview))
                        HistoryDetailLine("Max", formatSavedBandwidth(storedKbps = result.maxbps, preview = preview))
                        HistoryDetailLine("Min", formatSavedBandwidth(storedKbps = result.minbps, preview = preview))
                        HistoryDetailLine("Median", formatSavedBandwidth(storedKbps = result.medianbps, preview = preview))
                        HistoryDetailLine("Standard Deviation", formatSavedBandwidth(storedKbps = result.stdDevbps, preview = preview))
                    }
                    HorizontalDivider(modifier = Modifier.fillMaxWidth())
                    HistoryDetailLine("End Date/Time", result.endDateTime, preview)
                }
            }
        }
    }
}

/**
 * One label/value line inside a [HistoryRow]'s expanded details section.
 */
@Composable
private fun HistoryDetailLine(label: String, value: String, ipv6: Boolean = false) {
    val fontSize = if (!ipv6) 14.sp else 11.sp

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = mesloMonoTextStyle().copy(fontSize = fontSize),
            color = MaterialTheme.colorScheme.errorContainer
        )
        Text(text = value,
            style = mesloMonoTextStyle().copy(fontSize = fontSize),
            color = MaterialTheme.colorScheme.tertiaryContainer
        )
    }
}

/**
 * [ResultDataEntity.avgbps] (and the other *bps columns) are stored already
 * divided once by 1024 (see [com.jabramson.iperf3_network_tester_kmp.utils.tobps]),
 * so multiply back up before handing off to [toHumanUnit] for display.
 *
 * @param preview When true, mirrors [toHumanUnit]'s unit-selection logic
 *   with plain Kotlin math instead of calling it directly. [toHumanUnit]
 *   rounds via `truncateDouble()`, which goes through the native printf-style
 *   formatter (`StringFormatter.kt` / `NATIVE_FORMATTING_JUSTIFICATION.md`) --
 *   that native lib is never loaded during IDE/Xcode Compose Preview
 *   rendering, so calling it there throws. Same `preview` convention used
 *   elsewhere in this app (see `UnitConverter.toWholeNumber()`, `RunningColumnSection.kt`).
 */
private fun formatSavedBandwidth(prefix: String = "", storedKbps: Long, preview: Boolean = false): String {
    val rawBitsPerSec = storedKbps.toDouble() * Units.KBITS
    val p = if (prefix.isNotEmpty()) "$prefix: " else ""
    if (preview) {
        var value = rawBitsPerSec
        var unit = Units.BITS_UNIT
        when {
            value >= Units.TBITS -> { value /= Units.TBITS; unit = Units.TB_UNIT }
            value >= Units.GBITS -> { value /= Units.GBITS; unit = Units.GB_UNIT }
            value >= Units.MBITS -> { value /= Units.MBITS; unit = Units.MB_UNIT }
            value >= Units.KBITS -> { value /= Units.KBITS; unit = Units.KB_UNIT }
        }
        val rounded = round(value * 100) / 100
        return if (rounded > 0.0) "${rounded}35 $unit" else "----"
    }
    val human = toHumanUnit(rawBitsPerSec)
    return "${p}${toWholeNumber(human, preview = false)}"
}


/**
 *
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(name = "History With No Results Light",
    showBackground = true,
    device =  Devices.PIXEL_9, showSystemUi = true)
@Preview(name = "History with No Results Dark",
    showBackground = true,
    uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL,
    device =  Devices.PIXEL_9, showSystemUi = true)
annotation class PreviewHistoryWithNoResultsLightDarkWithBackground

@PreviewHistoryWithNoResultsLightDarkWithBackground
@Composable
private fun HistoryScreenEmptyPreview() {
    Iperf3NetworkTesterTheme(dynamicColor = true) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            HistoryScreen(results = emptyList())
        }
    }
}


/**
 *
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(name = "History With Results Light", showBackground = true,
    showSystemUi = true,
    device =  Devices.PIXEL_6)
@Preview(name = "History with Results Dark", showBackground = true,
    uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL,
    showSystemUi = true,
    device =  Devices.PIXEL_6)
annotation class PreviewHistoryWithResultsLightDarkWithBackground

@PreviewHistoryWithResultsLightDarkWithBackground
@Composable
private fun HistoryScreenWithResultsPreview() {
    val results = emptyList<ResultDataEntity>().toMutableList()
    for (i in 1..10) {
        val r = getSampleUiState(true)
        val s = r.iperf3RunningState.copy(isReverse = (i % 2 == 0))
        results.add(toEntity(s, 5.553, true))
    }

    Iperf3NetworkTesterTheme(dynamicColor = true) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            HistoryScreen(
                preview = true,
                results =  results,
            )
        }
    }
}
