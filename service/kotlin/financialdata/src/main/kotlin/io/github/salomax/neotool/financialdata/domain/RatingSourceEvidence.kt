package io.github.salomax.neotool.financialdata.domain

import io.github.salomax.neotool.financialdata.model.RatingSourceEvidenceEntity
import io.micronaut.serde.annotation.Serdeable
import java.time.Instant
import java.util.UUID

@Serdeable
data class RatingSourceEvidence(
    val id: UUID? = null,
    val issuerId: UUID,
    val fetchedAt: Instant = Instant.now(),
    val metadata: Map<String, Any> = emptyMap(),
    val rawPayload: String,
    val contentHash: String,
    val createdAt: Instant = Instant.now(),
) {
    fun toEntity(): RatingSourceEvidenceEntity {
        return RatingSourceEvidenceEntity(
            id = this.id,
            issuerId = this.issuerId,
            fetchedAt = this.fetchedAt,
            metadata = this.metadata,
            rawPayload = this.rawPayload,
            contentHash = this.contentHash,
            createdAt = this.createdAt,
        )
    }
}
