package io.github.salomax.neotool.banking.service.investment.tax

import io.github.salomax.neotool.banking.domain.investment.AssetType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

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
}
