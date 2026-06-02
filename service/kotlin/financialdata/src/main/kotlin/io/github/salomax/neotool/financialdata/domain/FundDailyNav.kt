package io.github.salomax.neotool.financialdata.domain

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Domain object for a single daily NAV (Net Asset Value) record for an investment fund.
 */
data class FundDailyNav(
    val date: LocalDate,
    val closePrice: BigDecimal,
    // volume column stores total net assets (patrimônio líquido) for CVM funds
    val totalNetAssets: BigDecimal?,
)
