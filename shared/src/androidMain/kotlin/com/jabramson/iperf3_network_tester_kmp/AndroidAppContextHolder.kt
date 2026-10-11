package com.jabramson.iperf3_network_tester_kmp

import android.content.Context

/**
 * Stashed once from MainActivity.onCreate() before setContent { App() }
 * runs. There's no DI framework in this app, so Room's Android driver
 * needs somewhere to get a Context from without threading one through
 * App()/the ViewModel constructor on all three platforms.
 */
object AndroidAppContextHolder {
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun requireContext(): Context =
        appContext ?: error(
            "AndroidAppContextHolder not initialized — call AndroidAppContextHolder.init(context) " +
                "from MainActivity.onCreate() before touching the database."
        )
}
