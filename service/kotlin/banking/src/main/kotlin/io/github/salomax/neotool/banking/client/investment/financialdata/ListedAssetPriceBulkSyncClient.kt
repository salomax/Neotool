package io.github.salomax.neotool.banking.client.investment.financialdata

import java.time.Instant
import java.time.LocalDate

data class ListedAssetDailyPriceBulkSyncNode(
    val cursor: String,
    val ticker: String,
    val isinCode: String?,
    val date: LocalDate,
    val closePrice: java.math.BigDecimal,
    val updatedAt: Instant,
)

interface ListedAssetPriceBulkSyncClient {
    fun streamListedAssetDailyPrices(
        from: LocalDate,
        to: LocalDate,
        updatedSince: Instant? = null,
        tickers: Collection<String>? = null,
        after: String? = null,
        consumer: (ListedAssetDailyPriceBulkSyncNode) -> Unit,
    )
}
