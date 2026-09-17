package com.safal207.androidreliabilitylab.data

import com.safal207.androidreliabilitylab.data.local.PendingMutationDao
import com.safal207.androidreliabilitylab.domain.IncidentStatus
import java.util.UUID

class PersistentIncidentStatusMutator(
    private val remote: HttpIncidentMutationSource,
    private val pendingDao: PendingMutationDao,
    private val newMutationId: () -> String = { UUID.randomUUID().toString() },
) : IncidentStatusMutator {
    override suspend fun changeStatus(incidentId: String, targetStatus: IncidentStatus): MutationResult {
        // Identity exists before any HTTP attempt. The historic mutationId column
        // now stores this same actionId; migration preserves older pending rows.
        val actionId = newMutationId()
        require(actionId.isNotBlank())
        check(pendingDao.find(actionId) == null && pendingDao.readReceipts().none { it.actionId == actionId }) {
            "Action identity is already bound; replay the stored action explicitly"
        }
        val delivery = remote.changeStatus(actionId, incidentId, targetStatus)
        // Preserve the intent even if a subsequent receipt commit fails. A received
        // HTTP rejection never queues or changes local status (Bead 004 contract).
        pendingDao.queue(incidentId, targetStatus.name, actionId)
        return finish(actionId, delivery)
    }

    override suspend fun replay(actionId: String): MutationResult {
        val pending = checkNotNull(pendingDao.find(actionId)) { "Pending action is missing" }
        // Payload comes only from this one stored intent. No new id, queue scan,
        // startup hook, scheduler or implicit HTTP retry is involved.
        val delivery = remote.changeStatus(actionId, pending.incidentId, IncidentStatus.valueOf(pending.targetStatus))
        return finish(actionId, delivery)
    }

    private suspend fun finish(actionId: String, delivery: MutationDelivery): MutationResult = when (delivery) {
        is MutationDelivery.Confirmed -> {
            pendingDao.confirm(delivery.receipt.toEntity())
            MutationResult.ServerConfirmed
        }
        MutationDelivery.TransportFailure -> MutationResult.Pending(actionId)
    }
}
