package com.jabramson.iperf3_network_tester_kmp.runner

actual fun getPlatformTempDir(): String =
    System.getProperty("java.io.tmpdir")
