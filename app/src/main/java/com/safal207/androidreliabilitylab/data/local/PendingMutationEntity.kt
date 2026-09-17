package com.safal207.androidreliabilitylab.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_mutations")
data class PendingMutationEntity(
    @PrimaryKey val mutationId: String,
    val incidentId: String,
    val targetStatus: String,
    val createdOrder: Long,
)
