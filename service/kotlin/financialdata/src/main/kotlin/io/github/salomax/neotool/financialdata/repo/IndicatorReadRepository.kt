package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.financialdata.domain.Indicator
import io.github.salomax.neotool.financialdata.domain.IndicatorVariation
import io.github.salomax.neotool.financialdata.model.ValueFormat
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Singleton
open class IndicatorReadRepository(
    @PersistenceContext private val entityManager: EntityManager,
) {
    fun findActiveIndicatorsWithVariations(): Map<Indicator, List<IndicatorVariation>> {
        val sql =
            """
            SELECT
                i.id,
                i.country_code,
                i.label AS indicator_label,
                i.issuer,
                i.description,
                i.frequency,
                i.unit,
                i.is_active,
                i.created_at,
                i.updated_at,
                v.id AS variation_id,
                v.indicator_id,
                v.label AS variation_label,
                v.unit AS variation_unit,
                v.is_default,
                v.incremental_months,
                v.incremental_years,
                v.incremental_days,
                v.value_format,
                v.created_at AS variation_created_at,
                v.updated_at AS variation_updated_at
            FROM financial_data.economic_indicator i
            LEFT JOIN financial_data.economic_indicator_variation v ON v.indicator_id = i.id
            WHERE i.is_active = true
            ORDER BY i.label ASC, v.label ASC
            """.trimIndent()

        val rows = entityManager.createNativeQuery(sql).resultList
        if (rows.isEmpty()) {
            return emptyMap()
        }

        val indicators = LinkedHashMap<UUID, Indicator>(rows.size)
        val variationsByIndicator = LinkedHashMap<UUID, MutableList<IndicatorVariation>>()

        rows.forEach { raw ->
            val row = raw as? Array<*> ?: return@forEach
            val indicatorId = row.getOrNull(0) as? UUID ?: return@forEach
            val indicator =
                indicators.getOrPut(indicatorId) {
                    Indicator(
                        id = indicatorId,
                        countryCode = row.getOrNull(1) as? String ?: "",
                        label = row.getOrNull(2) as? String ?: "",
                        issuer = row.getOrNull(3) as? String ?: "",
                        description = row.getOrNull(4) as? String,
                        frequency = row.getOrNull(5) as? String ?: "",
                        unit = row.getOrNull(6) as? String ?: "",
                        isActive = row.getOrNull(7) as? Boolean ?: true,
                        createdAt = row.getOrNull(8).toInstantSafe(),
                        updatedAt = row.getOrNull(9).toInstantSafe(),
                    )
                }

            val variationId = row.getOrNull(10) as? UUID ?: return@forEach
            val variation =
                IndicatorVariation(
                    id = variationId,
                    indicatorId = indicator.id ?: indicatorId,
                    label = row.getOrNull(12) as? String ?: "",
                    unit = row.getOrNull(13) as? String,
                    isDefault = row.getOrNull(14) as? Boolean ?: false,
                    incrementalMonths = row.getOrNull(15) as? Int,
                    incrementalYears = row.getOrNull(16) as? Int,
                    incrementalDays = row.getOrNull(17) as? Int,
                    valueFormat = ValueFormat.fromString(row.getOrNull(18) as? String),
                    createdAt = row.getOrNull(19).toInstantSafe(),
                    updatedAt = row.getOrNull(20).toInstantSafe(),
                )

            variationsByIndicator.getOrPut(indicatorId) { mutableListOf() }.add(variation)
        }

        val results = LinkedHashMap<Indicator, List<IndicatorVariation>>(indicators.size)
        indicators.forEach { (id, indicator) ->
            results[indicator] = variationsByIndicator[id].orEmpty()
        }
        return results
    }
}

private fun Any?.toInstantSafe(): Instant {
    return when (this) {
        is Instant -> this
        is Timestamp -> this.toInstant()
        is java.time.OffsetDateTime -> this.toInstant()
        is java.time.LocalDateTime -> this.atZone(java.time.ZoneOffset.UTC).toInstant()
        else -> Instant.EPOCH
    }
}
