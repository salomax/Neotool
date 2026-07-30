package io.github.salomax.neotool.banking.service.investment.tax

import io.github.salomax.neotool.common.finance.tax.brazil.fixedincome.BrazilianFixedIncomeTaxRules
import io.github.salomax.neotool.common.finance.tax.engine.TaxRegime
import java.math.BigDecimal

object ComeCotasShortRegime : TaxRegime {
    override fun iofRate(elapsedDays: Int): BigDecimal = BrazilianFixedIncomeTaxRules.iofRate(elapsedDays)

    override fun periodicWithholdingRate(elapsedDays: Int): BigDecimal = BigDecimal("0.20")

    override fun finalRate(elapsedDays: Int): BigDecimal =
        if (elapsedDays <= 180) {
            BigDecimal("0.225")
        } else {
            BigDecimal("0.20")
        }
}
