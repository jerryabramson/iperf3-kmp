/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.cli

import com.jabramson.iperf3_network_tester_kmp.utils.UnitConvertedData
import java.util.Date

data class ResultStatistics(
    val invalidValue: Double = (-1).toDouble(),
    val startTimeStamp: Date = Date(),
    val endTimeStamp: Date,
    val average: UnitConvertedData = UnitConvertedData(invalidValue),
    val minimum: UnitConvertedData = UnitConvertedData(invalidValue),
    val maximum: UnitConvertedData = UnitConvertedData(invalidValue),
    val median: UnitConvertedData = UnitConvertedData(invalidValue),
    val standardDeviation: UnitConvertedData = UnitConvertedData(invalidValue),
    val iperf3ReturnCode: Int = -1
)
