package io.github.salomax.neotool.financialdata.domain

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Domain object for a single day's OHLCV record from asset_daily_price.
 */
data class AssetDailyPrice(
    val date: LocalDate,
    val openPrice: BigDecimal?,
    val highPrice: BigDecimal?,
    val lowPrice: BigDecimal?,
    val closePrice: BigDecimal,
    val avgPrice: BigDecimal?,
    val volume: BigDecimal?,
    val trades: Int?,
)
