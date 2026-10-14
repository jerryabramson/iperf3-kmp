/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.runner

import com.jabramson.iperf3_network_tester_kmp.AndroidAppContextHolder

actual fun getPlatformTempDir(): String =
    AndroidAppContextHolder.requireContext().cacheDir.absolutePath
