/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.database

import androidx.room.Room
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.jabramson.iperf3_network_tester_kmp.jvmPlatform
import com.jabramson.iperf3_network_tester_kmp.utils.Log
import java.io.File

/**
 * Null on Windows on Arm -- see [com.jabramson.iperf3_network_tester_kmp.JVMPlatform.isWindowsOnArm].
 */
actual fun createSQLiteDriver(): SQLiteDriver? =
    if (jvmPlatform.isWindowsOnArm) null else BundledSQLiteDriver()

actual fun getResultDatabase(): ResultDatabase? = JvmResultDatabaseHolder.instance

private object JvmResultDatabaseHolder {
    const val TAG = "JvmResultDatabaseHolder"
    val instance: ResultDatabase? by lazy {
        if (jvmPlatform.isWindowsOnArm) {
            Log.e(TAG, "Room Database not supported on Windows on Arm!")
            return@lazy null
        }
        val dbDir = File(System.getProperty("user.home"), ".iperf3kmp").apply { mkdirs() }
        val dbFile = File(dbDir, "${ResultDatabase.DBNAME}.db")
        getRoomDatabase(Room.databaseBuilder<ResultDatabase>(dbFile.absolutePath))
    }
}
