package io.github.salomax.neotool.banking.service.investment

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

    /**
     * Inclusive boundary on purpose: edit/delete reprocessing starts at the first checkpoint
     * that may still be affected by a transaction recorded exactly on checkpoint day.
     */
    fun earliestCheckpointOnOrAfter(tradeDate: LocalDate): LocalDate {
        val sameYearCandidates =
            listOf(
                checkpointDateForMonth(tradeDate.year, Month.MAY),
                checkpointDateForMonth(tradeDate.year, Month.NOVEMBER),
            )
        return sameYearCandidates.firstOrNull { !it.isBefore(tradeDate) }
            ?: checkpointDateForMonth(tradeDate.year + 1, Month.MAY)
    }

    fun checkpointWindow(
        checkpointDate: LocalDate,
        windowDays: Long = RECONCILIATION_WINDOW_DAYS,
    ): ClosedRange<LocalDate> = checkpointDate.minusDays(windowDays)..checkpointDate.plusDays(windowDays)

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

                tx.type == InvestmentTransactionType.WITHHOLDING_TAX && tx.quantity != null -> {
                    consumeLots(openLots, tx.quantity)
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
                openLots.addFirst(lot.copy(quantity = remainingQuantity))
                remaining = BigDecimal.ZERO
            }
        }
    }
}
