package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.AnbimaCategory
import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.FundTaxProfile
import io.github.salomax.neotool.banking.domain.investment.Holding
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class ComeCotasEligibilityTest {
    @Test
    fun `mutual fund with long profile is eligible by default`() {
        val holding = baseHolding()
        val asset = baseAsset()

        assertThat(ComeCotasEligibility.isEligible(holding, asset, FundTaxProfile.COME_COTAS_LONG)).isTrue()
    }

    @Test
    fun `override false disables eligible fund`() {
        val holding = baseHolding().copy(comeCotasEnabled = false)
        val asset = baseAsset()

        assertThat(ComeCotasEligibility.isEligible(holding, asset, FundTaxProfile.COME_COTAS_LONG)).isFalse()
    }

    @Test
    fun `manual override forces unknown profile into long come cotas`() {
        val holding = baseHolding().copy(comeCotasEnabled = true)
        val asset = baseAsset().copy(anbimaCategory = null)
        val effective = ComeCotasEligibility.resolveEffectiveTaxProfile(holding, asset, FundTaxProfile.UNKNOWN)

        assertThat(effective).isEqualTo(FundTaxProfile.COME_COTAS_LONG)
        assertThat(ComeCotasEligibility.isEligible(holding, asset, effective)).isTrue()
    }

    @Test
    fun `anbima fallback maps fixed income funds to long come cotas`() {
        val asset = baseAsset().copy(anbimaCategory = AnbimaCategory.RENDA_FIXA)

        assertThat(
            ComeCotasEligibility.resolveTaxProfile(asset, FundTaxProfile.UNKNOWN),
        ).isEqualTo(FundTaxProfile.COME_COTAS_LONG)
    }

    private fun baseHolding(): Holding =
        Holding(
            id = UUID.randomUUID(),
            financialAccountId = UUID.randomUUID(),
            accountId = UUID.randomUUID(),
            assetId = UUID.randomUUID(),
            quantity = BigDecimal.ONE,
        )

    private fun baseAsset(): Asset =
        Asset(
            id = UUID.randomUUID(),
            name = "Fund",
            assetType = AssetType.MUTUAL_FUND,
            fundCnpj = "12345678000199",
        )
}
