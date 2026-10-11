package com.jabramson.iperf3_network_tester_kmp.model

import com.jabramson.iperf3_network_tester_kmp.AppLocalDateTime
import com.jabramson.iperf3_network_tester_kmp.AppUUID
import com.jabramson.iperf3_network_tester_kmp.getAppCurrentTimeMillis
import com.jabramson.iperf3_network_tester_kmp.getAppLocalDateTime
import com.jabramson.iperf3_network_tester_kmp.getAppUUID
import com.jabramson.iperf3_network_tester_kmp.utils.UnitConvertedData

/**
 * Data holder for the current state of an iperf3 test execution.
 *
 * It stores identifiers, timing information, configuration parameters,
 * host and port details, collected messages, interval counters,
 * human‑readable and raw statistics, and various status flags used by the UI
 * and the underlying iperf3 process.
 *
 * This is the equivalent data class, but more detailed, as the ResultDataEntity() data
 * class
 */
data class Iperf3RunningState(

    // db keys
    val guid: AppUUID = getAppUUID(),

    val startTimeMillis: Long = getAppCurrentTimeMillis().value,

    // test input details
    val isReverse: Boolean = false,
    val duration: Int = -1,
    val parallelStreams: Int = -1,
    val skip: Int = -1,

    // initial test data
    val startDateTime: String = getAppLocalDateTime().name,
    var localHost: String = "",
    var remoteHost: String = "",
    var localPort: Long = -1L,
    var remotePort: Long = -1L,
    var localHostDetails: String = "",
    var rawLocalHostDetails: String = "",
    var remoteHostDetails: String = "",
    var rawRemoteHostDetails: String = "",

    // iperf3 messages
    var messages: MutableList<String> = emptyList<String>().toMutableList(),

    // interval number (0 to duration, can go backwards with Omit)
    var intervalNumber: Long = -1,
    var totalSamples: Long = -1,
    var totalOmitted: Long = 0,

    // "micro-steps"
    var microProgress: Long = 0,


    // statistics - converted to human-readable units
    var currentBandWidth : UnitConvertedData = UnitConvertedData(),
    var currentMax: UnitConvertedData = UnitConvertedData(),
    var currentMin: UnitConvertedData = UnitConvertedData(),
    var currentAvg: UnitConvertedData = UnitConvertedData(),
    var currentMedian: UnitConvertedData = UnitConvertedData(),
    var currentStandardDeviation: UnitConvertedData = UnitConvertedData(),

    // processed output from iperf3
    var basicBandWidthString: String = "",
    var debugFormattedOutputLine: String = "",
    var formattedOutputLine: String = "",
    var connectedString: String = "",
    var timeout: String = "",
    var rawOutputLine: String = "",
    var omitted: Boolean = false,
    var stalled: Boolean = false,
    var consecutiveStalled: Int = 0,
    var totalStalled: Int = 0,


    // statistics - raw numeric values
    var maxRawBitsPerSec: Double = Double.MIN_VALUE,
    var minRawBitsPerSec: Double = Double.MAX_VALUE,
    var avgRawBitsPerSec: Double = 0.toDouble(),
    var medianRawBitsPerSec: Double = 0.toDouble(),
    var currentRawBitsPerSec: Double = 0.toDouble(),
    var standardDeviationRawBitsPerSec: Double = 0.toDouble(),
    var elapsedTime: String = "0",

    var lastResult: String = "",
)

