package io.github.salomax.neotool.financialdata.test.unit.repo

import io.github.salomax.neotool.financialdata.domain.FundTaxProfile
import io.github.salomax.neotool.financialdata.repo.FundTaxProfileClassifier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("FundTaxProfileClassifier")
class FundTaxProfileClassifierTest {
    @Test
    fun `maps fixed income curto prazo funds to short come cotas`() {
        val profile =
            FundTaxProfileClassifier.taxProfileFor(
                assetType = "FIXED_INCOME_FUND",
                fundName = "ALFA FUNDO DE INVESTIMENTO RENDA FIXA CURTO PRAZO",
            )

        assertThat(profile).isEqualTo(FundTaxProfile.COME_COTAS_SHORT)
    }

    @Test
    fun `maps multimarket funds without short marker to long come cotas`() {
        val profile =
            FundTaxProfileClassifier.taxProfileFor(
                assetType = "MULTI_MARKET_FUND",
                fundName = "OMEGA FUNDO DE INVESTIMENTO MULTIMERCADO LONG BIAS",
            )

        assertThat(profile).isEqualTo(FundTaxProfile.COME_COTAS_LONG)
    }

    @Test
    fun `classifies incentivized fixed income funds as unknown to keep them out of come cotas automation`() {
        val profile =
            FundTaxProfileClassifier.taxProfileFor(
                assetType = "FIXED_INCOME_FUND",
                fundName = "BETA FI-INFRA RENDA FIXA DEBÊNTURES INCENTIVADAS",
            )

        assertThat(profile).isEqualTo(FundTaxProfile.UNKNOWN)
    }

    @Test
    fun `maps equity and pension funds to their dedicated tax profiles`() {
        assertThat(
            FundTaxProfileClassifier.taxProfileFor(
                assetType = "EQUITY_FUND",
                fundName = "GAMMA FIA",
            ),
        ).isEqualTo(FundTaxProfile.VARIABLE_INCOME_15)

        assertThat(
            FundTaxProfileClassifier.taxProfileFor(
                assetType = "PGBL",
                fundName = "DELTA PGBL",
            ),
        ).isEqualTo(FundTaxProfile.PENSION)
    }
}
