package io.github.salomax.neotool.financialdata.domain

import java.math.BigDecimal

/**
 * Domain object representing a value for a specific quarter.
 */
data class QuarterValue(
    /**
     * Quarter in "YYYYMM" format
     */
    val quarter: String,
    /**
     * Value for this quarter (saldo from bacen_bank_report_data)
     */
    val value: BigDecimal?,
)
