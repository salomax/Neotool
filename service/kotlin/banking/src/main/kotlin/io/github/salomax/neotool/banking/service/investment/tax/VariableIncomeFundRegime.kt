package io.github.salomax.neotool.banking.service.investment.tax

import io.github.salomax.neotool.common.finance.tax.brazil.fixedincome.BrazilianFixedIncomeTaxRules
import io.github.salomax.neotool.common.finance.tax.engine.TaxRegime
import java.math.BigDecimal

object VariableIncomeFundRegime : TaxRegime {
    private val finalRate = BigDecimal("0.15")

    override fun iofRate(elapsedDays: Int): BigDecimal = BrazilianFixedIncomeTaxRules.iofRate(elapsedDays)

    override fun periodicWithholdingRate(elapsedDays: Int): BigDecimal? = null

    override fun finalRate(elapsedDays: Int): BigDecimal = finalRate
}
