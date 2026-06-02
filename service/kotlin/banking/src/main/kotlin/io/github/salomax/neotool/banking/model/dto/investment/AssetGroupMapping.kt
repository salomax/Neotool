package io.github.salomax.neotool.banking.model.dto.investment

import io.github.salomax.neotool.banking.domain.investment.AssetType

object AssetGroupMapping {
    const val CASH = "cash"
    const val VARIABLE_INCOME = "variableIncome"
    const val FIXED_INCOME = "fixedIncome"
    const val MUTUAL_FUNDS = "mutualFunds"
    const val PENSION = "pension"
    const val STRUCTURED = "structured"
    const val OTHERS = "others"

    private val GROUP_BY_ASSET_TYPE =
        mapOf(
            AssetType.STOCK to VARIABLE_INCOME,
            AssetType.STOCK_RIGHTS to VARIABLE_INCOME,
            AssetType.BDR to VARIABLE_INCOME,
            AssetType.REAL_ESTATE_FUND to VARIABLE_INCOME,
            AssetType.AGRIBUSINESS_FUND to VARIABLE_INCOME,
            AssetType.ETF to VARIABLE_INCOME,
            AssetType.ETF_INTERNATIONAL to VARIABLE_INCOME,
            AssetType.CDB to FIXED_INCOME,
            AssetType.RDB to FIXED_INCOME,
            AssetType.LCI to FIXED_INCOME,
            AssetType.LCA to FIXED_INCOME,
            AssetType.LC to FIXED_INCOME,
            AssetType.LF to FIXED_INCOME,
            AssetType.LIG to FIXED_INCOME,
            AssetType.ETF_FIXED_INCOME to FIXED_INCOME,
            AssetType.DEBENTURES to FIXED_INCOME,
            AssetType.CRI to FIXED_INCOME,
            AssetType.CRA to FIXED_INCOME,
            AssetType.TREASURY to FIXED_INCOME,
            AssetType.MUTUAL_FUND to MUTUAL_FUNDS,
            AssetType.PGBL to PENSION,
            AssetType.VGBL to PENSION,
            AssetType.COE to STRUCTURED,
            AssetType.CRYPTO to OTHERS,
            AssetType.OPTION to OTHERS,
            AssetType.DERIVATIVES to OTHERS,
            AssetType.OTHER to OTHERS,
        )

    private val LABEL_BY_GROUP =
        mapOf(
            CASH to "Cash",
            VARIABLE_INCOME to "Variable income",
            FIXED_INCOME to "Fixed income",
            MUTUAL_FUNDS to "Investment funds",
            PENSION to "Pension",
            STRUCTURED to "Structured",
            OTHERS to "Others",
        )

    fun groupKeyFor(assetType: AssetType?): String = GROUP_BY_ASSET_TYPE[assetType] ?: OTHERS

    fun labelFor(groupKey: String): String = LABEL_BY_GROUP[groupKey] ?: groupKey

    fun knownGroupKeys(): Set<String> = LABEL_BY_GROUP.keys

    fun isKnownGroupKey(groupKey: String): Boolean = LABEL_BY_GROUP.containsKey(groupKey)
}
