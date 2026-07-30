package io.github.salomax.neotool.banking.service.investment.tax

import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.FundTaxProfile
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.service.investment.ComeCotasEligibility
import io.github.salomax.neotool.common.finance.tax.engine.TaxLot
import io.github.salomax.neotool.common.finance.tax.engine.computeTax
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class TaxRegimeRegistryTest {
    @ParameterizedTest
    @EnumSource(
        value = AssetType::class,
        names = ["LCI", "LCA", "CRI", "CRA"],
    )
    fun `force-exempt asset types ignore the exemption flag`(assetType: AssetType) {
        listOf(false, true).forEach { isTaxExempt ->
            assertThat(TaxRegimeRegistry.resolve(assetType, isTaxExempt))
                .isSameAs(TaxExemptRegime)
        }
    }

    @ParameterizedTest
    @EnumSource(
        value = AssetType::class,
        names = ["CDB", "RDB", "LC", "LF", "LIG", "TREASURY"],
    )
    fun `taxable fixed-income asset types ignore the exemption flag`(assetType: AssetType) {
        listOf(false, true).forEach { isTaxExempt ->
            assertThat(TaxRegimeRegistry.resolve(assetType, isTaxExempt))
                .isSameAs(FixedIncomeRegressiveRegime)
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `debentures trust the exemption flag`(isTaxExempt: Boolean) {
        val expectedRegime =
            if (isTaxExempt) {
                TaxExemptRegime
            } else {
                FixedIncomeRegressiveRegime
            }

        assertThat(TaxRegimeRegistry.resolve(AssetType.DEBENTURES, isTaxExempt))
            .isSameAs(expectedRegime)
    }

    @ParameterizedTest
    @EnumSource(
        value = AssetType::class,
        names = ["STOCK", "MUTUAL_FUND"],
    )
    fun `unsupported asset types are rejected without fall-through`(assetType: AssetType) {
        assertThatThrownBy { TaxRegimeRegistry.resolve(assetType, isTaxExempt = false) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("No tax regime registered for asset type $assetType")
    }

    @Test
    fun `resolveFund maps every fund tax profile across come cotas override states`() {
        val expectations =
            listOf(
                Triple(FundTaxProfile.COME_COTAS_LONG, null, ComeCotasLongRegime::class.java),
                Triple(FundTaxProfile.COME_COTAS_LONG, true, ComeCotasLongRegime::class.java),
                Triple(FundTaxProfile.COME_COTAS_LONG, false, NoPeriodicWithholding::class.java),
                Triple(FundTaxProfile.COME_COTAS_SHORT, null, ComeCotasShortRegime::class.java),
                Triple(FundTaxProfile.COME_COTAS_SHORT, true, ComeCotasShortRegime::class.java),
                Triple(FundTaxProfile.COME_COTAS_SHORT, false, NoPeriodicWithholding::class.java),
                Triple(FundTaxProfile.VARIABLE_INCOME_15, null, VariableIncomeFundRegime::class.java),
                Triple(FundTaxProfile.VARIABLE_INCOME_15, true, VariableIncomeFundRegime::class.java),
                Triple(FundTaxProfile.VARIABLE_INCOME_15, false, VariableIncomeFundRegime::class.java),
                Triple(FundTaxProfile.PENSION, null, FundNoTaxRegime::class.java),
                Triple(FundTaxProfile.PENSION, true, FundNoTaxRegime::class.java),
                Triple(FundTaxProfile.PENSION, false, FundNoTaxRegime::class.java),
                Triple(FundTaxProfile.UNKNOWN, null, FundNoTaxRegime::class.java),
                Triple(FundTaxProfile.UNKNOWN, true, FundNoTaxRegime::class.java),
                Triple(FundTaxProfile.UNKNOWN, false, FundNoTaxRegime::class.java),
            )

        expectations.forEach { (taxProfile, comeCotasEnabled, expectedType) ->
            val resolved = TaxRegimeRegistry.resolveFund(AssetType.MUTUAL_FUND, taxProfile, comeCotasEnabled)

            assertThat(resolved)
                .describedAs("profile=%s override=%s", taxProfile, comeCotasEnabled)
                .isInstanceOf(expectedType)
        }
    }

    @Test
    fun `resolveFund rejects every non mutual fund asset type`() {
        AssetType.entries
            .filterNot { it == AssetType.MUTUAL_FUND }
            .forEach { assetType ->
                assertThatThrownBy {
                    TaxRegimeRegistry.resolveFund(assetType, FundTaxProfile.COME_COTAS_LONG, null)
                }.isInstanceOf(IllegalArgumentException::class.java)
                    .hasMessage("No fund tax regime registered for asset type $assetType")
            }
    }

    @Test
    fun `resolveEffectiveTaxProfile promotes variable income override before registry resolution`() {
        val asset =
            Asset(
                id = UUID.randomUUID(),
                name = "Fund",
                assetType = AssetType.MUTUAL_FUND,
                fundCnpj = "12345678000199",
            )
        val holding =
            Holding(
                id = UUID.randomUUID(),
                financialAccountId = UUID.randomUUID(),
                accountId = UUID.randomUUID(),
                assetId = UUID.randomUUID(),
                quantity = BigDecimal.ONE,
                comeCotasEnabled = true,
            )

        val effectiveProfile =
            ComeCotasEligibility.resolveEffectiveTaxProfile(
                holding = holding,
                asset = asset,
                snapshotTaxProfile = FundTaxProfile.VARIABLE_INCOME_15,
            )
        val resolved = TaxRegimeRegistry.resolveFund(AssetType.MUTUAL_FUND, effectiveProfile, holding.comeCotasEnabled)

        assertThat(effectiveProfile).isEqualTo(FundTaxProfile.COME_COTAS_LONG)
        assertThat(resolved).isSameAs(ComeCotasLongRegime)
    }

    @Test
    fun `come cotas disabled regime never emits periodic withholding`() {
        val referenceDate = LocalDate.of(2026, 11, 30)
        val regime = TaxRegimeRegistry.resolveFund(AssetType.MUTUAL_FUND, FundTaxProfile.COME_COTAS_LONG, false)
        listOf(1, 180, 181, 360, 900).forEach { elapsedDays ->
            assertThat(regime.periodicWithholdingRate(elapsedDays))
                .describedAs("elapsedDays=%s", elapsedDays)
                .isNull()
        }

        // AC1 at #540 scope: this issue owns regime resolution. Once callers migrate onto this
        // wrapped regime, prepaid come-cotas is structurally disabled regardless of lot data.
        val result =
            computeTax(
                lots =
                    listOf(
                        TaxLot(
                            id = "lot",
                            quantity = BigDecimal("100.00000000"),
                            unitCost = BigDecimal("1.00"),
                            acquisitionDate = LocalDate.of(2026, 1, 2),
                        ),
                    ),
                regime = regime,
                referenceDate = referenceDate,
                navAt = { BigDecimal("1.20") },
            )

        assertThat(result.totalPeriodicWithholdingDue).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(result.totalFinalTaxDue).isGreaterThan(BigDecimal.ZERO)
    }
}
