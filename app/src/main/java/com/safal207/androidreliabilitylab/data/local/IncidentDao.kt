package com.safal207.androidreliabilitylab.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class IncidentDao {
    @Query("SELECT * FROM incidents ORDER BY id")
    abstract suspend fun readAll(): List<IncidentEntity>

    @Query("DELETE FROM incidents")
    protected abstract suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertAll(incidents: List<IncidentEntity>)

    // A complete snapshot replaces the old one, including deletion on an empty response.
    // Duplicate ids reject the write and roll back the deletion; partial data never wins.
    @Transaction
    open suspend fun replaceAll(incidents: List<IncidentEntity>) {
        deleteAll()
        insertAll(incidents)
    }
}
