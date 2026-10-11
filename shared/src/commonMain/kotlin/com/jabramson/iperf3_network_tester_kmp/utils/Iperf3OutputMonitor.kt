package com.jabramson.iperf3_network_tester_kmp.utils

//import android.annotation.SuppressLint
//import androidx.compose.ui.text.intl.Locale

import com.jabramson.iperf3_network_tester_kmp.getAppUUID
import com.jabramson.iperf3_network_tester_kmp.model.Iperf3RunningState
import com.jabramson.iperf3_network_tester_kmp.viewmodel.UiInputData
//import java.util.Locale
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.roundToLong
import kotlin.math.sqrt



/**
 * Parses iperf3 stdout lines and accumulates bandwidth statistics.
 * The state is explicit, testable, and not shared across tests.
 *
 *  * The eventual goal will be to replace this error-prone text parsing with the iperf3 supported
 *    JSON-output format.
 *
 *  * The most recent versions of iperf3 support the '--forceflush' flag for JSON as well as
 *    standard text output.
 *
 * NOTE: At the time this code was originally designed this capability did not exist.
 *
 */
class Iperf3OutputMonitor {
    // -- accumulated state (private, no static fields) --
    private var currentIperf3RunningState: Iperf3RunningState = Iperf3RunningState()
    private var gathered = false
    private var isGathering = true
    private var lastResult = ""
    private var summaryResults = false
    private var finished = false
    private var lastOmitted = false
    private var isSingleThread = true
    private var historicalResults: MutableList<Double> = emptyList<Double>().toMutableList()

    /*
     * iperf3_bridge.c fires callbacks (onOutput/onError/onComplete) from
     * several native threads (the reader thread, the stderr-reader thread,
     * and the thread that called iperf3_bridge_native_iperf3 itself for its
     * final message), and on iOS those calls land directly in Kotlin with
     * no forced serialization (unlike JNI, which at least pays per-call
     * attach overhead). Two of those threads racing a `messages.add()`
     * against another thread's `messages.toMutableList()` throws
     * kotlin.ConcurrentModificationException — this lock serializes every
     * read/write of this monitor's state, and every method that hands a
     * Iperf3RunningState out to a caller returns a fresh copy (via
     * snapshotLocked()) so nobody outside ever holds a reference to the
     * still-being-mutated `messages` list.
     */
    @OptIn(ExperimentalAtomicApi::class)
    private val stateLock = AtomicBoolean(false)

    @OptIn(ExperimentalAtomicApi::class)
    private inline fun <T> withStateLock(block: () -> T): T {
        while (!stateLock.compareAndSet(expectedValue = false, newValue = true)) {
            // Busy-spin: critical sections here are plain field/list
            // mutations with no I/O, so held time is microseconds.
        }
        try {
            return block()
        } finally {
            stateLock.store(false)
        }
    }

    /** Must only be called while holding stateLock. */
    private fun snapshotLocked(): Iperf3RunningState =
        currentIperf3RunningState.copy(messages = currentIperf3RunningState.messages.toMutableList())

    /** Reset all accumulated state for a new test run. */
   fun reset(params: UiInputData): Unit = withStateLock {
        currentIperf3RunningState = Iperf3RunningState(
            isReverse = params.isReverse,
            duration = params.durationSecs,
            parallelStreams = params.parallelStreams,
            skip = params.skip,
            guid = getAppUUID(),
            microProgress = 0
        )
        gathered = false
        isGathering = true
        lastResult = ""
        summaryResults = false
        finished = false
        lastOmitted = false
        isSingleThread = params.parallelStreams == 1
        historicalResults = emptyList<Double>().toMutableList()
    }


    fun processCancelLine(line: String): Iperf3RunningState = withStateLock {
        currentIperf3RunningState.messages.add(line)
        snapshotLocked()
    }
    /** Parse one iperf3 output line and return all extracted data at once. */

    //@SuppressLint("DefaultLocale"
    fun processLine(line: String): Iperf3RunningState = withStateLock {
        val cleanLine = line.replace("\n", "")
        currentIperf3RunningState.rawOutputLine = cleanLine
        val firstLeftBracket = cleanLine.indexOf('[')
        val firstRightBracket = cleanLine.indexOf(']')
        if (firstLeftBracket in 0..<firstRightBracket) {
            val id = cleanLine.substring(firstLeftBracket + 1, firstRightBracket).trim()
            val restOfLine = cleanLine.substring(firstRightBracket + 1).split(Regex("[ \\t]+"))
            // Connection-info line (e.g. "[ ID ] Interval...")
            if (id.lowercase().equals("id")) {
                if (!gathered) {
                    gathered = true
                    currentIperf3RunningState.intervalNumber = 0
                }
            } else {
                // Pre-gathering: extract connection details or collect messages
                if (!gathered) {
                    if (restOfLine.size == 10) {
                        currentIperf3RunningState.localHost = restOfLine[2].trim()
                        currentIperf3RunningState.localPort = restOfLine[4].trim().toLongOrNull() ?: -1L
                        currentIperf3RunningState.remoteHost = restOfLine[7].trim()
                        currentIperf3RunningState.remotePort = restOfLine[9].trim().toLongOrNull() ?: -1L
                        currentIperf3RunningState.connectedString = restOfLine[5].trim()
                        currentIperf3RunningState.timeout = restOfLine[6].trim()

                        /*
                         * Note that the formatString() utility functions are implemented using native C code.
                         * This document provides a more thorough analysis:
                         *    * <git_root>/IPERF3_NATIVE_INTEGRATION.md provides a more thorough
                         */
                        val localHostPort =  formatString("%s:%-5d",currentIperf3RunningState.localHost, currentIperf3RunningState.localPort)
                        val remoteHostPort = formatString("%s:%-5d", currentIperf3RunningState.remoteHost, currentIperf3RunningState.remotePort)

                        currentIperf3RunningState.localHostDetails =  " Local Host:port $localHostPort"
                        currentIperf3RunningState.remoteHostDetails = "Remote Host:port $remoteHostPort"
                        currentIperf3RunningState.rawLocalHostDetails = localHostPort
                        currentIperf3RunningState.rawRemoteHostDetails = remoteHostPort
                        currentIperf3RunningState.messages.add(" Local Host:port $localHostPort")
                        currentIperf3RunningState.messages.add("Remote Host:port $remoteHostPort")
                        gathered = true
                    }
                } else {

                    /*
                     * Post-gathering: interval / summary data:
                     */
                    if (restOfLine.size >= 7) {
                        val intervalString = restOfLine[1].trim()
                        val bitRateString = restOfLine[5].trim()
                        val bitRateUnitString = restOfLine[6].trim()
                        var sendOrReceive = ""
                        for (i in 7..10) {
                            if (i < restOfLine.size) {
                                sendOrReceive = restOfLine[i].trim()
                            }
                        }
                        if (sendOrReceive.isNotEmpty()) {
                            val lower = sendOrReceive.lowercase()
                            if (!lower.contains("sender") && !lower.contains("receive") && !lower.contains("omit")) {
                                sendOrReceive = ""
                            }
                        }

                        // Micro steps, not including SUM
                        if (!isGathering && (isSingleThread || !id.contains("SUM"))) {
                            if (sendOrReceive.isEmpty()) {
                                currentIperf3RunningState.microProgress++
                            }
                        }

                        // Only process SUM lines or single-thread intervals
                        if (id.contains("SUM") || isSingleThread) {
                            isGathering = false
                            val bitRateValue = bitRateString.toDoubleOrNull() ?: -1.0
                            currentIperf3RunningState.currentBandWidth = UnitConvertedData(bitRateValue, bitRateUnitString)
                            currentIperf3RunningState.basicBandWidthString = "$bitRateString $bitRateUnitString"
                            val timeLabel: String
                            when (sendOrReceive.lowercase()) {
                                "(omitted)" -> {
                                    lastOmitted = true
                                    timeLabel = "omitted"
                                    currentIperf3RunningState.totalOmitted++
                                    currentIperf3RunningState.omitted = true
                                    currentIperf3RunningState.basicBandWidthString = "${currentIperf3RunningState.basicBandWidthString} (${currentIperf3RunningState.totalOmitted} omitted)"
                                }

                                "sender", "receiver" -> {
                                    lastOmitted = false
                                    currentIperf3RunningState.omitted = false
                                    if (!finished) {
                                        finished = true
                                        summaryResults = true
                                        // Not a new sample — this is the final aggregate row over
                                        // the whole test, already built from the per-interval
                                        // samples counted below. Counting it too made totalSamples
                                        // read one higher than the actual number of 1-second
                                        // intervals (e.g. 11 for --time 10).
                                        currentIperf3RunningState.currentBandWidth = UnitConvertedData(bitRateValue, bitRateString)
                                        currentIperf3RunningState.currentAvg = UnitConvertedData(bitRateValue, bitRateUnitString)
                                    }
                                    timeLabel = sendOrReceive
                                }
                                else -> {
                                    if (lastOmitted) {
                                        // -1, not 0: 0 is also the legitimate index of the
                                        // first real interval ("0.00-1.00"), which always
                                        // follows immediately after the omit period ends. Using
                                        // 0 as the "nothing processed yet" sentinel here made
                                        // that first real interval indistinguishable from "no
                                        // progress since last time", so it always misfired the
                                        // "iperf3 has stalled" check below on every single run.
                                        currentIperf3RunningState.intervalNumber = -1
                                        currentIperf3RunningState.totalSamples = 0
                                    }
                                    lastOmitted = false
                                    val intervalParts = intervalString.split("-")
                                    val expectedIntervalLong = currentIperf3RunningState.intervalNumber + 1
                                    var reportedIntervalLong: Long
                                    if (intervalParts.size == 2) {
                                        // toDoubleOrNull() null (a genuinely unparseable
                                        // interval string) used to silently fall back to 0.0,
                                        // making it indistinguishable from a legitimately
                                        // parsed "0.00" — which always misfired the "missing
                                        // the required interval number" check below on that
                                        // same first-real-interval-after-omit line.
                                        val parsedIntervalStart = intervalParts[0].toDoubleOrNull()
                                        if (parsedIntervalStart == null) {
                                            currentIperf3RunningState.messages.add("Reported output is missing the required interval number: \"$cleanLine\"")
                                            reportedIntervalLong = expectedIntervalLong
                                        } else {
                                            reportedIntervalLong = parsedIntervalStart.roundToLong()
                                            if (!currentIperf3RunningState.omitted) {
                                                if (reportedIntervalLong == currentIperf3RunningState.intervalNumber) {
                                                    currentIperf3RunningState.messages.add("iperf3 has stalled.  Last reported interval is $reportedIntervalLong!")
                                                } else if (reportedIntervalLong != expectedIntervalLong) {
                                                    currentIperf3RunningState.messages.add("Missed interval results: Expected $expectedIntervalLong but received report for $reportedIntervalLong!")
                                                }
                                            }
                                        }
                                        currentIperf3RunningState.intervalNumber = reportedIntervalLong
                                    }
                                    currentIperf3RunningState.stalled = bitRateValue <= 0.0
                                    currentIperf3RunningState.omitted = false
                                    historicalResults.add(fromHumanUnit(currentIperf3RunningState.currentBandWidth.value, currentIperf3RunningState.currentBandWidth.unit))
                                    updateRunningAverage(historicalResults)
                                    updateRunningMedian(historicalResults)
                                    updateMax(bitRateValue, bitRateUnitString)
                                    updateMin(bitRateValue, bitRateUnitString)
                                    updateRunningDeviation(historicalResults)
                                    currentIperf3RunningState.totalSamples++
                                    if (currentIperf3RunningState.stalled) {
                                        timeLabel = "Stalled"
                                        currentIperf3RunningState.consecutiveStalled++
                                        currentIperf3RunningState.totalStalled++
                                        currentIperf3RunningState.basicBandWidthString = "${currentIperf3RunningState.basicBandWidthString} (stalled)"
                                    } else {
                                        currentIperf3RunningState.consecutiveStalled = 0
                                        timeLabel =
                                            if (!isSingleThread) formatString("%2d %s", currentIperf3RunningState.parallelStreams, "streams")
                                            else "Running"
                                    }

                                }
                            }
                            currentIperf3RunningState.formattedOutputLine =
                                formatString("%-12.12s %4.4s %-9.9s %10.10s",
                                    intervalString,
                                    bitRateString.trim(),
                                    bitRateUnitString,
                                    timeLabel)
                            currentIperf3RunningState.debugFormattedOutputLine =
                                formatString("%-12.12s %4.4s %-9.9s %7.7s %7.7s %7.7s %10.10s",
                                        intervalString,
                                        bitRateString.trim(),
                                        bitRateUnitString,
                                        toMbs(currentIperf3RunningState.avgRawBitsPerSec),
                                        toMbs(currentIperf3RunningState.minRawBitsPerSec),
                                        toMbs(currentIperf3RunningState.maxRawBitsPerSec),
                                        timeLabel)




                        }
                    }
                }
            }
        } else if (!cleanLine.startsWith("- -") && cleanLine.trim().isNotEmpty()) {
            currentIperf3RunningState.messages.add(cleanLine)
        }
        return snapshotLocked()
    }


    // -- private helpers --
    private fun updateMax(value: Double, unit: String) {
        val rawValue = fromHumanUnit(value, unit)
        if (rawValue > currentIperf3RunningState.maxRawBitsPerSec) {
            currentIperf3RunningState.maxRawBitsPerSec = truncateDouble(rawValue)
            currentIperf3RunningState.currentMax = toHumanUnit(rawValue)
        }
    }

    private fun updateMin(value: Double, unit: String) {
        val rawValue = fromHumanUnit(value, unit)
        if (rawValue < currentIperf3RunningState.minRawBitsPerSec) {
            currentIperf3RunningState.minRawBitsPerSec = truncateDouble(rawValue)
            currentIperf3RunningState.currentMin = toHumanUnit(rawValue)
        }
    }

    private fun updateRunningAverage(historicalResults: List<Double>) {
        if (historicalResults.isEmpty()) return
        var sum = 0.0
        for (i in historicalResults.indices) {
            sum += historicalResults[i]
        }
        val avg: Double = sum / historicalResults.size.toDouble()
        currentIperf3RunningState.currentAvg = toHumanUnit(avg)
        currentIperf3RunningState.avgRawBitsPerSec = truncateDouble(avg)
    }

    private fun updateRunningMedian(historicalResults: List<Double>) {
        val median: Double
        if (historicalResults.isEmpty()) return
        val sortedResults = historicalResults.sorted()
        val mid = sortedResults.size / 2
        median = if (sortedResults.size % 2 == 0) {
            (sortedResults[mid - 1] + sortedResults[mid]) / 2.0
        } else {
            sortedResults[mid]
        }
        currentIperf3RunningState.currentMedian = toHumanUnit(median)
        currentIperf3RunningState.medianRawBitsPerSec = truncateDouble(median)
    }

    fun updateRunningDeviation(historicalResults: List<Double>) {
        val mean = historicalResults.average()
        val variance = historicalResults.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)
        currentIperf3RunningState.currentStandardDeviation = toHumanUnit(stdDev)
        currentIperf3RunningState.standardDeviationRawBitsPerSec = truncateDouble(stdDev)
    }

    fun getCurrentLineResult(): Iperf3RunningState = withStateLock { snapshotLocked() }
}

fun getHeading(preview: Boolean = false): String {
    return if (!preview) {
        formatString(
            "%-12.12s %4.4s %-9.9s %10.10s",
            "Interval",
            "rate",
            "Unit",
            "comment")
    } else {
        "Interval    rate Unit       comment"
    }
}

fun getDebugHeading(): String =
     formatString("%-12.12s %4.4s %-9.9s %7.7s %7.7s %7.7s %10.10s",
        "Interval",
        "rate",
        "Unit",
        "Avg",
        "Min",
        "Max",
        "comment")



fun getSampleSize(iperf3RunningState: Iperf3RunningState): String {
    var res = ""
    if (iperf3RunningState.totalSamples > 0) {
        res = formatString("Samples:  %6d     [%d omitted]",
            iperf3RunningState.totalSamples,
            iperf3RunningState.totalOmitted)

        if (iperf3RunningState.totalStalled > 0) {
            res += formatString("  {%d stalled}", iperf3RunningState.totalStalled)
        }
    }
    return res
}


fun getMaximum(iperf3RunningState: Iperf3RunningState): String = if (iperf3RunningState.maxRawBitsPerSec > Double.MIN_VALUE)              "Maximum: ${toWholeNumber(iperf3RunningState.currentMax)}" else "N/A"
fun getMinimum(resultDataDataInProgress: Iperf3RunningState): String = if (resultDataDataInProgress.minRawBitsPerSec < Double.MAX_VALUE)  "Minimum: ${toWholeNumber(resultDataDataInProgress.currentMin)}" else "N/A"
fun getAverage(iperf3RunningState: Iperf3RunningState): String = if (iperf3RunningState.avgRawBitsPerSec >= 0)                            "Average: ${toWholeNumber(iperf3RunningState.currentAvg)}" else "N/A"
fun getMedian(iperf3RunningState: Iperf3RunningState): String = if (iperf3RunningState.medianRawBitsPerSec >= 0)                          " Median: ${toWholeNumber(iperf3RunningState.currentMedian)}" else "N/A"
fun getStandardDeviation(iperf3RunningState: Iperf3RunningState): String = if (iperf3RunningState.standardDeviationRawBitsPerSec >= 0)    "Std Dev: ${toWholeNumber(iperf3RunningState.currentStandardDeviation)}" else "N/A"
fun getSource(iperf3RunningState: Iperf3RunningState): String = iperf3RunningState.localHostDetails
fun getDest(iperf3RunningState: Iperf3RunningState): String = iperf3RunningState.remoteHostDetails
fun printLineResult(iperf3RunningState: Iperf3RunningState): String {
    val out = StringBuilder()
    out.append("LineResult\n ")
    iperf3RunningState.messages.indices.forEach {
        val m = iperf3RunningState.messages[it]
        out.append("             messages[$it] = $m\n ")
    }
    out.append("        Local Host: ${iperf3RunningState.localHost}\n")
    out.append("       Remote Host: ${iperf3RunningState.remoteHost}\n")
    out.append("        Local Port: ${iperf3RunningState.localPort}\n")
    out.append("       Remote Port: ${iperf3RunningState.remotePort}\n")
    out.append("       Last Result: ${iperf3RunningState.lastResult}\n")
    out.append("      Result Entry: ${iperf3RunningState.intervalNumber}\n")
    out.append(" Current Bandwidth: ${iperf3RunningState.currentBandWidth}\n")
    out.append("       Current Max: ${iperf3RunningState.currentMax}\n")
    out.append("       Current Min: ${iperf3RunningState.currentMin}\n")
    out.append("       Current Avg: ${iperf3RunningState.currentAvg}\n")
    out.append("   Basic Bandwidth: ${iperf3RunningState.basicBandWidthString}\n")
    out.append("    Formatted Line: ${iperf3RunningState.formattedOutputLine}\n")
    out.append("  Connected String: ${iperf3RunningState.connectedString}\n")
    out.append("           Timeout: ${iperf3RunningState.timeout}\n")
    out.append("  Max Raw Bits/sec: ${iperf3RunningState.maxRawBitsPerSec}\n")
    out.append("  Min Raw Bits/sec: ${iperf3RunningState.minRawBitsPerSec}\n")
    out.append("  Avg Raw Bits/sec: ${iperf3RunningState.avgRawBitsPerSec}\n")
    out.append("  Cur Raw Bits/sec: ${iperf3RunningState.currentRawBitsPerSec}\n")
    return out.toString()
}
