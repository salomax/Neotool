package io.github.salomax.neotool.banking.model.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.MarketBenchmarkCode
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.io.Serializable
import java.time.LocalDate

@Embeddable
data class MarketBenchmarkDailyId(
    @Enumerated(EnumType.STRING)
    @Column(name = "benchmark_code", nullable = false, length = 10)
    val benchmarkCode: MarketBenchmarkCode,
    @Column(name = "price_date", nullable = false)
    val priceDate: LocalDate,
) : Serializable
