package io.github.salomax.neotool.financialdata.domain.fixedincome

import java.math.BigDecimal
import java.math.RoundingMode

private val BPS_DIVISOR = BigDecimal("10000")
private val PERCENT_DIVISOR = BigDecimal("100")

fun bpsToDecimalRate(
    bps: Int,
    scale: Int,
): BigDecimal = BigDecimal.valueOf(bps.toLong()).divide(BPS_DIVISOR, scale, RoundingMode.HALF_UP)

fun decimalRateToBps(rate: BigDecimal): Int =
    rate.multiply(BPS_DIVISOR).setScale(0, RoundingMode.HALF_UP).intValueExact()

fun bpsToPercent(
    bps: Int,
    scale: Int = 2,
): BigDecimal = BigDecimal.valueOf(bps.toLong()).divide(PERCENT_DIVISOR, scale, RoundingMode.HALF_UP)
