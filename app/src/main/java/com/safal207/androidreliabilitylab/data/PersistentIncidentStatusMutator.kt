package com.safal207.androidreliabilitylab.data

import com.safal207.androidreliabilitylab.data.local.PendingMutationDao
import com.safal207.androidreliabilitylab.domain.IncidentStatus
import java.util.UUID

class PersistentIncidentStatusMutator(
    private val remote: HttpIncidentMutationSource,
    private val pendingDao: PendingMutationDao,
    private val newMutationId: () -> String = { UUID.randomUUID().toString() },
) : IncidentStatusMutator {
    override suspend fun changeStatus(incidentId: String, targetStatus: IncidentStatus): MutationResult =
        when (remote.changeStatus(incidentId, targetStatus)) {
            MutationDelivery.CONFIRMED -> {
                check(pendingDao.updateIncidentStatus(incidentId, targetStatus.name) == 1) {
                    "Incident is not loaded"
                }
                MutationResult.ServerConfirmed
            }
            MutationDelivery.TRANSPORT_FAILURE -> {
                val mutationId = newMutationId()
                pendingDao.queue(incidentId, targetStatus.name, mutationId)
                // The id stays local. There is no replay path or server idempotency key.
                MutationResult.Pending(mutationId)
            }
        }
}
