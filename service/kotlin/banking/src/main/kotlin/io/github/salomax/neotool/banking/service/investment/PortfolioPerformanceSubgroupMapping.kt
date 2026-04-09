package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.AssetType

object PortfolioPerformanceSubgroupMapping {
    const val PERF_STOCKS = "perfStocks"
    const val PERF_REITS = "perfReits"
    const val PERF_CRYPTO = "perfCrypto"
    const val PERF_OPTIONS = "perfOptions"

    private val ASSET_TYPES_BY_SUBGROUP =
        mapOf(
            PERF_STOCKS to
                setOf(
                    AssetType.STOCK,
                    AssetType.STOCK_RIGHTS,
                    AssetType.BDR,
                    AssetType.ETF,
                    AssetType.ETF_INTERNATIONAL,
                ),
            PERF_REITS to
                setOf(
                    AssetType.REAL_ESTATE_FUND,
                    AssetType.AGRIBUSINESS_FUND,
                ),
            PERF_CRYPTO to setOf(AssetType.CRYPTO),
            PERF_OPTIONS to setOf(AssetType.OPTION, AssetType.DERIVATIVES),
        )

    fun isKnownSubgroupKey(subgroupKey: String): Boolean = ASSET_TYPES_BY_SUBGROUP.containsKey(subgroupKey)

    fun assetTypesFor(subgroupKey: String): Set<AssetType>? = ASSET_TYPES_BY_SUBGROUP[subgroupKey]
}
