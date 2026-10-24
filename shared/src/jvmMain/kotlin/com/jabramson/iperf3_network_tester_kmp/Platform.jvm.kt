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
    private val os: String = System.getProperty("os.name") ?: "Unknown"
    private val arch: String = System.getProperty("os.arch") ?: ""

    /**
     * Windows on ARM64 (aarch64 JVM). androidx.sqlite's BundledSQLiteDriver
     * ships no windows_arm64 native (2.7.0 has linux_x64/arm64, osx_arm64,
     * windows_x64 only), so Room is disabled there.
     */
    val isWindowsOnArm: Boolean = os.startsWith("Windows") && arch == "aarch64"

    private val osName: String = if (isWindowsOnArm) "WindowsOnArm" else os

    override val isDesktop: Boolean = true
    override val name: String = "Desktop: Operating System='$osName'; Java Version: ${System.getProperty("java.version")}"
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

// System properties don't change at runtime, so build this once rather than
// on every call (getPlatform() is called from composables on recomposition).
internal val jvmPlatform = JVMPlatform()

actual fun getPlatform(): Platform = jvmPlatform

actual fun getAppUUID(): AppUUID = JVMUUID()
actual fun getAppLocalDateTime(): AppLocalDateTime = JVMLocalDateTime()
actual fun getNotch(): Notch = JVMNotch()
actual fun getAppCurrentTimeMillis(): AppCurrentTimeMillis = JVMCurrentTimeMillis()
actual fun getParallelStreamsDivisor(): Int = 1
