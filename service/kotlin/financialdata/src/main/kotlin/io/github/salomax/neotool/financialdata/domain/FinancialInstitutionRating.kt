package io.github.salomax.neotool.financialdata.domain

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
)
