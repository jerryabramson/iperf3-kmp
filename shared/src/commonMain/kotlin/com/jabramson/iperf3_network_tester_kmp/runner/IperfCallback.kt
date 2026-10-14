/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.runner

interface IperfCallback {
    fun onOutput(line: String)
    fun onError(error: String)
    fun onTime(ms: String)
    fun onComplete()
}