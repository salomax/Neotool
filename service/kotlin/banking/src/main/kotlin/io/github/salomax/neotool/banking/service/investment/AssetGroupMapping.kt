package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.AssetType

object AssetGroupMapping {
    const val CASH = "cash"
    const val VARIABLE_INCOME = "variableIncome"
    const val BANK_FIXED_INCOME = "bankFixedIncome"
    const val CREDIT_FIXED_INCOME = "creditFixedIncome"
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
            AssetType.CDB to BANK_FIXED_INCOME,
            AssetType.RDB to BANK_FIXED_INCOME,
            AssetType.LCI to BANK_FIXED_INCOME,
            AssetType.LCA to BANK_FIXED_INCOME,
            AssetType.LC to BANK_FIXED_INCOME,
            AssetType.LF to BANK_FIXED_INCOME,
            AssetType.LIG to BANK_FIXED_INCOME,
            AssetType.ETF_FIXED_INCOME to BANK_FIXED_INCOME,
            AssetType.DEBENTURES to CREDIT_FIXED_INCOME,
            AssetType.CRI to CREDIT_FIXED_INCOME,
            AssetType.CRA to CREDIT_FIXED_INCOME,
            AssetType.TREASURY to BANK_FIXED_INCOME,
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
            BANK_FIXED_INCOME to "Bank fixed income",
            CREDIT_FIXED_INCOME to "Credit fixed income",
            MUTUAL_FUNDS to "Investment funds",
            PENSION to "Pension",
            STRUCTURED to "Structured",
            OTHERS to "Others",
        )

    fun groupKeyFor(assetType: AssetType?): String = GROUP_BY_ASSET_TYPE[assetType] ?: OTHERS

    fun labelFor(groupKey: String): String = LABEL_BY_GROUP[groupKey] ?: groupKey
}
