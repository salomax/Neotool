package io.github.salomax.neotool.banking.repo.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.MarketBenchmarkCode
import io.github.salomax.neotool.banking.domain.portfolio.MarketBenchmarkDaily
import java.time.LocalDate

interface MarketBenchmarkDailyRepository {
    fun upsert(benchmark: MarketBenchmarkDaily): MarketBenchmarkDaily

    fun findByCodeAndDateRange(
        code: MarketBenchmarkCode,
        from: LocalDate,
        to: LocalDate,
    ): List<MarketBenchmarkDaily>
}
