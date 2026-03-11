package io.github.salomax.neotool.banking.domain.transaction

import java.math.BigDecimal
import java.util.UUID

/**
 * Aggregate totals for one (financialAccountId, year, month).
 * Used by [SnapshotComputationService] to compute monthly balance snapshots.
 *
 * All amounts are positive; direction is carried by the field name.
 * FUTURE-status transactions are excluded from the real totals.
 */
data class MonthTransactionTotals(
    val financialAccountId: UUID,
    val year: Int,
    val month: Int,
    /** SUM of all CREDIT amounts in the month (POSTED + PENDING, excl. FUTURE). */
    val totalCredit: BigDecimal,
    /** SUM of all DEBIT amounts in the month (POSTED + PENDING, excl. FUTURE). */
    val totalDebit: BigDecimal,
    /** SUM of CREDIT amounts WHERE type NOT IN (TRANSFER, BALANCE_ADJUSTMENT). */
    val totalIncome: BigDecimal,
    /** SUM of DEBIT amounts WHERE type NOT IN (TRANSFER, BALANCE_ADJUSTMENT). */
    val totalExpenses: BigDecimal,
    /** SUM of FUTURE CREDIT amounts (for projectedClosingBalance). */
    val totalFutureCredit: BigDecimal,
    /** SUM of FUTURE DEBIT amounts (for projectedClosingBalance). */
    val totalFutureDebit: BigDecimal,
    /** SUM of FUTURE CREDIT amounts WHERE type NOT IN (TRANSFER, BALANCE_ADJUSTMENT). */
    val totalFutureIncome: BigDecimal,
    /** SUM of FUTURE DEBIT amounts WHERE type NOT IN (TRANSFER, BALANCE_ADJUSTMENT). */
    val totalFutureExpenses: BigDecimal,
)

/**
 * Aggregate totals for TRANSFER type in one month — used by MonthSummaryQueryService
 * to populate the on-demand transfersIn / transfersOut fields.
 */
data class MonthTransferTotals(
    val transfersIn: BigDecimal,
    val transfersOut: BigDecimal,
)
