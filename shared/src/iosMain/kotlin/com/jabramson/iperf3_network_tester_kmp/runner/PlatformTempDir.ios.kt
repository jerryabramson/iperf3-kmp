package com.jabramson.iperf3_network_tester_kmp.runner

import platform.Foundation.NSTemporaryDirectory

actual fun getPlatformTempDir(): String = NSTemporaryDirectory()
