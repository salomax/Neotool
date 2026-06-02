package io.github.salomax.neotool.banking.model.dto.investment

import io.github.salomax.neotool.banking.service.investment.ValuationSource
import java.math.BigDecimal
import java.time.LocalDate

data class HoldingValuationResult(
    val grossValue: BigDecimal?,
    val incomeTax: BigDecimal?,
    val iof: BigDecimal?,
    val netValue: BigDecimal?,
    val valuationSource: ValuationSource,
    val valuationReferenceDate: LocalDate?,
)

data class FundValuationResult(
    val grossValue: BigDecimal?,
    val incomeTax: BigDecimal?,
    val iof: BigDecimal?,
    val netValue: BigDecimal?,
    val valuationSource: ValuationSource,
    val valuationReferenceDate: LocalDate?,
)
