/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp

/**
 * Central home for app-wide constants, grouped by concern.
 */
object Iperf3KMPConstants {

    /**
     * iperf3's own hard ceiling on parallel streams (third_party/iperf/src/iperf.h:454,
     * `#define MAX_STREAMS 128`). Not importable directly from Kotlin -- there's no
     * cinterop binding exposing iperf.h to commonMain -- so this mirrors the native
     * header's literal value to keep [MAX_PARALLEL_STREAMS] below traceable to its source.
     */
    const val IPERF3_MAX_STREAMS = 128

    /**
     * This app's own ceiling: [IPERF3_MAX_STREAMS] divided by a platform-dependent
     * divisor (see [getParallelStreamsDivisor]) -- a quarter of iperf3's own hard
     * limit (32) on Android/iOS, since mobile hardware/networks can't meaningfully
     * drive that many parallel connections; the full 128 on desktop/JVM, where real
     * CPUs/NICs can. Not `const` since it depends on a platform `expect`/`actual`
     * call, evaluated once when this object is first touched.
     */
    val MAX_PARALLEL_STREAMS = IPERF3_MAX_STREAMS / getParallelStreamsDivisor()

    /**
     * Single source of truth for the small set of iPerf3 Messages lines that are
     * allowed to render their URL as a clickable link. Writers (e.g., Iperf3Executor)
     * emit these exact constants; the renderer (IperfMessagesSection) only linkifies
     * a line that matches one of them, so a message is never clickable unless it was
     * deliberately added here. Add new entries to both the const val and [ALL] to
     * make another message linkable.
     */
    object LinkableMessages {
        const val IPERF3_HOMEPAGE = "iperf3 homepage at: https://software.es.net/iperf/"
        const val PUBLIC_SERVER_LIST = "Public Server List: https://iperf.fr/iperf-servers.php"

        val ALL: List<String> = listOf(IPERF3_HOMEPAGE, PUBLIC_SERVER_LIST)
    }
}
