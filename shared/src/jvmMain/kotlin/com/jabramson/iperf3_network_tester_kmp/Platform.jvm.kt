/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp

import java.util.UUID
import java.time.LocalDateTime

class JVMPlatform : Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
}

class JVMUUID : AppUUID {
    override val name: String = UUID.randomUUID().toString()
}
class JVMLocalDateTime: AppLocalDateTime {
    override val name: String = LocalDateTime.now().toString()
}

class JVMNotch: Notch {
    override val hasNotch: Boolean = false
}

class JVMCurrentTimeMillis : AppCurrentTimeMillis {
    override val value: Long = System.currentTimeMillis()
}

actual fun getPlatform(): Platform = JVMPlatform()
actual fun getAppUUID(): AppUUID = JVMUUID()
actual fun getAppLocalDateTime(): AppLocalDateTime = JVMLocalDateTime()
actual fun getNotch(): Notch = JVMNotch()
actual fun getAppCurrentTimeMillis(): AppCurrentTimeMillis = JVMCurrentTimeMillis()
actual fun getParallelStreamsDivisor(): Int = 1
