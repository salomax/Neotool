package io.github.salomax.neotool.banking.domain.transaction

import java.math.BigDecimal

/**
 * Monthly summary for the selected (year, month) and optional financial account.
 *
 * Served from [MonthlyBalanceSnapshot] (closingBalance, openingBalance, totalIncome, totalExpenses).
 * On-demand fields
 * (transfersIn, transfersOut, projectedClosingBalance, expensesByCategory, incomeByCategory,
 * tagsSummary) are computed at query time.
 */
data class MonthSummary(
    val year: Int,
    val month: Int,
    /** Entradas — CREDIT excl. TRANSFER & BALANCE_ADJUSTMENT (POSTED/PENDING only). */
    val totalIncome: BigDecimal,
    /** Saídas — DEBIT excl. TRANSFER & BALANCE_ADJUSTMENT (POSTED/PENDING only). */
    val totalExpenses: BigDecimal,
    /** Saldo total at end of month (includes TRANSFER & BALANCE_ADJUSTMENT, excludes FUTURE). */
    val closingBalance: BigDecimal,
    /** Balance at start of month (prior month's closingBalance). */
    val openingBalance: BigDecimal,
    /** CREDIT of type TRANSFER in the month (single-account view). */
    val transfersIn: BigDecimal,
    /** DEBIT of type TRANSFER in the month (single-account view). */
    val transfersOut: BigDecimal,
    /** closingBalance + FUTURE transactions (projeção). Null when no FUTURE transactions exist. */
    val projectedClosingBalance: BigDecimal?,
    /** True when (year, month) equals the current calendar month. */
    val isCurrentMonth: Boolean,
    /** True when (year, month) is strictly after the current calendar month. No snapshot is persisted; all values are projections. */
    val isFutureMonth: Boolean = false,
    /** Top expense categories for the month (CAT-15). Empty for future months. */
    val expensesByCategory: List<CategoryTotal> = emptyList(),
    /** Top income categories for the month (CAT-15). Empty for future months. */
    val incomeByCategory: List<CategoryTotal> = emptyList(),
    /** Top tags for the month across all posted/pending transactions. Empty for future months. */
    val tagsSummary: List<TagTotal> = emptyList(),
)
