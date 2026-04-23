package io.github.salomax.neotool.banking.domain.transaction

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Monthly balance snapshot — read cache for the monthly summary header.
 *
 * Source of truth is always [banking.transactions].
 * This is a derived value recomputed synchronously on every transaction write.
 *
 * [openingBalance] = prior month's [closingBalance], or 0 at account genesis.
 * [closingBalance] = [openingBalance] + SUM(CREDIT where type != TRANSFER)
 *                  - SUM(DEBIT where type != TRANSFER) for the month (POSTED/PENDING, not FUTURE).
 *                  (BALANCE_ADJUSTMENT is included; only TRANSFER is excluded).
 * [totalIncome]    = SUM(CREDIT) WHERE type NOT IN (TRANSFER, BALANCE_ADJUSTMENT).
 * [totalExpenses]  = SUM(DEBIT)  WHERE type NOT IN (TRANSFER, BALANCE_ADJUSTMENT).
 * [isDirty]        = safety net; true when a mid-chain recompute failed. Read path heals on demand.
 */
data class MonthlyBalanceSnapshot(
    val financialAccountId: UUID,
    val accountId: UUID,
    val year: Int,
    val month: Int,
    val openingBalance: BigDecimal,
    val closingBalance: BigDecimal,
    val totalIncome: BigDecimal,
    val totalExpenses: BigDecimal,
    val isDirty: Boolean = false,
    val computedAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
