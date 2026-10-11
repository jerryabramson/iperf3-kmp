package com.jabramson.iperf3_network_tester_kmp.database

import androidx.room.Room
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.NativeSQLiteDriver
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

actual fun createSQLiteDriver(): SQLiteDriver = NativeSQLiteDriver()

actual fun getResultDatabase(): ResultDatabase = IosResultDatabaseHolder.instance

private object IosResultDatabaseHolder {
    val instance: ResultDatabase by lazy {
        val documentDirectory = NSSearchPathForDirectoriesInDomains(
            directory = NSDocumentDirectory,
            domainMask = NSUserDomainMask,
            expandTilde = true,
        ).first() as String
        val dbFilePath = "$documentDirectory/${ResultDatabase.DBNAME}.db"
        getRoomDatabase(Room.databaseBuilder<ResultDatabase>(name = dbFilePath))
    }
}
