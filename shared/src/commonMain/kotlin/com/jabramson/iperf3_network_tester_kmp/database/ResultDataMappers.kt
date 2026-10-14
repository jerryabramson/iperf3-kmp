/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.database

import com.jabramson.iperf3_network_tester_kmp.getAppCurrentTimeMillis
import com.jabramson.iperf3_network_tester_kmp.getAppLocalDateTime
import com.jabramson.iperf3_network_tester_kmp.model.Iperf3RunningState
import com.jabramson.iperf3_network_tester_kmp.utils.formatTime

import com.jabramson.iperf3_network_tester_kmp.utils.tobps


/**
 * Converts an [Iperf3RunningState] into a [ResultDataEntity].
 *
 * @param iperf3RunningState the running state containing test metadata and statistics
 * @return a [ResultDataEntity] populated with extracted values*/
fun toEntity(iperf3RunningState: Iperf3RunningState, elapsedTime: Double, preview: Boolean = false): ResultDataEntity =
    ResultDataEntity(
        // key
            guid = iperf3RunningState.guid.name,

            // timestamps
            startTimeMillis = iperf3RunningState.startTimeMillis,
            endTimeMillis = getAppCurrentTimeMillis().value,
            startDateTime = iperf3RunningState.startDateTime,
            endDateTime = getAppLocalDateTime().name,
            reportedIterations = iperf3RunningState.totalOmitted + iperf3RunningState.totalSamples,
            rawTime = elapsedTime,
            elapsedTime = formatTime(elapsedTime, preview),

            // Input Params
            direction = if (iperf3RunningState.isReverse) "Download" else "Upload",
            duration =  iperf3RunningState.duration,
            parallelStreams = iperf3RunningState.parallelStreams,
            skip =  iperf3RunningState.skip,
            localHost = iperf3RunningState.localHost,
            destinationHost = iperf3RunningState.remoteHost,
            localPort = iperf3RunningState.localPort,
            destinationPort = iperf3RunningState.remotePort,

            // raw bps results
            maxbps = tobps(iperf3RunningState.maxRawBitsPerSec),
            minbps = tobps(iperf3RunningState.minRawBitsPerSec),
            avgbps = tobps(iperf3RunningState.avgRawBitsPerSec),
            stdDevbps = tobps(iperf3RunningState.standardDeviationRawBitsPerSec),
            medianbps = tobps(iperf3RunningState.medianRawBitsPerSec),
            stalled = iperf3RunningState.totalStalled,

            ipv6 = (iperf3RunningState.localHost.contains(":"))
    )

/**
 * Will eventually be used to reconstruct the more complex Iperf3RunningState()
 * data class from the simplified ResultDataEntity()
 */
fun toDomain(e: ResultDataEntity): Iperf3RunningState =
        Iperf3RunningState()

    

