package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.common.graphql.pagination.CompositeCursor
import io.github.salomax.neotool.financialdata.domain.FundDailyNav
import io.github.salomax.neotool.financialdata.domain.InvestmentFund
import java.time.LocalDate
import java.util.UUID

/**
 * Custom query contract for InvestmentFund search and detail operations.
 */
interface InvestmentFundRepositoryCustom {

    /**
     * Search funds for autocomplete suggestions.
     * Uses trigram similarity on name. Returns top [limit] results ordered by similarity.
     */
    fun searchFundsAutocomplete(
        query: String,
        limit: Int = 10,
    ): List<InvestmentFund>

    /**
     * Search/list funds with optional filters and Relay keyset pagination.
     * When [query] is null, returns all funds ordered by name.
     * When [query] is provided, orders by trigram similarity DESC then name ASC.
     * The latestNav is fetched inline via LATERAL join.
     * Returns a Pair of (page of funds, total matching count) — the total count is
     * computed via COUNT(*) OVER() to avoid a second DB round-trip.
     */
    fun searchFunds(
        query: String?,
        assetType: String?,
        isActive: Boolean?,
        first: Int,
        after: CompositeCursor?,
    ): Pair<List<InvestmentFund>, Long>

    /**
     * Fetch a single fund by its UUID. Returns null when not found.
     * latestNav is fetched inline via LATERAL join.
     */
    fun findFundById(id: UUID): InvestmentFund?

    /**
     * Fetch historical daily NAV records for a fund.
     * [from] and [to] are inclusive date bounds. [last] limits the number of rows
     * returned (most recent first). When all are null, returns up to 365 rows.
     */
    fun getPriceHistory(
        assetId: UUID,
        from: LocalDate?,
        to: LocalDate?,
        last: Int?,
    ): List<FundDailyNav>
}
