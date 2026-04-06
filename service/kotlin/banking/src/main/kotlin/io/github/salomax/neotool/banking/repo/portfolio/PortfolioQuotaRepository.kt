package io.github.salomax.neotool.banking.repo.portfolio

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
     * Returns the latest quota for every dimension (PORTFOLIO + all ASSET_GROUP keys) in a single query.
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

    fun deleteFromDate(
        financialAccountId: UUID,
        from: LocalDate,
    ): Int
}
