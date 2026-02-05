package io.github.salomax.neotool.financialdata.repo

import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.sql.Date
import java.time.LocalDate

@Singleton
open class IndicatorValueRepositoryImpl(
    @PersistenceContext private val entityManager: EntityManager,
) : IndicatorValueRepositoryCustom {
    private val log = LoggerFactory.getLogger(IndicatorValueRepositoryImpl::class.java)

    override fun findSeriesGrouped(
        indicatorLabel: String?,
        variationLabel: String?,
        from: LocalDate,
        to: LocalDate,
        limit: Int?,
    ): Map<Pair<String, String>, IndicatorSeriesData> {
        log.trace(
            "findSeriesGrouped: indicator={}, variation={}, from={}, to={}, limit={}",
            indicatorLabel,
            variationLabel,
            from,
            to,
            limit,
        )

        val params = mutableMapOf<String, Any>()
        val conditions = mutableListOf("i.is_active = true")

        if (indicatorLabel != null) {
            conditions += "i.label = :indicator"
            params["indicator"] = indicatorLabel
        }
        if (variationLabel != null) {
            conditions += "v.label = :variation"
            params["variation"] = variationLabel
        }

        conditions += "iv.reference_date >= :fromDate"
        params["fromDate"] = Date.valueOf(from)

        conditions += "iv.reference_date <= :toDate"
        params["toDate"] = Date.valueOf(to)

        val whereClause = conditions.joinToString(" AND ")

        val sql =
            if (limit != null && limit > 0) {
                params["limit"] = limit
                """
                SELECT indicator, variation, unit, reference_date, value
                FROM (
                    SELECT 
                        i.label as indicator,
                        v.label as variation,
                        COALESCE(v.unit, i.unit) as unit,
                        iv.reference_date, 
                        COALESCE(iv.adjusted_value, iv.raw_value) AS value,
                        ROW_NUMBER() OVER (PARTITION BY i.label, v.label ORDER BY iv.reference_date DESC) as rn
                    FROM financial_data.economic_indicator_value iv
                    JOIN financial_data.economic_indicator_variation v ON v.id = iv.indicator_variation_id
                    JOIN financial_data.economic_indicator i ON i.id = v.indicator_id
                    WHERE $whereClause
                ) ranked
                WHERE rn <= :limit
                ORDER BY indicator, variation, reference_date ASC
                """.trimIndent()
            } else {
                """
                SELECT 
                    i.label as indicator,
                    v.label as variation,
                    COALESCE(v.unit, i.unit) as unit,
                    iv.reference_date, 
                    COALESCE(iv.adjusted_value, iv.raw_value) AS value
                FROM financial_data.economic_indicator_value iv
                JOIN financial_data.economic_indicator_variation v ON v.id = iv.indicator_variation_id
                JOIN financial_data.economic_indicator i ON i.id = v.indicator_id
                WHERE $whereClause
                ORDER BY i.label, v.label, iv.reference_date ASC
                """.trimIndent()
            }

        val query = entityManager.createNativeQuery(sql)
        params.forEach { (key, value) -> query.setParameter(key, value) }
        val rows = query.resultList

        val result = mutableMapOf<Pair<String, String>, MutableList<IndicatorValuePoint>>()
        val units = mutableMapOf<Pair<String, String>, String?>()

        rows.forEach { raw ->
            val row = raw as? Array<*> ?: return@forEach
            val ind = row.getOrNull(0) as? String ?: return@forEach
            val `var` = row.getOrNull(1) as? String ?: return@forEach
            val unit = row.getOrNull(2) as? String
            val date = (row.getOrNull(3) as? Date)?.toLocalDate() ?: return@forEach
            val value = row.getOrNull(4) as? BigDecimal ?: return@forEach

            val key = ind to `var`
            if (!units.containsKey(key)) {
                units[key] = unit
            }
            result.computeIfAbsent(key) { mutableListOf() }.add(IndicatorValuePoint(date, value))
        }

        log.trace("findSeriesGrouped: returned {} series", result.size)
        return result.mapValues { (key, points) ->
            IndicatorSeriesData(units[key], points)
        }
    }
}
