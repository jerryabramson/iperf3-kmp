package com.jabramson.iperf3_network_tester_kmp.runner

import com.jabramson.iperf3_network_tester_kmp.AndroidAppContextHolder

actual fun getPlatformTempDir(): String =
    AndroidAppContextHolder.requireContext().cacheDir.absolutePath
