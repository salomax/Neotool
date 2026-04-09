package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import java.math.BigDecimal
import java.math.RoundingMode

internal const val HOLDING_PERFORMANCE_COST_SCALE = 8

internal fun Holding.asEmptyPerformanceState(): Holding =
    copy(
        quantity = BigDecimal.ZERO,
        averageCost = BigDecimal.ZERO,
        grossValue = null,
        netValue = null,
        incomeTax = null,
        iof = null,
        lastValuedAt = null,
    )

internal fun Holding.applyPerformanceTransaction(transaction: InvestmentTransaction): Holding {
    val quantity = transaction.quantity
    val unitPrice =
        transaction.unitPrice ?: quantity?.takeIf { it > BigDecimal.ZERO }?.let {
            transaction.grossValue.divide(it, HOLDING_PERFORMANCE_COST_SCALE, RoundingMode.HALF_UP)
        }

    return when (transaction.type) {
        InvestmentTransactionType.PURCHASE,
        InvestmentTransactionType.SUBSCRIPTION,
        -> {
            if (quantity == null || unitPrice == null || quantity <= BigDecimal.ZERO) return this
            val newQuantity = this.quantity + quantity
            val newAverageCost =
                if (this.quantity.signum() == 0) {
                    unitPrice
                } else {
                    (this.quantity * this.averageCost + quantity * unitPrice)
                        .divide(newQuantity, HOLDING_PERFORMANCE_COST_SCALE, RoundingMode.HALF_UP)
                }
            copy(quantity = newQuantity, averageCost = newAverageCost)
        }

        InvestmentTransactionType.SALE -> {
            if (quantity == null || quantity <= BigDecimal.ZERO) return this
            copy(quantity = (this.quantity - quantity).coerceAtLeast(BigDecimal.ZERO))
        }

        InvestmentTransactionType.MATURITY -> {
            val closingQuantity = quantity ?: this.quantity
            copy(quantity = (this.quantity - closingQuantity).coerceAtLeast(BigDecimal.ZERO))
        }

        InvestmentTransactionType.CUSTODY_TRANSFER,
        InvestmentTransactionType.OWNERSHIP_TRANSFER,
        InvestmentTransactionType.QUOTA_TRANSFER,
        -> {
            if (transaction.direction == InvestmentTransactionDirection.INFLOW) {
                if (quantity == null || unitPrice == null || quantity <= BigDecimal.ZERO) return this
                val newQuantity = this.quantity + quantity
                val newAverageCost =
                    if (this.quantity.signum() == 0) {
                        unitPrice
                    } else {
                        (this.quantity * this.averageCost + quantity * unitPrice)
                            .divide(newQuantity, HOLDING_PERFORMANCE_COST_SCALE, RoundingMode.HALF_UP)
                    }
                copy(quantity = newQuantity, averageCost = newAverageCost)
            } else {
                if (quantity == null || quantity <= BigDecimal.ZERO) return this
                copy(quantity = (this.quantity - quantity).coerceAtLeast(BigDecimal.ZERO))
            }
        }

        else -> this
    }
}

internal fun InvestmentTransaction.isCapitalContributionForPerformance(): Boolean =
    direction == InvestmentTransactionDirection.INFLOW &&
        type in CAPITAL_CONTRIBUTION_TYPES_FOR_PERFORMANCE

internal fun InvestmentTransaction.isCapitalWithdrawalForPerformance(): Boolean =
    direction == InvestmentTransactionDirection.OUTFLOW &&
        type in CAPITAL_WITHDRAWAL_TYPES_FOR_PERFORMANCE

private val CAPITAL_CONTRIBUTION_TYPES_FOR_PERFORMANCE =
    setOf(
        InvestmentTransactionType.PURCHASE,
        InvestmentTransactionType.SUBSCRIPTION,
        InvestmentTransactionType.CUSTODY_TRANSFER,
        InvestmentTransactionType.OWNERSHIP_TRANSFER,
        InvestmentTransactionType.QUOTA_TRANSFER,
    )

private val CAPITAL_WITHDRAWAL_TYPES_FOR_PERFORMANCE =
    setOf(
        InvestmentTransactionType.SALE,
        InvestmentTransactionType.MATURITY,
        InvestmentTransactionType.AMORTIZATION,
        InvestmentTransactionType.CANCELLATION,
        InvestmentTransactionType.CUSTODY_TRANSFER,
        InvestmentTransactionType.OWNERSHIP_TRANSFER,
        InvestmentTransactionType.QUOTA_TRANSFER,
    )
