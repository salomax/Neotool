package io.github.salomax.neotool.banking.service.investment.tax

import io.github.salomax.neotool.banking.domain.investment.AssetType
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
}
