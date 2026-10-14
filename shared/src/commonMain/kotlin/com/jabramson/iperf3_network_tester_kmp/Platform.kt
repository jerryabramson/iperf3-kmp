/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp


/*
 * *************************************************************************************
 * * All platform-dependent APIs are captured in this thin set of interfaces/functions.*
 * *************************************************************************************
 */

/**
 * encapsulates the platform name/details
 */
interface Platform {
    val name: String
}

/**
 * Represents a platform abstraction for determining whether a device has a notch or display cutout.
 *
 * This interface is intended to be implemented by platform-specific classes to provide
 * the appropriate logic for detecting the presence of a notch on different devices (e.g., Android, iOS, JVM).
 *
 * The `hasNotch` property is used to indicate whether the device has a notch.
 */
interface Notch {
    val hasNotch: Boolean
}

/**
 * encapsulates a platform-dependent generated unique UUID.
 *   * This would seem to be a very simple platform-independent Kotlin API, but there is none
 *     available.
 */
interface AppUUID {
    val name: String
}

/**
 * Returns the date/time in a consistent formatted output string:
 *
 * YYYY-MM-DDTHH:MM:SS.ssssss
 *
 * xxxx-xx-xxTxx:xx:xx:.xxxxx
 * 2026-07-27T17:31:01.738256
 */
interface AppLocalDateTime {
    val name: String
}

/**
 * encapsulates a platform-dependent generated time since 1970
 *  * Again this would seem to be a very simple platform-independent Kotlin API,
 *    but there is none available.
 */
interface AppCurrentTimeMillis {
    val value: Long
}

/**
 * Use `Platform.name` for value (not `.toString()`)
 * @return Platform.
 */
expect fun getPlatform(): Platform

/**
 * Retrieves a platform-specific instance of the `Notch` interface.
 * This function provides an abstraction to determine whether the device has a notch
 * or display cutout, based on the platform's implementation.
 *
 * @return A `Notch` implementation representing the platform-specific logic for detecting a notch.
 */
expect fun getNotch(): Notch

/**
 * Use `AppUUID.name` for value (not `.toString()`)
 * @return AppUUID
 */
expect fun getAppUUID(): AppUUID

/**
 * Use `AppLocalDateTime.name` for value (not `.toString()`)
 * @return AppLocalDateTime
 */
expect fun getAppLocalDateTime(): AppLocalDateTime

/**
 * Use `AppCurrentTimeMillis.name` for value (not `platform.toString()`)
 * @return AppCurrentTimeMillis
 */
expect fun getAppCurrentTimeMillis(): AppCurrentTimeMillis

/**
 * Divisor applied to iperf3's own hard parallel-streams ceiling
 * ([Iperf3KMPConstants.IPERF3_MAX_STREAMS]) to get this app's actual limit
 * ([Iperf3KMPConstants.MAX_PARALLEL_STREAMS]) -- 4 on Android/iOS (mobile
 * hardware/networks can't meaningfully drive iperf3's own full ceiling), 1 on
 * desktop/JVM (real desktop CPUs/NICs can).
 */
expect fun getParallelStreamsDivisor(): Int