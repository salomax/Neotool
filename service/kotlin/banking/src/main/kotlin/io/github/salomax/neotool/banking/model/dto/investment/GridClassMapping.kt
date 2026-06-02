package io.github.salomax.neotool.banking.model.dto.investment

import io.github.salomax.neotool.banking.domain.investment.AssetType

object GridClassMapping {
    const val CASH = "cash"
    const val FIXED_INCOME = "fixed-income"
    const val FUNDS = "funds"
    const val STOCKS = "stocks"
    const val REITS = "reits"
    const val CRYPTO = "crypto"

    val ALL_KEYS: Set<String> = setOf(CASH, FIXED_INCOME, FUNDS, STOCKS, REITS, CRYPTO)

    private val GRID_CLASS_BY_ASSET_TYPE: Map<AssetType, String> =
        mapOf(
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
            AssetType.MUTUAL_FUND to FUNDS,
            AssetType.PGBL to FUNDS,
            AssetType.VGBL to FUNDS,
            AssetType.STOCK to STOCKS,
            AssetType.STOCK_RIGHTS to STOCKS,
            AssetType.BDR to STOCKS,
            AssetType.ETF to STOCKS,
            AssetType.ETF_INTERNATIONAL to STOCKS,
            AssetType.REAL_ESTATE_FUND to REITS,
            AssetType.AGRIBUSINESS_FUND to REITS,
            AssetType.CRYPTO to CRYPTO,
        )

    fun gridClassFor(assetType: AssetType?): String? = GRID_CLASS_BY_ASSET_TYPE[assetType]
}
