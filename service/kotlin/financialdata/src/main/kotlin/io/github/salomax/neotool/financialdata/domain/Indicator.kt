package io.github.salomax.neotool.financialdata.domain

import io.github.salomax.neotool.financialdata.model.IndicatorEntity
import io.micronaut.serde.annotation.Serdeable
import java.time.Instant
import java.util.UUID

@Serdeable
data class Indicator(
    val id: UUID? = null,
    val countryCode: String,
    val label: String,
    val issuer: String,
    val description: String? = null,
    val frequency: String,
    val unit: String,
    val isActive: Boolean = true,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    fun toEntity(): IndicatorEntity {
        return IndicatorEntity(
            id = this.id,
            countryCode = this.countryCode,
            label = this.label,
            issuer = this.issuer,
            description = this.description,
            frequency = this.frequency,
            unit = this.unit,
            isActive = this.isActive,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
        )
    }
}
