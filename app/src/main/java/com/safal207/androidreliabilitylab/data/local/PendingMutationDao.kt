package com.safal207.androidreliabilitylab.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class PendingMutationDao {
    @Query("SELECT * FROM pending_mutations ORDER BY createdOrder, mutationId")
    abstract suspend fun readAll(): List<PendingMutationEntity>

    @Query("UPDATE incidents SET status = :status WHERE id = :incidentId")
    abstract suspend fun updateIncidentStatus(incidentId: String, status: String): Int

    @Query("SELECT COALESCE(MAX(createdOrder), 0) + 1 FROM pending_mutations")
    protected abstract suspend fun nextOrder(): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insert(pending: PendingMutationEntity)

    @Transaction
    open suspend fun queue(incidentId: String, targetStatus: String, mutationId: String) {
        check(updateIncidentStatus(incidentId, targetStatus) == 1) { "Incident is not loaded" }
        // Ordering is allocated in the same transaction, without relying on a clock.
        insert(PendingMutationEntity(mutationId, incidentId, targetStatus, nextOrder()))
    }
}
