/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp

import android.os.Build


class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

class AndroidUUID : AppUUID {
    override val name: String = java.util.UUID.randomUUID().toString()
}

class AndroidAppLocalDateTime: AppLocalDateTime {
    override val name: String = java.time.LocalDateTime.now().toString()
}

class AndroidNotch: Notch {
    override val hasNotch: Boolean = false
}

actual fun getNotch(): Notch = AndroidNotch()

actual fun getPlatform(): Platform = AndroidPlatform()

actual fun getAppUUID(): AppUUID = AndroidUUID()

actual fun getAppLocalDateTime(): AppLocalDateTime =  AndroidAppLocalDateTime()

class AndroidCurrentTimeMillis : AppCurrentTimeMillis {
    override val value: Long = System.currentTimeMillis()
}

actual fun getAppCurrentTimeMillis(): AppCurrentTimeMillis = AndroidCurrentTimeMillis()

actual fun getParallelStreamsDivisor(): Int = 4

