package com.jabramson.iperf3_network_tester_kmp.database

import androidx.room.Room
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.jabramson.iperf3_network_tester_kmp.AndroidAppContextHolder

actual fun createSQLiteDriver(): SQLiteDriver = AndroidSQLiteDriver()

actual fun getResultDatabase(): ResultDatabase = AndroidResultDatabaseHolder.instance

private object AndroidResultDatabaseHolder {
    val instance: ResultDatabase by lazy {
        val context = AndroidAppContextHolder.requireContext()
        val dbFile = context.getDatabasePath("${ResultDatabase.DBNAME}.db")
        getRoomDatabase(Room.databaseBuilder<ResultDatabase>(context, dbFile.absolutePath))
    }
}
