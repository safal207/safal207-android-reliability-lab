package com.safal207.androidreliabilitylab.data

import com.safal207.androidreliabilitylab.domain.IncidentStatus

sealed interface MutationResult {
    data object ServerConfirmed : MutationResult
    data class Pending(val mutationId: String) : MutationResult
}

interface IncidentStatusMutator {
    suspend fun changeStatus(incidentId: String, targetStatus: IncidentStatus): MutationResult
    suspend fun replay(actionId: String): MutationResult
}
