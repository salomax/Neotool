package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.common.graphql.pagination.CompositeCursor
import io.github.salomax.neotool.financialdata.domain.FundDailyNav
import io.github.salomax.neotool.financialdata.domain.FundTaxProfile
import io.github.salomax.neotool.financialdata.domain.InvestmentFund
import io.github.salomax.neotool.financialdata.domain.InvestmentFundMarketSnapshot
import io.micronaut.transaction.annotation.ReadOnly
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * Native-SQL implementation of [InvestmentFundRepositoryCustom].
 *
 * All list/search queries fetch latestNav inline via a LATERAL join to avoid N+1.
 * Keyset pagination uses a composite (sim_score, name, id) cursor when a query is
 * active, or a (name, id) cursor for the browse-all case.
 */
@Singleton
open class InvestmentFundRepositoryImpl(
    private val entityManager: EntityManager,
) : InvestmentFundRepositoryCustom {

    // -------------------------------------------------------------------------
    // Autocomplete
    // -------------------------------------------------------------------------

    @ReadOnly
    override fun searchFundsAutocomplete(
        query: String,
        limit: Int,
    ): List<InvestmentFund> {
        val sql = """
            WITH params AS (
                SELECT
                    financial_data.normalize_fund_search_text(?::text) AS normalized_query,
                    financial_data.build_fund_search_tokens(?::text) AS query_tokens
            )
            SELECT
                ia.id::text,
                ia.name,
                ia.fund_cnpj,
                ia.asset_type,
                ia.is_active,
                financial_data.similarity(
                    COALESCE(ia.fund_search_text, financial_data.build_fund_search_text(ia.name)),
                    p.normalized_query
                ) AS sim_score
            FROM financial_data.investment_asset ia
            CROSS JOIN params p
            WHERE ia.source = 'CVM'
              AND ia.fund_cnpj IS NOT NULL
              AND p.normalized_query <> ''
              AND (
                  COALESCE(ia.fund_search_text, financial_data.build_fund_search_text(ia.name)) LIKE '%' || p.normalized_query || '%'
                  OR NOT EXISTS (
                      SELECT 1
                      FROM unnest(p.query_tokens) AS qt
                      WHERE NOT EXISTS (
                          SELECT 1
                          FROM unnest(COALESCE(ia.fund_search_tokens, financial_data.build_fund_search_tokens(ia.name))) AS st
                          WHERE st LIKE qt || '%'
                      )
                  )
              )
            ORDER BY
                CASE
                    WHEN COALESCE(ia.fund_search_text, financial_data.build_fund_search_text(ia.name)) LIKE p.normalized_query || '%' THEN 0
                    ELSE 1
                END,
                sim_score DESC,
                ia.name ASC
            LIMIT ?
        """.trimIndent()

        val q = entityManager.createNativeQuery(sql)
        q.setParameter(1, query)
        q.setParameter(2, query)
        q.setParameter(3, limit)

        @Suppress("UNCHECKED_CAST")
        val rows = q.resultList as List<Array<Any?>>
        return rows.map { mapFundRow(it, includeNav = false) }
    }

    // -------------------------------------------------------------------------
    // List / search with pagination
    // -------------------------------------------------------------------------

    @ReadOnly
    override fun searchFunds(
        query: String?,
        assetType: String?,
        isActive: Boolean?,
        first: Int,
        after: CompositeCursor?,
    ): Pair<List<InvestmentFund>, Long> {
        val hasQuery = !query.isNullOrBlank()
        val hasAssetType = assetType != null
        val hasIsActive = isActive != null
        val hasCursor = after != null

        val sql = buildSearchFundsQuery(hasQuery, hasAssetType, hasIsActive, hasCursor)
        val q = entityManager.createNativeQuery(sql)

        val likePattern = if (hasQuery) "%$query%" else null
        var p = 1
        if (hasQuery) {
            q.setParameter(p++, query)        // SELECT similarity(name, ?)
            q.setParameter(p++, likePattern)  // WHERE name ILIKE ?
        }
        if (hasAssetType) q.setParameter(p++, assetType)
        if (hasIsActive) q.setParameter(p++, isActive)
        if (hasCursor) {
            val cursorName = after!!.fieldValues["name"] as? String ?: ""
            val cursorId = after.id
            if (hasQuery) {
                val cursorSim = (after.fieldValues["simScore"] as? Number)?.toDouble() ?: 1.0
                q.setParameter(p++, cursorSim)   // sim_score < ?
                q.setParameter(p++, cursorSim)   // sim_score = ?
                q.setParameter(p++, cursorName)  // name > ?
                q.setParameter(p++, cursorName)  // name = ?
                q.setParameter(p, cursorId)      // id > ?
            } else {
                q.setParameter(p++, cursorName)  // name > ?
                q.setParameter(p++, cursorName)  // name = ?
                q.setParameter(p, cursorId)      // id > ?
            }
        }
        q.setMaxResults(first)

        @Suppress("UNCHECKED_CAST")
        val rows = q.resultList as List<Array<Any?>>
        val totalCount = (rows.firstOrNull()?.get(9) as? Number)?.toLong() ?: 0L
        val funds = rows.map { row ->
            val simScore = (row[5] as? Number)?.toDouble() ?: 0.0
            mapFundRow(row, includeNav = true, simScore = simScore)
        }
        return Pair(funds, totalCount)
    }

    private fun buildSearchFundsQuery(
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

        val queryFilter = if (hasQuery) "AND ia.name ILIKE ?" else ""
        val assetTypeFilter = if (hasAssetType) "AND ia.asset_type = ?" else ""
        val isActiveFilter = if (hasIsActive) "AND ia.is_active = ?" else ""

        // Cursor filter matches the ORDER BY direction:
        //   hasQuery=true  → ORDER BY sim_score DESC, name ASC, id ASC
        //   hasQuery=false → ORDER BY name ASC, id ASC
        val cursorFilter = if (hasCursor) {
            if (hasQuery) {
                "AND (sim_score < ? OR (sim_score = ? AND (ia.name > ? OR (ia.name = ? AND ia.id::text > ?))))"
            } else {
                "AND (ia.name > ? OR (ia.name = ? AND ia.id::text > ?))"
            }
        } else ""

        val orderBy = if (hasQuery) {
            "ORDER BY sim_score DESC, ia.name ASC, ia.id ASC"
        } else {
            "ORDER BY ia.name ASC, ia.id ASC"
        }

        return """
            SELECT
                ia.id::text,
                ia.name,
                ia.fund_cnpj,
                ia.asset_type,
                ia.is_active,
                $similaritySelect
                nav.price_date  AS nav_date,
                nav.close_price AS nav_close_price,
                nav.volume      AS nav_total_net_assets,
                COUNT(*) OVER() AS total_count
            FROM financial_data.investment_asset ia
            LEFT JOIN LATERAL (
                SELECT price_date, close_price, volume
                FROM financial_data.asset_daily_price
                WHERE asset_id = ia.id
                ORDER BY price_date DESC
                LIMIT 1
            ) nav ON true
            WHERE ia.source = 'CVM'
              AND ia.fund_cnpj IS NOT NULL
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
    override fun findFundById(id: UUID): InvestmentFund? {
        val sql = """
            SELECT
                ia.id::text,
                ia.name,
                ia.fund_cnpj,
                ia.asset_type,
                ia.is_active,
                0.0 AS sim_score,
                nav.price_date  AS nav_date,
                nav.close_price AS nav_close_price,
                nav.volume      AS nav_total_net_assets
            FROM financial_data.investment_asset ia
            LEFT JOIN LATERAL (
                SELECT price_date, close_price, volume
                FROM financial_data.asset_daily_price
                WHERE asset_id = ia.id
                ORDER BY price_date DESC
                LIMIT 1
            ) nav ON true
            WHERE ia.id = ?::uuid
        """.trimIndent()

        val q = entityManager.createNativeQuery(sql)
        q.setParameter(1, id.toString())

        @Suppress("UNCHECKED_CAST")
        val rows = q.resultList as List<Array<Any?>>
        return rows.firstOrNull()?.let { mapFundRow(it, includeNav = true) }
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
    ): List<FundDailyNav> {
        val fromFilter = if (from != null) "AND price_date >= ?" else ""
        val toFilter = if (to != null) "AND price_date <= ?" else ""
        val limit = last ?: 365

        val sql = """
            SELECT price_date, close_price, volume
            FROM financial_data.asset_daily_price
            WHERE asset_id = ?::uuid
              $fromFilter
              $toFilter
            ORDER BY price_date DESC
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
            FundDailyNav(
                date = (row[0] as java.sql.Date).toLocalDate(),
                closePrice = row[1] as BigDecimal,
                // volume stores CVM's VL_TOTAL (total net assets / patrimônio líquido),
                // not trading volume — the column name is a legacy misnomer.
                totalNetAssets = row[2] as? BigDecimal,
            )
        }
    }

    @ReadOnly
    override fun findMarketSnapshotsByCnpj(cnpjs: Collection<String>): List<InvestmentFundMarketSnapshot> {
        val normalized = cnpjs.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (normalized.isEmpty()) return emptyList()

        val placeholders = normalized.indices.joinToString(", ") { "?" }
        val sql = """
            SELECT
                ia.fund_cnpj,
                ia.asset_type,
                nav.price_date,
                nav.close_price,
                nav.volume
            FROM financial_data.investment_asset ia
            LEFT JOIN LATERAL (
                SELECT price_date, close_price, volume
                FROM financial_data.asset_daily_price
                WHERE asset_id = ia.id
                ORDER BY price_date DESC
                LIMIT 1
            ) nav ON true
            WHERE ia.source = 'CVM'
              AND ia.fund_cnpj IN ($placeholders)
        """.trimIndent()

        val q = entityManager.createNativeQuery(sql)
        normalized.forEachIndexed { index, cnpj -> q.setParameter(index + 1, cnpj) }

        @Suppress("UNCHECKED_CAST")
        val rows = q.resultList as List<Array<Any?>>
        return rows.map { row ->
            val navDate = row[2] as? java.sql.Date
            val navClose = row[3] as? BigDecimal
            InvestmentFundMarketSnapshot(
                cnpj = row[0] as String,
                assetType = row[1] as String,
                taxProfile = taxProfileFor(row[1] as String),
                latestNav = if (navDate != null && navClose != null) {
                    FundDailyNav(
                        date = navDate.toLocalDate(),
                        closePrice = navClose,
                        totalNetAssets = row[4] as? BigDecimal,
                    )
                } else {
                    null
                },
            )
        }
    }

    // -------------------------------------------------------------------------
    // Mapping
    // -------------------------------------------------------------------------

    /**
     * Maps a result row to [InvestmentFund].
     *
     * Autocomplete (includeNav=false): id(0), name(1), fund_cnpj(2), asset_type(3), is_active(4)
     * List/detail  (includeNav=true):  id(0), name(1), fund_cnpj(2), asset_type(3), is_active(4),
     *                                   sim_score(5), nav_date(6), nav_close_price(7),
     *                                   nav_total_net_assets(8)
     * searchFunds adds total_count(9) via COUNT(*) OVER() — extracted separately before calling this.
     */
    private fun mapFundRow(row: Array<Any?>, includeNav: Boolean, simScore: Double = 0.0): InvestmentFund {
        val latestNav = if (includeNav) {
            val navDate = row[6] as? java.sql.Date
            val navClose = row[7] as? BigDecimal
            if (navDate != null && navClose != null) {
                FundDailyNav(
                    date = navDate.toLocalDate(),
                    closePrice = navClose,
                    // volume stores CVM's VL_TOTAL (total net assets), not trading volume.
                    totalNetAssets = row[8] as? BigDecimal,
                )
            } else null
        } else null

        return InvestmentFund(
            id = UUID.fromString(row[0] as String),
            name = row[1] as String,
            cnpj = row[2] as String,
            assetType = row[3] as String,
            isActive = row[4] as Boolean,
            latestNav = latestNav,
            simScore = simScore,
        )
    }

    private fun taxProfileFor(assetType: String): FundTaxProfile =
        when (assetType) {
            "FIXED_INCOME_FUND", "MULTI_MARKET_FUND" -> FundTaxProfile.COME_COTAS_LONG
            "EQUITY_FUND" -> FundTaxProfile.VARIABLE_INCOME_15
            "PGBL", "VGBL" -> FundTaxProfile.PENSION
            else -> FundTaxProfile.UNKNOWN
        }
}
