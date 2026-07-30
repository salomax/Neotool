package io.github.salomax.neotool.banking.service.investment.tax

import io.github.salomax.neotool.common.finance.tax.brazil.fixedincome.BrazilianFixedIncomeTaxRules
import io.github.salomax.neotool.common.finance.tax.engine.TaxRegime
import java.math.BigDecimal

object ComeCotasLongRegime : TaxRegime {
    override fun iofRate(elapsedDays: Int): BigDecimal = BrazilianFixedIncomeTaxRules.iofRate(elapsedDays)

    override fun periodicWithholdingRate(elapsedDays: Int): BigDecimal = BigDecimal("0.15")

    override fun finalRate(elapsedDays: Int): BigDecimal = BrazilianFixedIncomeTaxRules.irRate(elapsedDays)
}
