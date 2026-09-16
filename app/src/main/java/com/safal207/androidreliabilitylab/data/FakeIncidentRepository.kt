package com.safal207.androidreliabilitylab.data

import com.safal207.androidreliabilitylab.domain.Incident
import com.safal207.androidreliabilitylab.domain.IncidentStatus

class FakeIncidentRepository : IncidentRepository {
    override suspend fun getIncidents(): List<Incident> = listOf(
        Incident(
            id = "INC-001",
            title = "Payment retry",
            status = IncidentStatus.OPEN,
        ),
        Incident(
            id = "INC-002",
            title = "Duplicate request",
            status = IncidentStatus.INVESTIGATING,
        ),
        Incident(
            id = "INC-003",
            title = "Offline sync",
            status = IncidentStatus.RESOLVED,
        ),
    )
}
