package com.jabramson.iperf3_network_tester_kmp.database
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor


/**
 * Abstract Room database class responsible for persisting iperf3 network test results.
 *
 * Maps the ResultDataEntity table and exposes the ResultDataEntityDao for all
 * data access operations. Initialized with the database name result_data-db and
 * configured to fall back to destructive migration on schema updates. DAO queries
 * are set to run on Dispatchers.Default.
 *
 */
@Database(entities = [ResultDataEntity::class], version = 19)
@ConstructedBy(ResultDatabaseConstructor::class)
abstract class ResultDatabase: RoomDatabase() {
    /**
     * Provides access to the Data Access Object for persisting and
     * querying iperf3 network test results.
     *
     * @return An instance of ResultDataEntityDao for performing database
     * operations on the result data table.
     */
    abstract fun resultDataEntityDao(): ResultDataEntityDao

    companion object {
        const val DBNAME = "result_data-db"
    }
}

/**
 * Multiplatform expectation for constructing the [ResultDatabase] instance.
 *
 * This object is consumed by Room's compiler plugin to generate platform-specific
 * database construction code. Actual implementations on each target platform are
 * responsible for initializing the SQLite driver, configuring the database builder,
 * and returning the fully set up [ResultDatabase].
 *
 * The constructed database is configured to persist iperf3 network test results
 * using the [ResultDataEntity] table, expose the [ResultDataEntityDao] for data access,
 * fall back to destructive migration on schema updates, and route query execution
 * to Dispatchers.Default.
 *
 */
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object ResultDatabaseConstructor : RoomDatabaseConstructor<ResultDatabase> {
    override fun initialize(): ResultDatabase
}
