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

    override fun findSeries(
        indicatorLabel: String,
        variationLabel: String,
        from: LocalDate?,
        to: LocalDate?,
        limit: Int?,
    ): List<IndicatorValuePoint> {
        log.trace(
            "findSeries: indicator={}, variation={}, from={}, to={}, limit={}",
            indicatorLabel,
            variationLabel,
            from,
            to,
            limit,
        )

        val params =
            mutableMapOf<String, Any>(
                "indicator" to indicatorLabel,
                "variation" to variationLabel,
            )
        val conditions = mutableListOf("i.label = :indicator", "v.label = :variation", "i.is_active = true")
        if (from != null) {
            conditions += "iv.reference_date >= :fromDate"
            params["fromDate"] = Date.valueOf(from)
        }
        if (to != null) {
            conditions += "iv.reference_date <= :toDate"
            params["toDate"] = Date.valueOf(to)
        }

        val baseSql =
            """
            SELECT iv.reference_date, iv.value
            FROM financial_data.indicator_value iv
            JOIN financial_data.indicator_variation v ON v.id = iv.indicator_variation_id
            JOIN financial_data.indicator i ON i.id = v.indicator_id
            WHERE ${conditions.joinToString(" AND ")}
            """.trimIndent()

        val sql =
            if (limit != null && limit > 0) {
                params["limit"] = limit
                """
                SELECT reference_date, value
                FROM (
                    $baseSql
                    ORDER BY iv.reference_date DESC
                    LIMIT :limit
                ) latest
                ORDER BY reference_date ASC
                """.trimIndent()
            } else {
                """
                $baseSql
                ORDER BY iv.reference_date ASC
                """.trimIndent()
            }

        val query = entityManager.createNativeQuery(sql)
        params.forEach { (key, value) -> query.setParameter(key, value) }
        val rows = query.resultList

        val points =
            rows.mapNotNull { raw ->
                val row = raw as? Array<*> ?: return@mapNotNull null
                val date = row.getOrNull(0) as? Date ?: return@mapNotNull null
                val value = row.getOrNull(1) as? BigDecimal ?: return@mapNotNull null
                IndicatorValuePoint(date.toLocalDate(), value)
            }

        log.trace("findSeries: returned {} points", points.size)
        return points
    }

    override fun findLatest(
        indicatorLabel: String,
        variationLabel: String,
    ): IndicatorValuePoint? {
        log.trace("findLatest: indicator={}, variation={}", indicatorLabel, variationLabel)

        val sql =
            """
            SELECT iv.reference_date, iv.value
            FROM financial_data.indicator_value iv
            JOIN financial_data.indicator_variation v ON v.id = iv.indicator_variation_id
            JOIN financial_data.indicator i ON i.id = v.indicator_id
            WHERE i.label = :indicator
              AND v.label = :variation
              AND i.is_active = true
            ORDER BY iv.reference_date DESC
            LIMIT 1
            """.trimIndent()
        val query = entityManager.createNativeQuery(sql)
        query.setParameter("indicator", indicatorLabel)
        query.setParameter("variation", variationLabel)
        val row = query.resultList.firstOrNull() as? Array<*>

        if (row == null) {
            log.trace("findLatest: no data found")
            return null
        }

        val date = row.getOrNull(0) as? Date ?: return null
        val value = row.getOrNull(1) as? BigDecimal ?: return null

        log.trace("findLatest: found date={}", date)
        return IndicatorValuePoint(date.toLocalDate(), value)
    }

    override fun findUnit(
        indicatorLabel: String,
        variationLabel: String,
    ): String? {
        log.trace("findUnit: indicator={}, variation={}", indicatorLabel, variationLabel)

        val sql =
            """
            SELECT COALESCE(v.unit, i.unit)
            FROM financial_data.indicator_variation v
            JOIN financial_data.indicator i ON i.id = v.indicator_id
            WHERE i.label = :indicator AND v.label = :variation
              AND i.is_active = true
            LIMIT 1
            """.trimIndent()
        val query = entityManager.createNativeQuery(sql)
        query.setParameter("indicator", indicatorLabel)
        query.setParameter("variation", variationLabel)
        val unit = query.resultList.firstOrNull() as? String

        log.trace("findUnit: result={}", unit)
        return unit
    }
}
