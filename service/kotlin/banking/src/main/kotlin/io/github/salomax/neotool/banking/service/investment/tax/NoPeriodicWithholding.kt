package io.github.salomax.neotool.banking.service.investment.tax

import io.github.salomax.neotool.common.finance.tax.engine.TaxRegime
import java.math.BigDecimal

class NoPeriodicWithholding(
    private val inner: TaxRegime,
) : TaxRegime {
    override fun iofRate(elapsedDays: Int): BigDecimal = inner.iofRate(elapsedDays)

    override fun periodicWithholdingRate(elapsedDays: Int): BigDecimal? = null

    override fun finalRate(elapsedDays: Int): BigDecimal = inner.finalRate(elapsedDays)
}
