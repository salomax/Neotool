package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.common.graphql.pagination.CompositeCursor
import io.github.salomax.neotool.financialdata.domain.AssetCorporateEventSyncItem
import io.micronaut.transaction.annotation.ReadOnly
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Singleton
open class AssetCorporateEventRepositoryImpl(
    private val entityManager: EntityManager,
) : AssetCorporateEventRepository {

    @ReadOnly
    override fun findStockEventsForSync(
        from: LocalDate,
        to: LocalDate,
        tickers: Collection<String>?,
        isins: Collection<String>?,
        updatedSince: Instant?,
        first: Int,
        after: CompositeCursor?,
    ): List<AssetCorporateEventSyncItem> {
        val normalizedTickers = tickers?.map { it.trim().uppercase() }?.filter { it.isNotBlank() }?.distinct()
        val normalizedIsins = isins?.map { it.trim().uppercase() }?.filter { it.isNotBlank() }?.distinct()
        val hasTickers = !normalizedTickers.isNullOrEmpty()
        val hasIsins = !normalizedIsins.isNullOrEmpty()
        val hasUpdatedSince = updatedSince != null
        val hasCursor = after != null
        val tickerPlaceholders = normalizedTickers?.indices?.joinToString(", ") { "?" }.orEmpty()
        val isinPlaceholders = normalizedIsins?.indices?.joinToString(", ") { "?" }.orEmpty()

        val sql = """
            SELECT
                se.id::text,
                se.asset_id::text,
                ia.ticker,
                se.isin_code,
                se.event_type,
                se.factor,
                se.price_unit,
                se.trading_start,
                se.trading_end,
                se.subscription_date,
                se.last_date_prior,
                se.approved_on,
                se.remarks,
                se.updated_at
            FROM financial_data.asset_stock_event se
            LEFT JOIN financial_data.investment_asset ia ON ia.id = se.asset_id
            WHERE se.last_date_prior >= ?
              AND se.last_date_prior <= ?
              ${if (hasTickers) "AND upper(ia.ticker) IN ($tickerPlaceholders)" else ""}
              ${if (hasIsins) "AND upper(se.isin_code) IN ($isinPlaceholders)" else ""}
              ${if (hasUpdatedSince) "AND se.updated_at >= ?" else ""}
              ${
            if (hasCursor) {
                "AND (se.last_date_prior > ? OR (se.last_date_prior = ? AND (se.isin_code > ? OR (se.isin_code = ? AND (se.event_type > ? OR (se.event_type = ? AND (se.approved_on > ? OR (se.approved_on = ? AND se.id::text > ?))))))))"
            } else {
                ""
            }
        }
            ORDER BY se.last_date_prior ASC, se.isin_code ASC, se.event_type ASC, se.approved_on ASC, se.id ASC
        """.trimIndent()

        val query = entityManager.createNativeQuery(sql)
        var index = 1
        query.setParameter(index++, from)
        query.setParameter(index++, to)
        normalizedTickers?.forEach { query.setParameter(index++, it) }
        normalizedIsins?.forEach { query.setParameter(index++, it) }
        if (hasUpdatedSince) query.setParameter(index++, Timestamp.from(updatedSince))
        if (hasCursor) {
            val cursorDate = LocalDate.parse(after!!.fieldValues["lastDatePrior"] as String)
            val cursorIsin = after.fieldValues["isinCode"] as String
            val cursorType = after.fieldValues["eventType"] as String
            val cursorApprovedOn = LocalDate.parse(after.fieldValues["approvedOn"] as String)
            query.setParameter(index++, cursorDate)
            query.setParameter(index++, cursorDate)
            query.setParameter(index++, cursorIsin)
            query.setParameter(index++, cursorIsin)
            query.setParameter(index++, cursorType)
            query.setParameter(index++, cursorType)
            query.setParameter(index++, cursorApprovedOn)
            query.setParameter(index++, cursorApprovedOn)
            query.setParameter(index++, after.id)
        }
        query.setMaxResults(first)

        @Suppress("UNCHECKED_CAST")
        val rows = query.resultList as List<Array<Any?>>
        return rows.map { row ->
            AssetCorporateEventSyncItem(
                id = UUID.fromString(row[0] as String),
                assetId = (row[1] as? String)?.let(UUID::fromString),
                ticker = row[2] as? String,
                isinCode = row[3] as String,
                eventType = row[4] as String,
                factor = row[5] as? BigDecimal,
                priceUnit = row[6] as? BigDecimal,
                tradingStart = (row[7] as? java.sql.Date)?.toLocalDate(),
                tradingEnd = (row[8] as? java.sql.Date)?.toLocalDate(),
                subscriptionDate = (row[9] as? java.sql.Date)?.toLocalDate(),
                lastDatePrior = (row[10] as java.sql.Date).toLocalDate(),
                approvedOn = (row[11] as java.sql.Date).toLocalDate(),
                remarks = row[12] as? String,
                updatedAt = requireNotNull(row[13].asInstantOrNull()) { "Invalid updated_at value: ${row[13]}" },
            )
        }
    }
}
