package io.github.salomax.neotool.common.finance.tax.engine

import java.math.BigDecimal

/**
 * Supplies the rates used to tax an investment lot after a given number of calendar days.
 */
interface TaxRegime {
    fun iofRate(elapsedDays: Int): BigDecimal = BigDecimal.ZERO

    fun periodicWithholdingRate(elapsedDays: Int): BigDecimal?

    fun finalRate(elapsedDays: Int): BigDecimal
}
