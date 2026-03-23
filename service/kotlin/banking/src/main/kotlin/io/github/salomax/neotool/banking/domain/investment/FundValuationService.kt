package io.github.salomax.neotool.banking.domain.investment

import jakarta.inject.Singleton
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Month
import java.time.temporal.ChronoUnit

data class FundValuationResult(
    val grossValue: BigDecimal?,
    val incomeTax: BigDecimal?,
    val iof: BigDecimal?,
    val netValue: BigDecimal?,
    val valuationSource: ValuationSource,
    val valuationReferenceDate: LocalDate?,
)

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
            -> FundValuationResult(
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

                val withholdingPaid = transactions
                    .filter { it.type == InvestmentTransactionType.WITHHOLDING_TAX }
                    .fold(BigDecimal.ZERO) { acc, tx ->
                        acc + (tx.incomeTax ?: tx.grossValue)
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
        val checkpointRate = when (taxProfile) {
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
        transactions.sortedWith(compareBy<InvestmentTransaction> { it.tradeDate }.thenBy { it.createdAt }).forEach { tx ->
            when {
                tx.type in setOf(
                    InvestmentTransactionType.PURCHASE,
                    InvestmentTransactionType.SUBSCRIPTION,
                ) && tx.direction == InvestmentTransactionDirection.INFLOW && tx.quantity != null && tx.unitPrice != null -> {
                    openLots.addLast(OpenLot(tx.quantity, tx.unitPrice, tx.tradeDate))
                }

                tx.type in setOf(
                    InvestmentTransactionType.SALE,
                    InvestmentTransactionType.MATURITY,
                ) && tx.quantity != null -> {
                    var remaining = tx.quantity ?: BigDecimal.ZERO
                    while (remaining > BigDecimal.ZERO && openLots.isNotEmpty()) {
                        val lot = openLots.removeFirst()
                        if (lot.quantity <= remaining) {
                            remaining = remaining.subtract(lot.quantity, mathContext)
                        } else {
                            openLots.addFirst(lot.copy(quantity = lot.quantity.subtract(remaining, mathContext)))
                            remaining = BigDecimal.ZERO
                        }
                    }
                }

                tx.type in setOf(
                    InvestmentTransactionType.CUSTODY_TRANSFER,
                    InvestmentTransactionType.OWNERSHIP_TRANSFER,
                    InvestmentTransactionType.QUOTA_TRANSFER,
                ) && tx.quantity != null -> {
                    if (tx.direction == InvestmentTransactionDirection.INFLOW && tx.unitPrice != null) {
                        openLots.addLast(OpenLot(tx.quantity, tx.unitPrice, tx.tradeDate))
                    } else if (tx.direction == InvestmentTransactionDirection.OUTFLOW) {
                        var remaining = tx.quantity ?: BigDecimal.ZERO
                        while (remaining > BigDecimal.ZERO && openLots.isNotEmpty()) {
                            val lot = openLots.removeFirst()
                            if (lot.quantity <= remaining) {
                                remaining = remaining.subtract(lot.quantity, mathContext)
                            } else {
                                openLots.addFirst(lot.copy(quantity = lot.quantity.subtract(remaining, mathContext)))
                                remaining = BigDecimal.ZERO
                            }
                        }
                    }
                }
            }
        }
        return openLots.toList()
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
    )
}
