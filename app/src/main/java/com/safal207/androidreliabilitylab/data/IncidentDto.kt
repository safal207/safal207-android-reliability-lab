package com.safal207.androidreliabilitylab.data

import com.safal207.androidreliabilitylab.domain.Incident
import com.safal207.androidreliabilitylab.domain.IncidentStatus

// Nullable transport fields are validated before entering the domain model.
internal data class IncidentDto(
    val id: String?,
    val title: String?,
    val status: String?,
) {
    fun toDomain(): Incident = Incident(
        id = requireNotNull(id).also { require(it.isNotBlank()) },
        title = requireNotNull(title).also { require(it.isNotBlank()) },
        status = IncidentStatus.valueOf(requireNotNull(status)),
    )
}
