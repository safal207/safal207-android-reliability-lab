package com.safal207.androidreliabilitylab.data

import com.safal207.androidreliabilitylab.data.local.IncidentDao
import com.safal207.androidreliabilitylab.data.local.toEntity
import com.safal207.androidreliabilitylab.domain.Incident

class PersistentIncidentRepository(
    private val remote: IncidentRepository,
    private val dao: IncidentDao,
) : IncidentRepository {
    override suspend fun getIncidents(): List<Incident> {
        val incidents = remote.getIncidents()
        dao.replaceAll(incidents.map(Incident::toEntity))
        // Content may be published only after the transaction commits.
        // HTTP or write failures propagate; this bead never reads a cached fallback.
        return incidents
    }
}
