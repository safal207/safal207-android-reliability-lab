package com.safal207.androidreliabilitylab.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.safal207.androidreliabilitylab.domain.Incident
import com.safal207.androidreliabilitylab.domain.IncidentStatus

@Entity(tableName = "incidents")
data class IncidentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val status: String,
) {
    fun toDomain(): Incident = Incident(id, title, IncidentStatus.valueOf(status))
}

fun Incident.toEntity(): IncidentEntity = IncidentEntity(id, title, status.name)
