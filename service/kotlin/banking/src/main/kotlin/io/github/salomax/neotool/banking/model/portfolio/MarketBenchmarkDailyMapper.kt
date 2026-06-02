package io.github.salomax.neotool.banking.model.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.MarketBenchmarkDaily

object MarketBenchmarkDailyMapper {
    fun toDomain(entity: MarketBenchmarkDailyEntity): MarketBenchmarkDaily =
        MarketBenchmarkDaily(
            benchmarkCode = entity.id.benchmarkCode,
            priceDate = entity.id.priceDate,
            dailyFactor = entity.dailyFactor,
            cumulativeFrom = entity.cumulativeFrom,
            source = entity.source,
            createdAt = entity.createdAt,
        )

    fun toEntity(domain: MarketBenchmarkDaily): MarketBenchmarkDailyEntity =
        MarketBenchmarkDailyEntity(
            id =
                MarketBenchmarkDailyId(
                    benchmarkCode = domain.benchmarkCode,
                    priceDate = domain.priceDate,
                ),
            dailyFactor = domain.dailyFactor,
            cumulativeFrom = domain.cumulativeFrom,
            source = domain.source,
            createdAt = domain.createdAt,
        )
}
