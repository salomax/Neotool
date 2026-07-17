package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.AnbimaCategory
import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.FundTaxProfile
import io.github.salomax.neotool.banking.domain.investment.Holding

object ComeCotasEligibility {
    fun resolveEffectiveTaxProfile(
        holding: Holding,
        asset: Asset,
        snapshotTaxProfile: FundTaxProfile?,
    ): FundTaxProfile {
        val resolved = resolveTaxProfile(asset, snapshotTaxProfile)
        return if (holding.comeCotasEnabled == true && resolved == FundTaxProfile.UNKNOWN) {
            FundTaxProfile.COME_COTAS_LONG
        } else {
            resolved
        }
    }

    fun resolveTaxProfile(
        asset: Asset,
        snapshotTaxProfile: FundTaxProfile?,
    ): FundTaxProfile {
        val direct = snapshotTaxProfile ?: FundTaxProfile.UNKNOWN
        if (direct != FundTaxProfile.UNKNOWN) return direct
        return when (asset.anbimaCategory) {
            AnbimaCategory.RENDA_FIXA,
            AnbimaCategory.MULTIMERCADO,
            -> FundTaxProfile.COME_COTAS_LONG

            AnbimaCategory.ACOES -> FundTaxProfile.VARIABLE_INCOME_15
            else -> FundTaxProfile.UNKNOWN
        }
    }

    fun isEligible(
        holding: Holding,
        asset: Asset,
        taxProfile: FundTaxProfile,
    ): Boolean {
        if (asset.assetType != AssetType.MUTUAL_FUND) return false
        if (holding.comeCotasEnabled == false) return false
        if (holding.comeCotasEnabled == true) return true
        return taxProfile in setOf(FundTaxProfile.COME_COTAS_LONG, FundTaxProfile.COME_COTAS_SHORT)
    }
}
