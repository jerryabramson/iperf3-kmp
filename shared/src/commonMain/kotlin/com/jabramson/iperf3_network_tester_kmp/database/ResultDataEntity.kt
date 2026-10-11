package com.jabramson.iperf3_network_tester_kmp.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Data entity representing a single iperf3 network test result stored in the
 * {@code result_data} table.
 *
 * The entity contains a unique identifier, start and end timestamps, human‑readable date times,
 * aggregated bandwidth statistics, input configuration details such as hostnames and ports,
 * direction of the test (upload or download), duration, parallel stream count, and skip settings.
 *
 * NOTE This data class is purposely minimal, as we can reconstruct the more human-friendly
 *      values for all of the rates using the existing UnitConverter utility functions.
 *
  */
@Entity(tableName="result_data")
data class ResultDataEntity(
    @PrimaryKey
    val guid: String,

    // stats
    val elapsedTime: String,
    val rawTime: Double,

    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val startDateTime: String,
    val endDateTime: String,
    val reportedIterations: Long,
    val maxbps: Long,
    val minbps: Long,
    val avgbps: Long,
    val medianbps: Long,
    val stdDevbps: Long,
    val ipv6: Boolean = false,

    // Input Options for test
    val localHost: String,
    val localPort: Long,
    val destinationHost: String,
    val destinationPort: Long,


    val direction: String,
    val duration: Int,
    val parallelStreams: Int,
    val skip: Int,
    val stalled: Int
)
