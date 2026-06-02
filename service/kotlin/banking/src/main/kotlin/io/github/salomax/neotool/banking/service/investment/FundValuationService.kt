package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.FundMarketSnapshot
import io.github.salomax.neotool.banking.domain.investment.FundTaxProfile
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.model.domain.investment.policy.BrazilianBusinessCalendar
import io.github.salomax.neotool.banking.model.domain.investment.policy.BrazilianFixedIncomeTaxRules
import io.github.salomax.neotool.banking.model.dto.investment.FundValuationResult
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Month
import java.time.temporal.ChronoUnit

@Singleton
class FundValuationService {
    private val mathContext = MathContext(12, RoundingMode.HALF_UP)

    fun estimate(
        holding: Holding,
        snapshot: FundMarketSnapshot,
        transactions: List<InvestmentTransaction>,
    ): FundValuationResult {
        val nav = snapshot.latestNav ?: return unavailable()
        val grossValue = holding.quantity.multiply(nav.closePrice, mathContext)

        return when (snapshot.taxProfile) {
            FundTaxProfile.UNKNOWN,
            FundTaxProfile.PENSION,
            ->
                FundValuationResult(
                    grossValue = grossValue,
                    incomeTax = null,
                    iof = null,
                    netValue = null,
                    valuationSource = ValuationSource.MARKET_DATA,
                    valuationReferenceDate = nav.date,
                )

            else -> {
                val openLots = reconstructOpenLots(transactions)
                if (openLots.isEmpty()) {
                    return FundValuationResult(
                        grossValue = grossValue,
                        incomeTax = null,
                        iof = null,
                        netValue = grossValue,
                        valuationSource = ValuationSource.MARKET_DATA,
                        valuationReferenceDate = nav.date,
                    )
                }

                val withholdingPaid =
                    openLots.fold(BigDecimal.ZERO) { acc, lot ->
                        acc.add(lot.withholdingPaid, mathContext)
                    }

                var totalIof = BigDecimal.ZERO
                var totalIrDue = BigDecimal.ZERO

                openLots.forEach { lot ->
                    val lotGross = lot.quantity.multiply(nav.closePrice, mathContext)
                    val costBasis = lot.quantity.multiply(lot.unitPrice, mathContext)
                    val gain = lotGross.subtract(costBasis, mathContext)
                    if (gain <= BigDecimal.ZERO) {
                        return@forEach
                    }
                    val calendarDays = calendarDaysBetween(lot.tradeDate, nav.date)
                    val iofAmount = BrazilianFixedIncomeTaxRules.computeIof(gain, calendarDays)
                    val taxableGain = (gain - iofAmount).max(BigDecimal.ZERO)
                    val finalIr = computeFinalIr(snapshot.taxProfile, taxableGain, calendarDays)
                    val prepaidComeCotas = estimateComeCotas(snapshot.taxProfile, gain, lot.tradeDate, nav.date)
                    val residualIr = (finalIr - prepaidComeCotas).max(BigDecimal.ZERO)
                    totalIof = totalIof.add(iofAmount, mathContext)
                    totalIrDue = totalIrDue.add(residualIr, mathContext)
                }

                val incomeTax = (totalIrDue - withholdingPaid).max(BigDecimal.ZERO)
                val netValue = grossValue.subtract(totalIof, mathContext).subtract(incomeTax, mathContext)

                FundValuationResult(
                    grossValue = grossValue,
                    incomeTax = incomeTax,
                    iof = totalIof,
                    netValue = netValue,
                    valuationSource = ValuationSource.MARKET_DATA,
                    valuationReferenceDate = nav.date,
                )
            }
        }
    }

    private fun computeFinalIr(
        taxProfile: FundTaxProfile,
        taxableGain: BigDecimal,
        calendarDays: Int,
    ): BigDecimal =
        when (taxProfile) {
            FundTaxProfile.COME_COTAS_SHORT ->
                taxableGain.multiply(
                    if (calendarDays <= 180) BigDecimal("0.225") else BigDecimal("0.20"),
                    mathContext,
                )

            FundTaxProfile.COME_COTAS_LONG ->
                BrazilianFixedIncomeTaxRules.computeIr(taxableGain, calendarDays, isTaxExempt = false)

            FundTaxProfile.VARIABLE_INCOME_15 ->
                taxableGain.multiply(BigDecimal("0.15"), mathContext)

            else -> BigDecimal.ZERO
        }

    private fun estimateComeCotas(
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

    private fun checkpointDatesBetween(
        tradeDate: LocalDate,
        referenceDate: LocalDate,
    ): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        for (year in tradeDate.year..referenceDate.year) {
            val may = BrazilianBusinessCalendar.lastBusinessDay(year, Month.MAY)
            val november = BrazilianBusinessCalendar.lastBusinessDay(year, Month.NOVEMBER)
            if (may.isAfter(tradeDate) && !may.isAfter(referenceDate)) dates += may
            if (november.isAfter(tradeDate) && !november.isAfter(referenceDate)) dates += november
        }
        return dates.sorted()
    }

    private fun reconstructOpenLots(transactions: List<InvestmentTransaction>): List<OpenLot> {
        val openLots = ArrayDeque<OpenLot>()
        transactions.sortedWith(compareBy<InvestmentTransaction> { it.tradeDate }.thenBy { it.createdAt }).forEach {
                tx ->
            when {
                tx.type in
                    setOf(
                        InvestmentTransactionType.PURCHASE,
                        InvestmentTransactionType.SUBSCRIPTION,
                    ) &&
                    tx.direction == InvestmentTransactionDirection.INFLOW &&
                    tx.quantity != null &&
                    tx.unitPrice != null -> {
                    openLots.addLast(OpenLot(tx.quantity, tx.unitPrice, tx.tradeDate))
                }

                tx.type in
                    setOf(
                        InvestmentTransactionType.SALE,
                        InvestmentTransactionType.MATURITY,
                    ) && tx.quantity != null -> {
                    consumeLots(openLots, tx.quantity ?: BigDecimal.ZERO)
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
                        consumeLots(openLots, tx.quantity ?: BigDecimal.ZERO)
                    }
                }

                tx.type == InvestmentTransactionType.WITHHOLDING_TAX -> {
                    allocateWithholding(
                        openLots = openLots,
                        amount = tx.incomeTax ?: tx.grossValue,
                    )
                }
            }
        }
        return openLots.toList()
    }

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
        quantity: BigDecimal,
        totalQuantity: BigDecimal,
    ): BigDecimal {
        if (amount == BigDecimal.ZERO || quantity <= BigDecimal.ZERO || totalQuantity <= BigDecimal.ZERO) {
            return BigDecimal.ZERO
        }
        return amount.multiply(quantity, mathContext).divide(totalQuantity, mathContext)
    }

    private fun calendarDaysBetween(
        start: LocalDate,
        end: LocalDate,
    ): Int {
        if (!end.isAfter(start)) return 0
        return ChronoUnit.DAYS.between(start, end).toInt()
    }

    private fun unavailable(): FundValuationResult =
        FundValuationResult(
            grossValue = null,
            incomeTax = null,
            iof = null,
            netValue = null,
            valuationSource = ValuationSource.UNAVAILABLE,
            valuationReferenceDate = null,
        )

    private data class OpenLot(
        val quantity: BigDecimal,
        val unitPrice: BigDecimal,
        val tradeDate: LocalDate,
        val withholdingPaid: BigDecimal = BigDecimal.ZERO,
    )
}
