package com.safal207.androidreliabilitylab.domain

data class Incident(
    val id: String,
    val title: String,
    val status: IncidentStatus,
)

enum class IncidentStatus {
    OPEN,
    INVESTIGATING,
    RESOLVED,
}
