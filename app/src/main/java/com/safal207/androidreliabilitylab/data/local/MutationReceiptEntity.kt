package com.safal207.androidreliabilitylab.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mutation_receipts")
data class MutationReceiptEntity(
    @PrimaryKey val actionId: String,
    val incidentId: String,
    val targetStatus: String,
    val effectId: String,
    val receiptVersion: Int,
    val effectSequence: Long,
)
