package io.github.salomax.neotool.banking.domain.investment

import java.math.BigDecimal
import java.time.LocalDate

data class ListedAssetMarketData(
    val ticker: String,
    val closePrice: BigDecimal?,
    val referenceDate: LocalDate?,
    val logoAssetId: String? = null,
)

data class ListedAssetMarketPrice(
    val ticker: String,
    val closePrice: BigDecimal,
    val referenceDate: LocalDate,
)

interface ListedAssetMarketDataProvider {
    fun getAssetsByTicker(tickers: Set<String>): Map<String, ListedAssetMarketData>

    fun getLatestPricesByTicker(tickers: Set<String>): Map<String, ListedAssetMarketPrice>
        =
        getAssetsByTicker(tickers)
            .mapNotNull { (ticker, data) ->
                val closePrice = data.closePrice ?: return@mapNotNull null
                val referenceDate = data.referenceDate ?: return@mapNotNull null
                ticker to ListedAssetMarketPrice(
                    ticker = data.ticker,
                    closePrice = closePrice,
                    referenceDate = referenceDate,
                )
            }
            .toMap()
}
