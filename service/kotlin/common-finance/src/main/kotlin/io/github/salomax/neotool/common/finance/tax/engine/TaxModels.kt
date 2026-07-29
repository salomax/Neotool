package io.github.salomax.neotool.common.finance.tax.engine

import java.math.BigDecimal
import java.time.LocalDate

data class TaxLot(
    val id: String,
    val quantity: BigDecimal,
    val unitCost: BigDecimal,
    val acquisitionDate: LocalDate,
)

data class TaxLotResult(
    val lotId: String,
    val grossGain: BigDecimal,
    val iofDue: BigDecimal,
    val periodicWithholdingDue: BigDecimal?,
    val finalTaxDue: BigDecimal,
)

data class TaxComputationResult(
    val referenceDate: LocalDate,
    val lots: List<TaxLotResult>,
    val totalIof: BigDecimal,
    val totalPeriodicWithholdingDue: BigDecimal,
    val totalFinalTaxDue: BigDecimal,
)
