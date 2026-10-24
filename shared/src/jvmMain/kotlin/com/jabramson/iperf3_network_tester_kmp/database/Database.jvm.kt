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
import com.jabramson.iperf3_network_tester_kmp.getPlatform
import java.io.File

actual fun createSQLiteDriver(): SQLiteDriver? {
    if (getPlatform().name.contains("Windows", ignoreCase = true)) {
        return null
    }
    return BundledSQLiteDriver()
}

actual fun getResultDatabase(): ResultDatabase? = JvmResultDatabaseHolder.instance

private object JvmResultDatabaseHolder {
    val instance: ResultDatabase? by lazy {
        val dbDir = File(System.getProperty("user.home"), ".iperf3kmp").apply { mkdirs() }
        val dbFile = File(dbDir, "${ResultDatabase.DBNAME}.db")
        val osName = System.getProperty("os.name")
        if (!osName.startsWith("WindowsOnArm", ignoreCase = true)) {
            val resultDatabase = getRoomDatabase(Room.databaseBuilder<ResultDatabase>(dbFile.absolutePath))
            return@lazy resultDatabase
        } else {
            println("Room Database not supported on Windows on Arm!")
        }
        return@lazy null
    }
}
