/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.utils

import com.jabramson.iperf3_network_tester_kmp.iperf3bridge.IperfCallbacks
import com.jabramson.iperf3_network_tester_kmp.iperf3bridge.iperf3_bridge_force_stop
import com.jabramson.iperf3_network_tester_kmp.iperf3bridge.iperf3_bridge_set_debug
import com.jabramson.iperf3_network_tester_kmp.iperf3bridge.iperf3_bridge_native_iperf3

import com.jabramson.iperf3_network_tester_kmp.iperf3bridge.iperf3_bridge_set_stall_timeout_seconds
import com.jabramson.iperf3_network_tester_kmp.iperf3bridge.iperf3_bridge_set_temp_dir
import com.jabramson.iperf3_network_tester_kmp.iperf3bridge.initOrigStderr
import com.jabramson.iperf3_network_tester_kmp.runner.IperfCallback
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toCValues
import kotlinx.cinterop.toKString

/**
 * Cinterop bridge to real libiperf 3.21 via native/iperf3_bridge.c/.h — the
 * platform-agnostic core Android/desktop's JNI adapter (native/iperf3_jni.c)
 * also calls into, see IPERF3_NATIVE_INTEGRATION.md §8.4. No JNI involved
 * here at all: iperf3_bridge.c's threads call straight back into Kotlin
 * through the function pointers below, which Kotlin/Native's cinterop
 * supports from any thread without an explicit attach/detach step (unlike
 * JNI) — that's why on_thread_attach/on_thread_detach are left null.
 *
 * `ctx` carries a `StableRef<IperfCallback>` across the C boundary as an
 * opaque pointer — the standard Kotlin/Native way to pass a Kotlin object
 * reference through a `void *`.
 */
@OptIn(ExperimentalForeignApi::class)
internal object NativeIperfBridge {

    init {
        // Runs exactly once, the first time this object is touched - which
        // is before any of its methods below can possibly run, since
        // Kotlin object init{} blocks complete before the object is
        // considered constructed. That makes this the one deliberate call
        // site for iOS (Android/desktop's equivalent is JNI_OnLoad in
        // iperf3_jni.c) - see initOrigStderr()'s own doc comment
        // (native/iperf3_bridge.c) for why the ordering matters.
        initOrigStderr()
    }

    private val onOutputCallback =
        staticCFunction<COpaquePointer?, CPointer<ByteVar>?, Unit> { ctx, line ->
            ctx?.asStableRef<IperfCallback>()?.get()?.onOutput(line?.toKString().orEmpty())
        }

    private val onTimeCallback =
        staticCFunction<COpaquePointer?, CPointer<ByteVar>?, Unit> { ctx, ms ->
            ctx?.asStableRef<IperfCallback>()?.get()?.onTime(ms?.toKString().orEmpty())
        }
    private val onErrorCallback =
        staticCFunction<COpaquePointer?, CPointer<ByteVar>?, Unit> { ctx, line ->
            ctx?.asStableRef<IperfCallback>()?.get()?.onError(line?.toKString().orEmpty())
        }

    private val onCompleteCallback =
        staticCFunction<COpaquePointer?, Unit> { ctx ->
            ctx?.asStableRef<IperfCallback>()?.get()?.onComplete()
        }

    fun nativeSetDebug(debug: Int) {
        iperf3_bridge_set_debug(debug)
    }

    fun nativeSetTempDir(tempDir: String) {
        iperf3_bridge_set_temp_dir(tempDir)
    }

    fun nativeSetStallTimeoutSeconds(seconds: Int) {
        iperf3_bridge_set_stall_timeout_seconds(seconds)
    }

    fun nativeForceStop(callback: IperfCallback) {
        val callbackRef = StableRef.create(callback)
        try {
            memScoped {
                val callbacks = alloc<IperfCallbacks>()
                callbacks.ctx = callbackRef.asCPointer()
                callbacks.on_thread_attach = null
                callbacks.on_thread_detach = null
                callbacks.on_output = onOutputCallback
                callbacks.on_error = null
                callbacks.on_complete = null
                iperf3_bridge_force_stop(callbacks.ptr)
            }
        } finally {
            callbackRef.dispose()
        }
    }

    fun nativeIperf3(arguments: Array<String>, callback: IperfCallback): Int {
        val callbackRef = StableRef.create(callback)
        return try {
            memScoped {
                val callbacks = alloc<IperfCallbacks>()
                callbacks.ctx = callbackRef.asCPointer()
                // No thread-attach/detach needed on this platform — see the
                // class-level doc comment.
                callbacks.on_thread_attach = null
                callbacks.on_thread_detach = null
                callbacks.on_output = onOutputCallback
                callbacks.on_time = onTimeCallback
                callbacks.on_error = onErrorCallback
                callbacks.on_complete = onCompleteCallback

                val argv = arguments.map { it.cstr.ptr }.toCValues()
                iperf3_bridge_native_iperf3(argv, arguments.size, callbacks.ptr)
            }
        } finally {
            callbackRef.dispose()
        }
    }
}
