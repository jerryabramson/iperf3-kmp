/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.database

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteDriver
import kotlinx.coroutines.Dispatchers

expect fun createSQLiteDriver(): SQLiteDriver?

/* Each platform caches its own instance (see Database.<platform>.kt) so
 * this is safe to call repeatedly without recreating the database.
 */

expect fun getResultDatabase(): ResultDatabase?

/*
 * Dispatchers.IO isn't public on every Native/coroutines vfersion
 * combination this project's dependency graph can resolve to (Room's own
 * transitive kotlinx-coroutines-core constraint has pulled it below the
 * version where Dispatchers.IO is public for iosArm64/iosSimulatorArm64
 * before) — Dispatchers.Default is guaranteed available on all three
 * targets, so use it here rather than re-fighting that version pin.
 * This is a research/coursework project, not a shipped app with real user
 * data to protect across schema changes — skip hand-written Migration
 * objects and just drop/recreate on every version bump. Revisit if that
 * changes.
 */
fun getRoomDatabase(builder: RoomDatabase.Builder<ResultDatabase>): ResultDatabase? {
    val sql = createSQLiteDriver()
    if (sql != null) {
        val resultDatabase =
            builder
                .setDriver(sql)
                .setQueryCoroutineContext(Dispatchers.Default)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        return resultDatabase
    }
    return null
}