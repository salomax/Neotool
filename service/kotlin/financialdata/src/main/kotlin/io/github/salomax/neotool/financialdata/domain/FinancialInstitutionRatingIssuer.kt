package io.github.salomax.neotool.financialdata.domain

import io.github.salomax.neotool.financialdata.model.FinancialInstitutionRatingIssuerEntity
import io.micronaut.serde.annotation.Serdeable
import java.time.Instant
import java.util.UUID

@Serdeable
data class FinancialInstitutionRatingIssuer(
    val id: UUID? = null,
    val ratingIssuerId: UUID,
    val financialInstitutionId: UUID,
    val config: Map<String, Any> = emptyMap(),
    val enabled: Boolean = true,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    fun toEntity(): FinancialInstitutionRatingIssuerEntity {
        return FinancialInstitutionRatingIssuerEntity(
            id = this.id,
            ratingIssuerId = this.ratingIssuerId,
            financialInstitutionId = this.financialInstitutionId,
            config = this.config,
            enabled = this.enabled,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
        )
    }
}
