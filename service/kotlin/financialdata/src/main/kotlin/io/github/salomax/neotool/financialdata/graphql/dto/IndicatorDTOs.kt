package io.github.salomax.neotool.financialdata.graphql.dto

import io.micronaut.core.annotation.Introspected
import java.time.Instant
import java.util.UUID

@Introspected
data class EconomicIndicatorDTO(
    val id: UUID,
    val countryCode: String,
    val label: String,
    val issuer: String,
    val description: String?,
    val frequency: String,
    val unit: String,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val variations: List<EconomicIndicatorVariationDTO>,
)

@Introspected
data class EconomicIndicatorVariationDTO(
    val id: UUID,
    val label: String,
    // Added to support batched loading
    val indicatorLabel: String?,
    val unit: String?,
    val isDefault: Boolean,
    val incrementalMonths: Int?,
    val incrementalYears: Int?,
    val incrementalDays: Int?,
)
