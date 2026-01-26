package io.github.salomax.neotool.financialdata.domain

import io.github.salomax.neotool.financialdata.model.FinancialInstitutionRatingEntity
import io.micronaut.serde.annotation.Serdeable
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Serdeable
data class FinancialInstitutionRating(
    val id: UUID? = null,
    val financialInstitutionId: UUID,
    val issuerId: UUID,
    val name: String,
    val label: String,
    val ratingDate: LocalDate,
    val customParams: Map<String, Any> = emptyMap(),
    val sourceEvidenceId: UUID? = null,
    val contentHash: String,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    fun toEntity(): FinancialInstitutionRatingEntity {
        return FinancialInstitutionRatingEntity(
            id = this.id,
            financialInstitutionId = this.financialInstitutionId,
            issuerId = this.issuerId,
            name = this.name,
            label = this.label,
            ratingDate = this.ratingDate,
            customParams = this.customParams,
            sourceEvidenceId = this.sourceEvidenceId,
            contentHash = this.contentHash,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
        )
    }
}
