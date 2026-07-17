package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.FundTaxProfile
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.model.domain.investment.policy.BrazilianBusinessCalendar
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Month
import java.time.temporal.ChronoUnit

internal data class OpenLot(
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val tradeDate: LocalDate,
    val withholdingPaid: BigDecimal = BigDecimal.ZERO,
)

internal object ComeCotasSupport {
    private val mathContext = MathContext(12, RoundingMode.HALF_UP)
    const val RECONCILIATION_WINDOW_DAYS: Long = 10

    fun checkpointDatesBetween(
        tradeDate: LocalDate,
        referenceDate: LocalDate,
    ): List<LocalDate> {
        if (tradeDate.isAfter(referenceDate)) return emptyList()
        val dates = mutableListOf<LocalDate>()
        for (year in tradeDate.year..referenceDate.year) {
            val may = checkpointDateForMonth(year, Month.MAY)
            val november = checkpointDateForMonth(year, Month.NOVEMBER)
            if (may.isAfter(tradeDate) && !may.isAfter(referenceDate)) dates += may
            if (november.isAfter(tradeDate) && !november.isAfter(referenceDate)) dates += november
        }
        return dates.sorted()
    }

    fun checkpointDateForMonth(
        year: Int,
        month: Month,
    ): LocalDate = BrazilianBusinessCalendar.lastBusinessDay(year, month)

    fun checkpointWindow(
        checkpointDate: LocalDate,
        windowDays: Long = RECONCILIATION_WINDOW_DAYS,
    ): ClosedRange<LocalDate> = checkpointDate.minusDays(windowDays)..checkpointDate.plusDays(windowDays)

    fun estimateProjectedComeCotas(
        taxProfile: FundTaxProfile,
        gain: BigDecimal,
        tradeDate: LocalDate,
        referenceDate: LocalDate,
    ): BigDecimal {
        val checkpointRate =
            when (taxProfile) {
                FundTaxProfile.COME_COTAS_LONG -> BigDecimal("0.15")
                FundTaxProfile.COME_COTAS_SHORT -> BigDecimal("0.20")
                else -> return BigDecimal.ZERO
            }

        val totalDays = calendarDaysBetween(tradeDate, referenceDate)
        if (totalDays <= 0) return BigDecimal.ZERO

        var prepaid = BigDecimal.ZERO
        var lastAccruedGain = BigDecimal.ZERO
        checkpointDatesBetween(tradeDate, referenceDate).forEach { checkpoint ->
            val elapsed = calendarDaysBetween(tradeDate, checkpoint)
            val accruedGain =
                gain.multiply(BigDecimal(elapsed), mathContext)
                    .divide(BigDecimal(totalDays), mathContext)
            val incrementalGain = (accruedGain - lastAccruedGain).max(BigDecimal.ZERO)
            prepaid = prepaid.add(incrementalGain.multiply(checkpointRate, mathContext), mathContext)
            lastAccruedGain = accruedGain
        }
        return prepaid
    }

    fun reconstructOpenLots(transactions: List<InvestmentTransaction>): List<OpenLot> {
        val openLots = ArrayDeque<OpenLot>()
        transactions.sortedWith(
            compareBy<InvestmentTransaction> {
                it.tradeDate
            }.thenBy { it.createdAt }.thenBy { it.id.toString() },
        ).forEach {
                tx ->
            when {
                tx.type in setOf(InvestmentTransactionType.PURCHASE, InvestmentTransactionType.SUBSCRIPTION) &&
                    tx.direction == InvestmentTransactionDirection.INFLOW &&
                    tx.quantity != null &&
                    tx.unitPrice != null -> {
                    openLots.addLast(OpenLot(tx.quantity, tx.unitPrice, tx.tradeDate))
                }

                tx.type in setOf(InvestmentTransactionType.SALE, InvestmentTransactionType.MATURITY) &&
                    tx.quantity != null -> {
                    consumeLots(openLots, tx.quantity)
                }

                tx.type in
                    setOf(
                        InvestmentTransactionType.CUSTODY_TRANSFER,
                        InvestmentTransactionType.OWNERSHIP_TRANSFER,
                        InvestmentTransactionType.QUOTA_TRANSFER,
                    ) && tx.quantity != null -> {
                    if (tx.direction == InvestmentTransactionDirection.INFLOW && tx.unitPrice != null) {
                        openLots.addLast(OpenLot(tx.quantity, tx.unitPrice, tx.tradeDate))
                    } else if (tx.direction == InvestmentTransactionDirection.OUTFLOW) {
                        consumeLots(openLots, tx.quantity)
                    }
                }

                tx.type == InvestmentTransactionType.WITHHOLDING_TAX -> {
                    allocateWithholding(openLots, tx.incomeTax ?: tx.grossValue ?: BigDecimal.ZERO)
                }
            }
        }
        return openLots.toList()
    }

    fun calendarDaysBetween(
        from: LocalDate,
        to: LocalDate,
    ): Int = ChronoUnit.DAYS.between(from, to).toInt().coerceAtLeast(0)

    private fun consumeLots(
        openLots: ArrayDeque<OpenLot>,
        quantityToConsume: BigDecimal,
    ) {
        var remaining = quantityToConsume
        while (remaining > BigDecimal.ZERO && openLots.isNotEmpty()) {
            val lot = openLots.removeFirst()
            if (lot.quantity <= remaining) {
                remaining = remaining.subtract(lot.quantity, mathContext)
            } else {
                val remainingQuantity = lot.quantity.subtract(remaining, mathContext)
                val remainingWithholding = prorateAmount(lot.withholdingPaid, remainingQuantity, lot.quantity)
                openLots.addFirst(
                    lot.copy(
                        quantity = remainingQuantity,
                        withholdingPaid = remainingWithholding,
                    ),
                )
                remaining = BigDecimal.ZERO
            }
        }
    }

    private fun allocateWithholding(
        openLots: ArrayDeque<OpenLot>,
        amount: BigDecimal,
    ) {
        if (amount <= BigDecimal.ZERO || openLots.isEmpty()) return

        val lots = openLots.toList()
        val totalQuantity = lots.fold(BigDecimal.ZERO) { acc, lot -> acc.add(lot.quantity, mathContext) }
        if (totalQuantity <= BigDecimal.ZERO) return

        openLots.clear()
        var allocated = BigDecimal.ZERO
        lots.forEachIndexed { index, lot ->
            val share =
                if (index == lots.lastIndex) {
                    amount.subtract(allocated, mathContext)
                } else {
                    prorateAmount(amount, lot.quantity, totalQuantity)
                }
            allocated = allocated.add(share, mathContext)
            openLots.addLast(
                lot.copy(
                    withholdingPaid = lot.withholdingPaid.add(share, mathContext),
                ),
            )
        }
    }

    private fun prorateAmount(
        amount: BigDecimal,
        part: BigDecimal,
        total: BigDecimal,
    ): BigDecimal {
        if (amount == BigDecimal.ZERO || part == BigDecimal.ZERO || total == BigDecimal.ZERO) return BigDecimal.ZERO
        return amount.multiply(part, mathContext).divide(total, mathContext)
    }
}
