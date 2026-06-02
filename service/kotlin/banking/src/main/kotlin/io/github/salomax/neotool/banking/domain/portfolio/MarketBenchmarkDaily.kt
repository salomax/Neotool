package io.github.salomax.neotool.banking.domain.portfolio

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class MarketBenchmarkDaily(
    val benchmarkCode: MarketBenchmarkCode,
    val priceDate: LocalDate,
    val dailyFactor: BigDecimal,
    val cumulativeFrom: BigDecimal? = null,
    val source: String,
    val createdAt: Instant = Instant.now(),
)
