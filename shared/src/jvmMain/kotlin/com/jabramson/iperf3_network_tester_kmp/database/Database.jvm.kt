package com.jabramson.iperf3_network_tester_kmp.database

import androidx.room.Room
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

actual fun createSQLiteDriver(): SQLiteDriver = BundledSQLiteDriver()

actual fun getResultDatabase(): ResultDatabase = JvmResultDatabaseHolder.instance

private object JvmResultDatabaseHolder {
    val instance: ResultDatabase by lazy {
        val dbDir = File(System.getProperty("user.home"), ".iperf3kmp").apply { mkdirs() }
        val dbFile = File(dbDir, "${ResultDatabase.DBNAME}.db")
        getRoomDatabase(Room.databaseBuilder<ResultDatabase>(dbFile.absolutePath))
    }
}
