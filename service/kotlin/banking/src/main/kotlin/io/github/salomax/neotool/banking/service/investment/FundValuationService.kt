package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.FundMarketSnapshot
import io.github.salomax.neotool.banking.domain.investment.FundTaxProfile
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.model.dto.investment.FundValuationResult
import io.github.salomax.neotool.common.finance.tax.brazil.fixedincome.BrazilianFixedIncomeTaxRules
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate

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
                    val prepaidComeCotas =
                        (
                            ComeCotasSupport.estimateProjectedComeCotas(
                                taxProfile = snapshot.taxProfile,
                                gain = gain,
                                tradeDate = lot.tradeDate,
                                referenceDate = nav.date,
                            ) - lot.withholdingPaid
                        ).max(BigDecimal.ZERO)
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

    private fun reconstructOpenLots(transactions: List<InvestmentTransaction>): List<OpenLot> =
        ComeCotasSupport.reconstructOpenLots(transactions)

    private fun calendarDaysBetween(
        start: LocalDate,
        end: LocalDate,
    ): Int = ComeCotasSupport.calendarDaysBetween(start, end)

    private fun unavailable(): FundValuationResult =
        FundValuationResult(
            grossValue = null,
            incomeTax = null,
            iof = null,
            netValue = null,
            valuationSource = ValuationSource.UNAVAILABLE,
            valuationReferenceDate = null,
        )
}
