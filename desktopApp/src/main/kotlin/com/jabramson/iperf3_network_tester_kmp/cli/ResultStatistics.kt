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
    val timeStamp: Date = Date(),
    val average: UnitConvertedData = UnitConvertedData(),
    val minimum: UnitConvertedData = UnitConvertedData(value = Double.MAX_VALUE),
    val maximum: UnitConvertedData = UnitConvertedData(value = Double.MIN_VALUE),
    val median: UnitConvertedData = UnitConvertedData(),
    val standardDeviation: UnitConvertedData = UnitConvertedData(),
    val iperf3ReturnCode: Int = -1
)