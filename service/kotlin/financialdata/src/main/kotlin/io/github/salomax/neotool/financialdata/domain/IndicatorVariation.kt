package io.github.salomax.neotool.financialdata.domain

import io.github.salomax.neotool.financialdata.model.IndicatorVariationEntity
import io.micronaut.serde.annotation.Serdeable
import java.time.Instant
import java.util.UUID

@Serdeable
data class IndicatorVariation(
    val id: UUID? = null,
    val indicatorId: UUID,
    val label: String,
    val unit: String? = null,
    val isDefault: Boolean = false,
    val incrementalMonths: Int? = null,
    val incrementalYears: Int? = null,
    val incrementalDays: Int? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    fun toEntity(): IndicatorVariationEntity {
        return IndicatorVariationEntity(
            id = this.id,
            indicatorId = this.indicatorId,
            label = this.label,
            unit = this.unit,
            isDefault = this.isDefault,
            incrementalMonths = this.incrementalMonths,
            incrementalYears = this.incrementalYears,
            incrementalDays = this.incrementalDays,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
        )
    }
}
