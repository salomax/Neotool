package io.github.salomax.neotool.banking.domain.investment

import java.time.LocalDate
import java.util.UUID

/**
 * Domain repository for [InvestmentTransaction].
 * All queries are scoped by [accountId] (tenant ID from JWT).
 * Soft-deleted transactions are never returned.
 */
interface InvestmentTransactionRepository {
    /** Full history of events for a specific holding, ordered by trade_date DESC. */
    fun findByHolding(
        holdingId: UUID,
        accountId: UUID,
    ): List<InvestmentTransaction>

    /** All investment transactions for the tenant, ordered by trade_date DESC (for IR calculation). */
    fun findByAccount(accountId: UUID): List<InvestmentTransaction>

    /** All transactions for a custodian account, ordered by trade_date DESC. */
    fun findByFinancialAccount(
        financialAccountId: UUID,
        accountId: UUID,
    ): List<InvestmentTransaction>

    /** Transaction history for multiple holdings, grouped client-side by holdingId. */
    fun findByHoldingIds(
        holdingIds: Collection<UUID>,
        accountId: UUID,
    ): List<InvestmentTransaction>

    /**
     * Transactions by type and date range — used by the IR engine.
     * E.g. all SALE events in a calendar year for DARF calculation.
     */
    fun findByTypeAndDateRange(
        accountId: UUID,
        type: InvestmentTransactionType,
        from: LocalDate,
        to: LocalDate,
    ): List<InvestmentTransaction>

    fun findById(
        id: UUID,
        accountId: UUID,
    ): InvestmentTransaction?

    /**
     * Checks for dedup before inserting Open Finance transactions.
     * The unique key is (financial_account_id, provider_transaction_id).
     */
    fun existsByProviderTransactionId(
        financialAccountId: UUID,
        providerTransactionId: String,
    ): Boolean

    fun save(transaction: InvestmentTransaction): InvestmentTransaction

    fun softDelete(
        id: UUID,
        accountId: UUID,
    ): Boolean
}
