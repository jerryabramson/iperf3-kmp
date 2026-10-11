package com.jabramson.iperf3_network_tester_kmp.runner

/**
 * A writable, app-owned directory for IperfRunner.setTempDir() — real
 * iperf3 needs this for its per-stream mkstemp() buffer file (see
 * IPERF3_NATIVE_INTEGRATION.md), since sandboxed platforms don't allow
 * writing to iperf3's own built-in fallback paths.
 */
expect fun getPlatformTempDir(): String
