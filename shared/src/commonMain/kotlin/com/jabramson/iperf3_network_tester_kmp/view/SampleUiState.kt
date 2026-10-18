/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.view

import com.jabramson.iperf3_network_tester_kmp.model.Iperf3RunningState
import com.jabramson.iperf3_network_tester_kmp.runner.getSampleOutputData
import com.jabramson.iperf3_network_tester_kmp.utils.UnitConvertedData
import com.jabramson.iperf3_network_tester_kmp.utils.fromHumanUnit
import com.jabramson.iperf3_network_tester_kmp.viewmodel.DefaultInputData
import com.jabramson.iperf3_network_tester_kmp.viewmodel.UiExecutionData
import com.jabramson.iperf3_network_tester_kmp.viewmodel.UiInputData
import com.jabramson.iperf3_network_tester_kmp.viewmodel.UiMicroProgressData

/**
 * Provides sample data and pre-configured instances for testing and demonstration purposes.
 * This class contains mock network test outputs, performance statistics, input configurations,
 * and execution states typically used for UI state simulation or unit testing.
 * 1. Sample output data
 * 2. Sample input data
 * 3. Sample execution states
 * It serves as a static reference dataset for iPerf3 client scenarios and does not implement
 * active state management or business logic.
 *
 * It is also used for the Jetpack Compose previews, ensuring consistent and accurate UI rendering.
 */
class SampleUiState {
    val sampleOutputData = listOf(
        " 0.00-1.00  2.35 Gbits/sec  skipped",
        " 1.00-2.00  2.35 Gbits/sec  skipped",
        " 0.00-1.00  2.35 Gbits/sec  8 streams",
        " 1.00-2.00  2.36 Gbits/sec  8 streams",
        " 2.00-3.00  2.35 Gbits/sec  8 streams",
        " 3.00-4.00  2.35 Gbits/sec  8 streams",
        " 4.00-5.00  2.35 Gbits/sec  8 streams",
        " 5.00-6.00  2.35 Gbits/sec  8 streams",
        " 6.00-7.00  2.35 Gbits/sec  8 streams",
        " 7.00-8.00  2.35 Gbits/sec  8 streams",
        " 8.00-9.00  2.35 Gbits/sec  8 streams",
        " 9.00-10.00 2.35 Gbits/sec  8 streams",
        "10.00-11.00 2.35 Gbits/sec  8 streams",
        "11.00-12.00 2.35 Gbits/sec  8 streams",
        "12.00-13.00 2.35 Gbits/sec  8 streams",
        "13.00-14.00 2.35 Gbits/sec  8 streams",
        "14.00-15.00 2.36 Gbits/sec  8 streams",
        "15.00-16.00 2.35 Gbits/sec  8 streams",
        "16.00-17.00 2.35 Gbits/sec  8 streams",
        "17.00-18.00 2.35 Gbits/sec  8 streams",
        "18.00-19.00 2.35 Gbits/sec  8 streams",
        "19.00-20.00 2.35 Gbits/sec  8 streams",
        "20.00-21.00 2.36 Gbits/sec  8 streams",
        "21.00-22.00 2.35 Gbits/sec  8 streams",
        "22.00-23.00 2.36 Gbits/sec  8 streams",
        "23.00-24.00 2.35 Gbits/sec  8 streams",
        "24.00-25.00 2.35 Gbits/sec  8 streams",
        "25.00-26.00 2.35 Gbits/sec  8 streams",
        "26.00-27.00 2.35 Gbits/sec  8 streams",
        "27.00-28.00 2.35 Gbits/sec  8 streams",
        "28.00-29.00 2.36 Gbits/sec  8 streams",
        "29.00-30.00 2.35 Gbits/sec  8 streams",
        ).toMutableList()

    val sampleOutputDataRunning = listOf(
        " 0.00-1.00  2.35 Gbits/sec  skipped",
        " 1.00-2.00  2.35 Gbits/sec  skipped",
        " 0.00-1.00  2.35 Gbits/sec  8 streams",
        " 1.00-2.00  2.36 Gbits/sec  8 streams",
        " 2.00-3.00  2.35 Gbits/sec  8 streams",
        " 3.00-4.00  2.35 Gbits/sec  8 streams",
        " 4.00-5.00  2.35 Gbits/sec  8 streams",
    ).toMutableList()

    val sampleLocalHostPort  = "10.0.0.2.16:51088"
    val sampleRemoteHostPort = "192.0.2.32:5201"
    val sampleLocalHost = "10.0.0.2.16"
    val sampleRemoteHost = "192.0.2.32"
    val sampleLocalPort: Long = 51088
    val sampleRemotePort: Long = 5201

    val sampleIperf3Messages = listOf(
        "{iPerf JNI} ▶ Initiating iPerf3 client request ...",
        "Connecting to host iperf.example.com, port 5201",
        "Reverse mode, remote host 192.0.2.12 is sending",
        " Local Host:port $sampleLocalHostPort",
        "Remote Host:port $sampleRemoteHostPort",
        "iperf Done.",
        "{iPerf JNI} ✅ Test completed successfully."
    ).toMutableList()

    val sampleIperf3MessagesRunning = listOf(
        "{iPerf JNI} ▶ Initiating iPerf3 client request ...",
        "Connecting to host iperf.example.com, port 5201",
        "Reverse mode, remote host 192.0.2.12 is sending",
        " Local Host:port $sampleLocalHostPort",
        "Remote Host:port $sampleRemoteHostPort"
    ).toMutableList()

    val sampleStatistics = listOf(
        "Samples:       10   [2 Omitted]",
        "Average:      996.00 Mbits/sec",
        "Maximum:        1.09 Gbits/sec",
        "Minimum:      838.00 Mbits/sec",
        " Median:      981.00 Mbits/sec",
        "Std Dev:      364.72 Mbits/sec",
    ).toMutableList()

    val sampleMin: UnitConvertedData = UnitConvertedData(52.30, "Mbits/sec")
    val sampleMax: UnitConvertedData = UnitConvertedData(163.00, "Mbits/sec")
    val sampleAvg: UnitConvertedData = UnitConvertedData(140.00, "Mbits/sec")
    val sampleMedian: UnitConvertedData = UnitConvertedData(153.00, "Mbits/sec")
    val sampleStdDev = UnitConvertedData(33.33, "Mbits/sec")
    val sampleIperf3RunningState = Iperf3RunningState(
        totalSamples = 10,
        totalOmitted = 2,
        microProgress = 8 * 5,
        currentMax = sampleMax,
        currentAvg = sampleAvg,
        currentMin = sampleMin,
        currentMedian = sampleMedian,
        currentStandardDeviation = sampleStdDev,
        maxRawBitsPerSec = fromHumanUnit(sampleMax),
        minRawBitsPerSec = fromHumanUnit(sampleMin),
        medianRawBitsPerSec = fromHumanUnit(sampleMedian),
        avgRawBitsPerSec = fromHumanUnit(sampleAvg),
        standardDeviationRawBitsPerSec = fromHumanUnit(sampleStdDev),
        intervalNumber = 5,
        basicBandWidthString = "191 Mbits/sec",
        localHostDetails = " Local Host:port $sampleLocalHostPort",
        remoteHostDetails = "Remote Host:port $sampleRemoteHostPort",
        localHost = sampleLocalHost,
        localPort = sampleLocalPort,
        remoteHost = sampleRemoteHost,
        remotePort = sampleRemotePort,
        messages = sampleIperf3MessagesRunning,
    )

    val sampleIperf3RunningStateFinished = Iperf3RunningState(
        totalSamples = 10,
        totalOmitted = 2,
        microProgress = 8 * 10,
        currentMax = sampleMax,
        currentAvg = sampleAvg,
        currentMin = sampleMin,
        currentMedian = sampleMedian,
        currentStandardDeviation = sampleStdDev,
        maxRawBitsPerSec = fromHumanUnit(sampleMax),
        minRawBitsPerSec = fromHumanUnit(sampleMin),
        medianRawBitsPerSec = fromHumanUnit(sampleMedian),
        avgRawBitsPerSec = fromHumanUnit(sampleAvg),
        standardDeviationRawBitsPerSec = fromHumanUnit(sampleStdDev),
        intervalNumber = 10,
        basicBandWidthString = "191 Mbits/sec",
        localHostDetails = " Local Host:port $sampleLocalHostPort",
        remoteHostDetails = "Remote Host:port $sampleRemoteHostPort",
        localHost = sampleLocalHost,
        localPort = sampleLocalPort,
        remoteHost = sampleRemoteHost,
        remotePort = sampleRemotePort,
        messages = sampleIperf3Messages,
        isReverse = true
    )


    val sampleErrorLines = listOf("Error: Failed to bind to 192.0.2.1:5201: Address already in use").toMutableList()


    val sampleUiStateExampleRunning = UiExecutionData(
        isRunning = true,
        isFinished = false,
        iperf3Messages = sampleIperf3MessagesRunning,
        bandWidth = "5.00-6.00    35.6 Mbits/sec",
        results = emptyList<String>().toMutableList(),
        outputLines = sampleOutputDataRunning,
        iperf3RunningState = sampleIperf3RunningState,
        //errorLines = sampleErrorLines,
        latestLine = "some stuff",
        returnCode = 0
    )

    val sampleUiMicroProgressData = UiMicroProgressData(
        progress = 0.33.toFloat(),
        elapsedTime = 5.500
    )

    val sampleUiStateExampleFinished = UiExecutionData(
        isRunning = false,
        bandWidth = "1.00-2.00    35.6 Mbits/sec",
        isFinished = true,
        results = sampleStatistics,
        outputLines = sampleOutputData,
        iperf3RunningState = sampleIperf3RunningStateFinished,
        errorLines = sampleErrorLines,
        latestLine = "some stuff",
        returnCode = 0,
        iperf3Messages = sampleIperf3Messages
    )

    val sampleInputData = UiInputData(
        hostName = DefaultInputData.HOST_NAME,
        durationSecs = 10,
        parallelStreams = 8,
        skip = 2,
        portNumber = DefaultInputData.PORT_NUMBER,
        isDebugging = true,
        isReverse = true,
        isForceFlush = false,
    )
}

//fun getSampleUiState() = getSampleUiState(true)
fun getSampleInputData() = SampleUiState().sampleInputData

fun getSampleUiMicroState() = SampleUiState().sampleUiMicroProgressData

/**
 * Returns a sample UI state for testing or preview purposes.
 * The state configuration is determined by the running status.
 *
 * @param isFinished whether the sample state should represent a running or finished execution
 * @return a sample UI state instance
 */
fun getSampleUiState(isFinished: Boolean) =
    if (isFinished) SampleUiState().sampleUiStateExampleFinished
    else SampleUiState().sampleUiStateExampleRunning

