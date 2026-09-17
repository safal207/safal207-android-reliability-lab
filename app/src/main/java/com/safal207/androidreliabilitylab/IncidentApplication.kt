package com.safal207.androidreliabilitylab

import android.app.Application
import com.safal207.androidreliabilitylab.data.HttpIncidentRepository
import com.safal207.androidreliabilitylab.data.IncidentRepository
import com.safal207.androidreliabilitylab.data.PersistentIncidentRepository
import com.safal207.androidreliabilitylab.data.HttpIncidentMutationSource
import com.safal207.androidreliabilitylab.data.PersistentIncidentStatusMutator
import com.safal207.androidreliabilitylab.data.local.IncidentDatabase

class IncidentApplication : Application() {
    private val incidentDatabase by lazy { IncidentDatabase.open(this) }

    val incidentStatusMutator by lazy {
        PersistentIncidentStatusMutator(
            HttpIncidentMutationSource(BuildConfig.INCIDENT_BASE_URL),
            incidentDatabase.pendingMutationDao(),
        )
    }

    val incidentRepository: IncidentRepository by lazy {
        PersistentIncidentRepository(
            HttpIncidentRepository.create(BuildConfig.INCIDENT_BASE_URL),
            incidentDatabase.incidentDao(),
        )
    }
}
