package io.github.salomax.neotool.financialdata.graphql.dto

import io.micronaut.core.annotation.Introspected
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Introspected
data class RatingIssuerDTO(
    val id: UUID,
    val code: String,
    val name: String,
    val website: String?,
    val logoAssetId: UUID?,
)

@Introspected
data class InstitutionRatingDTO(
    val id: UUID,
    val name: String,
    val label: String,
    val ratingDate: LocalDate,
    val customParams: Map<String, Any>?,
    val issuer: RatingIssuerDTO,
    val updatedAt: Instant,
)

@Introspected
data class RatingHistoryDTO(
    val id: UUID,
    val name: String,
    val label: String,
    val ratingDate: LocalDate,
    val customParams: Map<String, Any>?,
    val issuerCode: String,
    val changedAt: Instant,
)
