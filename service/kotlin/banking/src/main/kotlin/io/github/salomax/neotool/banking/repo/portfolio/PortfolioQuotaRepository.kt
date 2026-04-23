package io.github.salomax.neotool.banking.repo.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.PortfolioValueWindow
import io.github.salomax.neotool.banking.domain.portfolio.InvestmentPortfolioQuota
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioDimensionType
import java.time.LocalDate
import java.util.UUID

interface PortfolioQuotaRepository {
    fun upsert(quota: InvestmentPortfolioQuota): InvestmentPortfolioQuota

    fun batchUpsert(quotas: List<InvestmentPortfolioQuota>): List<InvestmentPortfolioQuota>

    fun findLatestBefore(
        financialAccountId: UUID,
        dimensionType: PortfolioDimensionType,
        dimensionKey: String?,
        date: LocalDate,
    ): InvestmentPortfolioQuota?

    /**
     * Returns the earliest portfolio quota date for one financial account (PORTFOLIO dimension),
     * or null when no portfolio snapshots exist yet.
     */
    fun findEarliestPortfolioDate(financialAccountId: UUID): LocalDate?

    /**
     * Returns the earliest portfolio quota date per financial account (PORTFOLIO dimension).
     * Accounts without portfolio snapshots are omitted from the returned map.
     */
    fun findEarliestPortfolioDates(financialAccountIds: Collection<UUID>): Map<UUID, LocalDate>

    /**
     * Returns the latest quota for every dimension (PORTFOLIO + all ASSET_GROUP/GRID_CLASS keys) in a single query.
     * Result is keyed by [dimensionKey] (empty string for PORTFOLIO dimension).
     */
    fun findLatestBeforeAllDimensions(
        financialAccountId: UUID,
        date: LocalDate,
    ): Map<PortfolioDimensionType, Map<String, InvestmentPortfolioQuota>>

    fun findByAccountAndDateRange(
        financialAccountId: UUID,
        dimensionType: PortfolioDimensionType,
        dimensionKey: String?,
        from: LocalDate,
        to: LocalDate,
    ): List<InvestmentPortfolioQuota>

    fun findByAccountsAndDateRange(
        financialAccountIds: List<UUID>,
        dimensionType: PortfolioDimensionType,
        dimensionKey: String?,
        from: LocalDate,
        to: LocalDate,
    ): List<InvestmentPortfolioQuota>

    /**
     * Returns all quota rows for investment accounts belonging to [accountId] (tenant-scoped)
     * within the inclusive date range — single DB query, no pre-fetch of account IDs required.
     */
    fun findByTenantAndDateRange(
        accountId: UUID,
        dimensionType: PortfolioDimensionType,
        dimensionKey: String?,
        from: LocalDate,
        to: LocalDate,
    ): List<InvestmentPortfolioQuota>

    /**
     * Returns aggregated opening and closing portfolio values for the period in one query.
     * - opening: latest PORTFOLIO quota strictly before period start
     * - closing: latest PORTFOLIO quota at or before period end
     */
    fun findOpeningAndClosingPortfolioValues(
        accountId: UUID,
        financialAccountId: UUID?,
        startYear: Int,
        startMonth: Int,
        endYear: Int,
        endMonth: Int,
    ): PortfolioValueWindow

    fun deleteFromDate(
        financialAccountId: UUID,
        from: LocalDate,
    ): Int
}
