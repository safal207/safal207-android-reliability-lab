package com.safal207.androidreliabilitylab.data

import com.safal207.androidreliabilitylab.domain.Incident

interface IncidentRepository {
    suspend fun getIncidents(): List<Incident>
}
