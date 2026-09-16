package com.safal207.androidreliabilitylab

import android.app.Application
import com.safal207.androidreliabilitylab.data.HttpIncidentRepository
import com.safal207.androidreliabilitylab.data.IncidentRepository

class IncidentApplication : Application() {
    val incidentRepository: IncidentRepository by lazy {
        HttpIncidentRepository.create(BuildConfig.INCIDENT_BASE_URL)
    }
}
