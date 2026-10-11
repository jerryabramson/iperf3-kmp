package com.jabramson.iperf3_network_tester_kmp.runner

interface IperfCallback {
    fun onOutput(line: String)
    fun onError(error: String)
    fun onTime(ms: String)
    fun onComplete()
}