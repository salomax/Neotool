package io.github.salomax.neotool.common.finance.tax.engine

import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun computeTax(
    lots: List<TaxLot>,
    regime: TaxRegime,
    referenceDate: LocalDate,
    navAt: (LocalDate) -> BigDecimal?,
): TaxComputationResult {
    val nav = navAt(referenceDate) ?: return emptyResult(referenceDate)
    val lotResults =
        lots
            .mapNotNull { lot -> computeLotTax(lot, regime, referenceDate, nav) }
            .sortedBy(TaxLotResult::lotId)

    return TaxComputationResult(
        referenceDate = referenceDate,
        lots = lotResults,
        totalIof = lotResults.sumOf(TaxLotResult::iofDue),
        totalPeriodicWithholdingDue =
            lotResults.sumOf { result ->
                result.periodicWithholdingDue ?: BigDecimal.ZERO
            },
        totalFinalTaxDue = lotResults.sumOf(TaxLotResult::finalTaxDue),
    )
}

private fun computeLotTax(
    lot: TaxLot,
    regime: TaxRegime,
    referenceDate: LocalDate,
    nav: BigDecimal,
): TaxLotResult? {
    val grossGain = lot.quantity.multiply(nav.subtract(lot.unitCost))
    if (grossGain <= BigDecimal.ZERO) return null

    val elapsedDays =
        ChronoUnit.DAYS
            .between(lot.acquisitionDate, referenceDate)
            .toInt()
            .coerceAtLeast(0)
    val iofDue = grossGain.multiply(regime.iofRate(elapsedDays))
    val taxableGain = grossGain.subtract(iofDue).max(BigDecimal.ZERO)
    val periodicWithholdingDue =
        regime.periodicWithholdingRate(elapsedDays)?.let(taxableGain::multiply)
    val finalTaxDue = taxableGain.multiply(regime.finalRate(elapsedDays))

    return TaxLotResult(
        lotId = lot.id,
        grossGain = grossGain,
        iofDue = iofDue,
        periodicWithholdingDue = periodicWithholdingDue,
        finalTaxDue = finalTaxDue,
    )
}

private fun emptyResult(referenceDate: LocalDate) =
    TaxComputationResult(
        referenceDate = referenceDate,
        lots = emptyList(),
        totalIof = BigDecimal.ZERO,
        totalPeriodicWithholdingDue = BigDecimal.ZERO,
        totalFinalTaxDue = BigDecimal.ZERO,
    )
