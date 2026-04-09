package io.github.salomax.neotool.banking.repo.investment

import io.github.salomax.neotool.banking.domain.investment.Holding
import java.util.UUID

/**
 * Domain repository for [Holding] (investment positions).
 * All queries are scoped by [accountId] (tenant ID from JWT).
 * Soft-deleted holdings are never returned.
 */
interface HoldingRepository {
    /** All open positions for a custodian account. */
    fun findByFinancialAccount(
        financialAccountId: UUID,
        accountId: UUID,
    ): List<Holding>

    /** All open positions across all custodian accounts for the tenant (for net worth). */
    fun findByAccount(accountId: UUID): List<Holding>

    /** All open positions (quantity > 0) for a custodian account. */
    fun findOpenByFinancialAccount(
        financialAccountId: UUID,
        accountId: UUID,
    ): List<Holding>

    /** All open positions (quantity > 0) across all custodian accounts for the tenant. */
    fun findOpenByAccount(accountId: UUID): List<Holding>

    fun findById(
        id: UUID,
        accountId: UUID,
    ): Holding?

    /**
     * Finds an existing holding for a given custodian account + asset.
     * Used for upsert on every PURCHASE — ensures one holding per (account, asset).
     */
    fun findByFinancialAccountAndAsset(
        financialAccountId: UUID,
        assetId: UUID,
    ): Holding?

    /**
     * Finds an existing holding by the provider's own investment ID.
     * Used for idempotent Open Finance sync.
     */
    fun findByProviderInvestmentId(
        financialAccountId: UUID,
        providerInvestmentId: String,
    ): Holding?

    fun save(holding: Holding): Holding

    fun update(holding: Holding): Holding

    fun softDelete(
        id: UUID,
        accountId: UUID,
    ): Boolean
}
