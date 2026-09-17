package com.safal207.androidreliabilitylab.data

import com.safal207.androidreliabilitylab.data.local.MutationReceiptEntity
import com.safal207.androidreliabilitylab.domain.IncidentStatus

/** Receipt contract v1 of the deterministic fixture, not a general backend guarantee. */
data class MutationReceipt(
    val actionId: String,
    val incidentId: String,
    val targetStatus: String,
    val effectId: String,
    val receiptVersion: Int,
    val effectSequence: Long,
) {
    fun requireMatch(actionId: String, incidentId: String, status: IncidentStatus): MutationReceipt {
        require(this.actionId == actionId && this.incidentId == incidentId && targetStatus == status.name) {
            "Server receipt does not match the action and payload"
        }
        require(effectId.isNotBlank() && receiptVersion == 1 && effectSequence > 0) { "Invalid server receipt" }
        return this
    }

    fun toEntity() = MutationReceiptEntity(actionId, incidentId, targetStatus, effectId, receiptVersion, effectSequence)
}
