package io.github.salomax.neotool.banking.service.investment.recalculation

import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.domain.investment.RecordInvestmentTransactionCommand
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Single source of truth for how a [Holding]'s quantity and average cost evolve from a transaction.
 * Used by [io.github.salomax.neotool.banking.service.investment.InvestmentTransactionService] (live writes)
 * and [HistoricalHoldingTimeline] (performance recalculation replay).
 *
 * Callers that record new transactions must validate inputs (e.g. require quantity on SALE) before calling
 * [evolve]; this function is lenient where persisted history may be incomplete (e.g. skips purchase when qty is null).
 */
data class PositionEvolutionInput(
    val type: InvestmentTransactionType,
    val direction: InvestmentTransactionDirection,
    val quantity: BigDecimal?,
    val unitPrice: BigDecimal?,
) {
    companion object {
        fun from(transaction: InvestmentTransaction): PositionEvolutionInput =
            PositionEvolutionInput(
                type = transaction.type,
                direction = transaction.direction,
                quantity = transaction.quantity,
                unitPrice = transaction.unitPrice,
            )

        fun from(command: RecordInvestmentTransactionCommand): PositionEvolutionInput =
            PositionEvolutionInput(
                type = command.type,
                direction = command.direction,
                quantity = command.quantity,
                unitPrice = command.unitPrice,
            )
    }
}

object HoldingPositionEvolution {
    const val COST_SCALE: Int = 8

    private val TRANSFER_TYPES: Set<InvestmentTransactionType> =
        setOf(
            InvestmentTransactionType.CUSTODY_TRANSFER,
            InvestmentTransactionType.OWNERSHIP_TRANSFER,
            InvestmentTransactionType.QUOTA_TRANSFER,
        )

    fun evolve(state: Holding, input: PositionEvolutionInput): Holding {
        val qty = input.quantity
        val price = input.unitPrice
        return when (input.type) {
            InvestmentTransactionType.PURCHASE,
            InvestmentTransactionType.SUBSCRIPTION,
            -> {
                if (qty == null || price == null) return state
                val newQty = state.quantity + qty
                val newCost =
                    if (state.quantity.signum() == 0) {
                        price
                    } else {
                        (state.quantity * state.averageCost + qty * price).divide(newQty, COST_SCALE, RoundingMode.HALF_UP)
                    }
                state.copy(quantity = newQty, averageCost = newCost)
            }

            InvestmentTransactionType.SALE,
            InvestmentTransactionType.MATURITY,
            InvestmentTransactionType.CUSTODY_TRANSFER,
            InvestmentTransactionType.OWNERSHIP_TRANSFER,
            InvestmentTransactionType.QUOTA_TRANSFER,
            -> {
                val isInflowTransfer =
                    input.type in TRANSFER_TYPES &&
                        input.direction == InvestmentTransactionDirection.INFLOW
                val reduction = if (isInflowTransfer) BigDecimal.ZERO else qty ?: state.quantity
                if (isInflowTransfer && qty != null && price != null) {
                    val newQty = state.quantity + qty
                    val newCost =
                        if (state.quantity.signum() == 0) {
                            price
                        } else {
                            (state.quantity * state.averageCost + qty * price).divide(newQty, COST_SCALE, RoundingMode.HALF_UP)
                        }
                    state.copy(quantity = newQty, averageCost = newCost)
                } else {
                    state.copy(quantity = (state.quantity - reduction).max(BigDecimal.ZERO))
                }
            }

            InvestmentTransactionType.STOCK_SPLIT,
            InvestmentTransactionType.BONUS_SHARES,
            -> {
                if (qty == null || qty <= BigDecimal.ZERO) return state
                val newQty = state.quantity + qty
                val newCost =
                    if (newQty.signum() == 0) BigDecimal.ZERO
                    else (state.quantity * state.averageCost).divide(newQty, COST_SCALE, RoundingMode.HALF_UP)
                state.copy(quantity = newQty, averageCost = newCost)
            }

            InvestmentTransactionType.REVERSE_SPLIT -> {
                if (qty == null || qty <= BigDecimal.ZERO) return state
                val newQty = (state.quantity - qty).max(BigDecimal.ZERO)
                val newCost =
                    if (newQty.signum() == 0) BigDecimal.ZERO
                    else (state.quantity * state.averageCost).divide(newQty, COST_SCALE, RoundingMode.HALF_UP)
                state.copy(quantity = newQty, averageCost = newCost)
            }

            else -> state
        }
    }
}
