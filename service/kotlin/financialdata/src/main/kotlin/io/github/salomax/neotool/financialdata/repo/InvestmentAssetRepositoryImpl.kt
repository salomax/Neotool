package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.common.graphql.pagination.CompositeCursor
import io.github.salomax.neotool.financialdata.domain.AssetDailyPrice
import io.github.salomax.neotool.financialdata.domain.ListedAsset
import io.micronaut.transaction.annotation.ReadOnly
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * Native-SQL implementation of [ListedAssetRepositoryCustom].
 *
 * All list/detail queries fetch latestPrice inline via a LATERAL join to avoid N+1.
 * Keyset pagination uses a composite (sim_score, ticker, id) cursor when a search query
 * is active, or (ticker, id) for the browse-all case.
 */
@Singleton
open class ListedAssetRepositoryImpl(
    private val entityManager: EntityManager,
) : ListedAssetRepositoryCustom {

    // -------------------------------------------------------------------------
    // Autocomplete
    // -------------------------------------------------------------------------

    @ReadOnly
    override fun searchAssetsAutocomplete(
        query: String,
        assetType: String?,
        limit: Int,
    ): List<ListedAsset> {
        val assetTypeFilter = if (assetType != null) "AND ia.asset_type = ?" else ""
        val sql = """
            SELECT
                ia.id::text,
                ia.ticker,
                ia.name,
                ia.asset_type,
                ia.exchange,
                ia.is_active
            FROM financial_data.investment_asset ia
            WHERE ia.source = 'B3'
              AND ia.ticker IS NOT NULL
              AND (ia.ticker ILIKE ? OR ia.name ILIKE ?)
              $assetTypeFilter
            ORDER BY
                CASE
                    WHEN upper(ia.ticker) = upper(?) THEN 0
                    WHEN ia.ticker ILIKE ? THEN 1
                    ELSE 2
                END,
                financial_data.similarity(ia.name, ?::text) DESC,
                ia.ticker ASC
            LIMIT ?
        """.trimIndent()

        val likeContains = "%$query%"
        val likePrefix = "$query%"
        val q = entityManager.createNativeQuery(sql)
        var p = 1
        q.setParameter(p++, likeContains)  // ticker ILIKE
        q.setParameter(p++, likeContains)  // name ILIKE
        if (assetType != null) q.setParameter(p++, assetType)
        q.setParameter(p++, query)          // exact ticker match
        q.setParameter(p++, likePrefix)     // ticker prefix
        q.setParameter(p++, query)          // similarity on name
        q.setParameter(p, limit)

        @Suppress("UNCHECKED_CAST")
        val rows = q.resultList as List<Array<Any?>>
        return rows.map { mapAssetRow(it, includePrice = false) }
    }

    // -------------------------------------------------------------------------
    // List / search with pagination
    // -------------------------------------------------------------------------

    @ReadOnly
    override fun searchAssets(
        query: String?,
        assetType: String?,
        isActive: Boolean?,
        first: Int,
        after: CompositeCursor?,
    ): Pair<List<ListedAsset>, Long> {
        val hasQuery = !query.isNullOrBlank()
        val hasAssetType = assetType != null
        val hasIsActive = isActive != null
        val hasCursor = after != null

        val sql = buildSearchAssetsQuery(hasQuery, hasAssetType, hasIsActive, hasCursor)
        val q = entityManager.createNativeQuery(sql)

        val likePattern = if (hasQuery) "%$query%" else null
        var p = 1
        if (hasQuery) {
            q.setParameter(p++, query)        // SELECT similarity(name, ?)
            q.setParameter(p++, likePattern)  // WHERE ticker ILIKE ?
            q.setParameter(p++, likePattern)  // OR name ILIKE ?
        }
        if (hasAssetType) q.setParameter(p++, assetType)
        if (hasIsActive) q.setParameter(p++, isActive)
        if (hasCursor) {
            val cursorTicker = after!!.fieldValues["ticker"] as? String ?: ""
            val cursorId = after.id
            if (hasQuery) {
                val cursorSim = (after.fieldValues["simScore"] as? Number)?.toDouble() ?: 1.0
                q.setParameter(p++, cursorSim)
                q.setParameter(p++, cursorSim)
                q.setParameter(p++, cursorTicker)
                q.setParameter(p++, cursorTicker)
                q.setParameter(p, cursorId)
            } else {
                q.setParameter(p++, cursorTicker)
                q.setParameter(p++, cursorTicker)
                q.setParameter(p, cursorId)
            }
        }
        q.setMaxResults(first)

        @Suppress("UNCHECKED_CAST")
        val rows = q.resultList as List<Array<Any?>>
        val totalCount = (rows.firstOrNull()?.get(11) as? Number)?.toLong() ?: 0L
        val assets = rows.map { row ->
            val simScore = (row[6] as? Number)?.toDouble() ?: 0.0
            mapAssetRow(row, includePrice = true, simScore = simScore)
        }
        return Pair(assets, totalCount)
    }

    private fun buildSearchAssetsQuery(
        hasQuery: Boolean,
        hasAssetType: Boolean,
        hasIsActive: Boolean,
        hasCursor: Boolean,
    ): String {
        val similaritySelect = if (hasQuery) {
            "financial_data.similarity(ia.name, ?::text) AS sim_score,"
        } else {
            "0.0 AS sim_score,"
        }

        val queryFilter = if (hasQuery) "AND (ia.ticker ILIKE ? OR ia.name ILIKE ?)" else ""
        val assetTypeFilter = if (hasAssetType) "AND ia.asset_type = ?" else ""
        val isActiveFilter = if (hasIsActive) "AND ia.is_active = ?" else ""

        val cursorFilter = if (hasCursor) {
            if (hasQuery) {
                "AND (sim_score < ? OR (sim_score = ? AND (ia.ticker > ? OR (ia.ticker = ? AND ia.id::text > ?))))"
            } else {
                "AND (ia.ticker > ? OR (ia.ticker = ? AND ia.id::text > ?))"
            }
        } else ""

        val orderBy = if (hasQuery) {
            "ORDER BY sim_score DESC, ia.ticker ASC, ia.id ASC"
        } else {
            "ORDER BY ia.ticker ASC, ia.id ASC"
        }

        // Columns: id(0), ticker(1), name(2), asset_type(3), exchange(4), is_active(5),
        //          sim_score(6), price_date(7), open(8), high(9), low(10), close(11) — wait, need to recount
        // Actually: id(0), ticker(1), name(2), asset_type(3), exchange(4), is_active(5),
        //           sim_score(6), price_date(7), open_price(8), high_price(9), low_price(10),
        //           close_price(11) — but total_count needs to be at a fixed index.
        // Let's put total_count at index 12 and avg_price,volume,trades after.
        return """
            SELECT
                ia.id::text,
                ia.ticker,
                ia.name,
                ia.asset_type,
                ia.exchange,
                ia.is_active,
                $similaritySelect
                p.price_date,
                p.open_price,
                p.high_price,
                p.low_price,
                p.close_price,
                COUNT(*) OVER() AS total_count
            FROM financial_data.investment_asset ia
            LEFT JOIN LATERAL (
                SELECT price_date, open_price, high_price, low_price, close_price
                FROM financial_data.asset_daily_price
                WHERE asset_id = ia.id
                ORDER BY price_date DESC
                LIMIT 1
            ) p ON true
            WHERE ia.source = 'B3'
              AND ia.ticker IS NOT NULL
              $queryFilter
              $assetTypeFilter
              $isActiveFilter
              $cursorFilter
            $orderBy
        """.trimIndent()
    }

    // -------------------------------------------------------------------------
    // Detail
    // -------------------------------------------------------------------------

    @ReadOnly
    override fun findAssetById(id: UUID): ListedAsset? {
        val sql = """
            SELECT
                ia.id::text,
                ia.ticker,
                ia.name,
                ia.asset_type,
                ia.exchange,
                ia.is_active,
                0.0 AS sim_score,
                p.price_date,
                p.open_price,
                p.high_price,
                p.low_price,
                p.close_price
            FROM financial_data.investment_asset ia
            LEFT JOIN LATERAL (
                SELECT price_date, open_price, high_price, low_price, close_price
                FROM financial_data.asset_daily_price
                WHERE asset_id = ia.id
                ORDER BY price_date DESC
                LIMIT 1
            ) p ON true
            WHERE ia.id = ?::uuid
        """.trimIndent()

        val q = entityManager.createNativeQuery(sql)
        q.setParameter(1, id.toString())

        @Suppress("UNCHECKED_CAST")
        val rows = q.resultList as List<Array<Any?>>
        return rows.firstOrNull()?.let { mapAssetRow(it, includePrice = true) }
    }

    @ReadOnly
    override fun findAssetByTicker(ticker: String): ListedAsset? {
        val sql = """
            SELECT
                ia.id::text,
                ia.ticker,
                ia.name,
                ia.asset_type,
                ia.exchange,
                ia.is_active,
                0.0 AS sim_score,
                p.price_date,
                p.open_price,
                p.high_price,
                p.low_price,
                p.close_price
            FROM financial_data.investment_asset ia
            LEFT JOIN LATERAL (
                SELECT price_date, open_price, high_price, low_price, close_price
                FROM financial_data.asset_daily_price
                WHERE asset_id = ia.id
                ORDER BY price_date DESC
                LIMIT 1
            ) p ON true
            WHERE upper(ia.ticker) = upper(?)
              AND ia.source = 'B3'
        """.trimIndent()

        val q = entityManager.createNativeQuery(sql)
        q.setParameter(1, ticker)

        @Suppress("UNCHECKED_CAST")
        val rows = q.resultList as List<Array<Any?>>
        return rows.firstOrNull()?.let { mapAssetRow(it, includePrice = true) }
    }

    // -------------------------------------------------------------------------
    // Price history
    // -------------------------------------------------------------------------

    @ReadOnly
    override fun getPriceHistory(
        assetId: UUID,
        from: LocalDate?,
        to: LocalDate?,
        last: Int?,
    ): List<AssetDailyPrice> {
        val fromFilter = if (from != null) "AND price_date >= ?" else ""
        val toFilter = if (to != null) "AND price_date <= ?" else ""
        val limit = last ?: 365

        val sql = """
            SELECT price_date, open_price, high_price, low_price, close_price, avg_price, volume, trades
            FROM financial_data.asset_daily_price
            WHERE asset_id = ?::uuid
              $fromFilter
              $toFilter
            ORDER BY price_date ASC
            LIMIT ?
        """.trimIndent()

        val q = entityManager.createNativeQuery(sql)
        var p = 1
        q.setParameter(p++, assetId.toString())
        if (from != null) q.setParameter(p++, from)
        if (to != null) q.setParameter(p++, to)
        q.setParameter(p, limit)

        @Suppress("UNCHECKED_CAST")
        val rows = q.resultList as List<Array<Any?>>
        return rows.map { row ->
            AssetDailyPrice(
                date = (row[0] as java.sql.Date).toLocalDate(),
                openPrice = row[1] as? BigDecimal,
                highPrice = row[2] as? BigDecimal,
                lowPrice = row[3] as? BigDecimal,
                closePrice = row[4] as BigDecimal,
                avgPrice = row[5] as? BigDecimal,
                volume = row[6] as? BigDecimal,
                trades = (row[7] as? Number)?.toInt(),
            )
        }
    }

    // -------------------------------------------------------------------------
    // Mapping
    // -------------------------------------------------------------------------

    /**
     * Maps a result row to [ListedAsset].
     *
     * Autocomplete (includePrice=false): id(0), ticker(1), name(2), asset_type(3), exchange(4), is_active(5)
     * List/detail  (includePrice=true):  same + sim_score(6), price_date(7), open(8), high(9), low(10), close(11)
     * searchAssets adds total_count(12) via COUNT(*) OVER() — extracted separately before calling this.
     */
    private fun mapAssetRow(row: Array<Any?>, includePrice: Boolean, simScore: Double = 0.0): ListedAsset {
        val latestPrice = if (includePrice) {
            val priceDate = row[7] as? java.sql.Date
            val closePrice = row[11] as? BigDecimal
            if (priceDate != null && closePrice != null) {
                AssetDailyPrice(
                    date = priceDate.toLocalDate(),
                    openPrice = row[8] as? BigDecimal,
                    highPrice = row[9] as? BigDecimal,
                    lowPrice = row[10] as? BigDecimal,
                    closePrice = closePrice,
                    avgPrice = null,
                    volume = null,
                    trades = null,
                )
            } else null
        } else null

        return ListedAsset(
            id = UUID.fromString(row[0] as String),
            ticker = row[1] as String,
            name = row[2] as String,
            assetType = row[3] as String,
            exchange = row[4] as? String,
            isActive = row[5] as Boolean,
            latestPrice = latestPrice,
            simScore = simScore,
        )
    }
}
