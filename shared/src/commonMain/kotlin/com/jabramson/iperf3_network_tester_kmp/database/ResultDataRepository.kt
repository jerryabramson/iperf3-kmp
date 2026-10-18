/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.database


/**
 * Repository providing asynchronous access to {@link ResultDataEntity} data stored via
 * {@link ResultDataEntityDao}. All operations are suspendable, enabling safe usage across
 * Android, JVM and native environments while abstracting Room's blocking semantics.
 *
 * The repository offers CRUD functionality, query capabilities for retrieving single or multiple
 * entities by identifier, date range, counting rows, fetching all records ordered by timestamp,
 * and limiting results to the most recent entries. It ensures a consistent API independent of the
 * underlying persistence mechanism.
 */
class ResultDataRepository(
    private val resultDataEntityDao: ResultDataEntityDao
) {
    // --- Read all / count ---

    suspend fun getAllResults(): List<ResultDataEntity> = resultDataEntityDao.getAll()

    suspend fun getCount(): Int = resultDataEntityDao.count()

    // --- Insert / Update ---

    suspend fun insert(resultDataEntity: ResultDataEntity): Unit {
        resultDataEntityDao.insert(resultDataEntity)
    }

    suspend fun update(resultDataEntity: ResultDataEntity) {
        resultDataEntityDao.update(resultDataEntity)
    }

    // --- Delete ---

    suspend fun delete(resultDataEntity: ResultDataEntity) {
        resultDataEntityDao.delete(resultDataEntity)
    }

    suspend fun deleteById(uuid: String): Int {
        return resultDataEntityDao.deleteById(uuid)
    }

    suspend fun deleteAll() {
        resultDataEntityDao.deleteAll()
    }

    // --- Query by ID ---

    suspend fun getById(uuid: String): ResultDataEntity? {
        return resultDataEntityDao.getById(uuid)
    }

    suspend fun getByIds(ids: List<String>): List<ResultDataEntity> {
        return resultDataEntityDao.getByIds(ids)
    }

    // --- Date range query ---

    suspend fun getByDateRange(start: Long, end: Long): List<ResultDataEntity> {
        return resultDataEntityDao.getByDateRange(start, end)
    }

    // --- Recent results (suspend for one-shot queries) ---

    suspend fun getRecent(limit: Int = 20): List<ResultDataEntity> {
        return resultDataEntityDao.getRecent(limit)
    }
}
