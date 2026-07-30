package io.github.salomax.neotool.banking.service.investment.tax

import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.FundTaxProfile
import io.github.salomax.neotool.common.finance.tax.engine.TaxRegime

object TaxRegimeRegistry {
    fun resolve(
        assetType: AssetType,
        isTaxExempt: Boolean,
    ): TaxRegime =
        when (assetType) {
            AssetType.LCI,
            AssetType.LCA,
            AssetType.CRI,
            AssetType.CRA,
            -> TaxExemptRegime

            AssetType.CDB,
            AssetType.RDB,
            AssetType.LC,
            AssetType.LF,
            AssetType.LIG,
            AssetType.TREASURY,
            -> FixedIncomeRegressiveRegime

            AssetType.DEBENTURES ->
                if (isTaxExempt) {
                    TaxExemptRegime
                } else {
                    FixedIncomeRegressiveRegime
                }

            else -> throw IllegalArgumentException("No tax regime registered for asset type $assetType")
        }

    fun resolveFund(
        assetType: AssetType,
        taxProfile: FundTaxProfile,
        comeCotasEnabled: Boolean?,
    ): TaxRegime {
        require(assetType == AssetType.MUTUAL_FUND) {
            "No fund tax regime registered for asset type $assetType"
        }

        val base =
            when (taxProfile) {
                FundTaxProfile.COME_COTAS_LONG -> ComeCotasLongRegime
                FundTaxProfile.COME_COTAS_SHORT -> ComeCotasShortRegime
                FundTaxProfile.VARIABLE_INCOME_15 -> VariableIncomeFundRegime
                FundTaxProfile.PENSION,
                FundTaxProfile.UNKNOWN,
                -> FundNoTaxRegime
            }

        return if (
            comeCotasEnabled == false &&
            (base === ComeCotasLongRegime || base === ComeCotasShortRegime)
        ) {
            NoPeriodicWithholding(base)
        } else {
            base
        }
    }
}
