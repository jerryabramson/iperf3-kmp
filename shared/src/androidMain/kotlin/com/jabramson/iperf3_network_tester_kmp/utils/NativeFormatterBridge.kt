/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.utils

/**
 * JNI bridge to the C `format_string()` in native/native_format.h — the
 * single source of truth for printf-style formatting, shared with iOS
 * (cinterop) and desktop JVM (its own JNI build).
 *
 * The native side binds this via `RegisterNatives()` in JNI_OnLoad, keyed
 * on this exact package/class/method — see native/native_format_jni.c.
 */
internal object NativeFormatterBridge {
    init {
        System.loadLibrary("nativeformat")
    }

    @JvmStatic
    external fun nativeFormat(format: String, args: Array<Any?>): String
}
