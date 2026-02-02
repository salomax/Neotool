package io.github.salomax.neotool.financialdata.domain

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
)
