package com.jabramson.iperf3_network_tester_kmp.database


import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update


/**
 * DAO interface defining all persistence operations for {@link ResultDataEntity} within the
 * {@code result_data} table.
 *
 * The interface is fully asynchronous: every method is declared `suspend` to accommodate
 * Room's blocking requirements on Android while remaining usable on JVM and native targets.
 *
 * It provides standard CRUD actions (insert, update, delete, deleteAll, deleteById),
 * query capabilities for retrieving a single entity by its UUID, fetching collections
 * filtered by identifier list or date range, counting rows, obtaining all records ordered
 * by start time descending, and limiting results to the most recent N entries.
 *
 * All SQL queries reference columns directly mapped onto the {@code ResultDataEntity}
 * properties; Room handles conversion of the `guid` field (a UUID) to a TEXT type for
 * storage. The design intentionally avoids platform‑specific annotations, ensuring a
 * consistent API across Android, JVM and native implementations.
 */
@Dao
interface ResultDataEntityDao {
    // Room only allows blocking DAO methods on Android; jvm()/iOS targets
    // require suspend — so every method here is suspend for consistency.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(resultDataEntity: ResultDataEntity)

    @Delete
    suspend fun delete(resultDataEntity: ResultDataEntity)

    @Update
    suspend fun update(resultDataEntity: ResultDataEntity)

    @Query("SELECT count(*) FROM result_data")
    suspend fun count(): Int

    // UUID is passed directly; Room converts it to TEXT for SQL
    @Query("SELECT * FROM result_data WHERE guid = :uuid")
    suspend fun getById(uuid: String): ResultDataEntity?

    @Query("SELECT * FROM result_data WHERE guid IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<ResultDataEntity>

    // Return all results ordered by most recent first
    @Query("SELECT * FROM result_data ORDER BY startTimeMillis DESC")
    suspend fun getAll(): List<ResultDataEntity>

    // Delete all stored results
    @Query("DELETE FROM result_data")
    suspend fun deleteAll()

    // Delete a single result by UUID
    @Query("DELETE FROM result_data WHERE guid = :uuid")
    suspend fun deleteById(uuid: String): Int

    // Results within a time range (epoch millis)
    @Query("SELECT * FROM result_data WHERE startTimeMillis BETWEEN :start AND :end ORDER BY startTimeMillis DESC")
    suspend fun getByDateRange(start: Long, end: Long): List<ResultDataEntity>

    // Most recent N results
    @Query("SELECT * FROM result_data ORDER BY startTimeMillis DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<ResultDataEntity>
}
