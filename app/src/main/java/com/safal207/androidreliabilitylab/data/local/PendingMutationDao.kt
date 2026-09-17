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

    @Query("SELECT * FROM pending_mutations WHERE mutationId = :actionId")
    abstract suspend fun find(actionId: String): PendingMutationEntity?

    @Query("SELECT * FROM mutation_receipts ORDER BY actionId")
    abstract suspend fun readReceipts(): List<MutationReceiptEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertReceipt(receipt: MutationReceiptEntity)

    @Query("DELETE FROM pending_mutations WHERE mutationId = :actionId")
    protected abstract suspend fun deletePending(actionId: String): Int

    @Transaction
    open suspend fun confirm(receipt: MutationReceiptEntity) {
        val pending = checkNotNull(find(receipt.actionId)) { "Pending action is missing" }
        check(pending.incidentId == receipt.incidentId && pending.targetStatus == receipt.targetStatus) {
            "Receipt payload differs from pending action"
        }
        check(updateIncidentStatus(receipt.incidentId, receipt.targetStatus) == 1) { "Incident is not loaded" }
        insertReceipt(receipt)
        check(deletePending(receipt.actionId) == 1) { "Pending action was not removed" }
    }

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
