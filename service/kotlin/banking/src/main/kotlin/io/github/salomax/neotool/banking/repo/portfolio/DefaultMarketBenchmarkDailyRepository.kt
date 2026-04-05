package io.github.salomax.neotool.banking.repo.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.MarketBenchmarkCode
import io.github.salomax.neotool.banking.domain.portfolio.MarketBenchmarkDaily
import io.github.salomax.neotool.banking.model.portfolio.MarketBenchmarkDailyMapper
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import java.time.LocalDate

@Singleton
open class DefaultMarketBenchmarkDailyRepository(
    private val entityRepository: MarketBenchmarkDailyEntityRepository,
    @PersistenceContext private val entityManager: EntityManager,
) : MarketBenchmarkDailyRepository {
    @Transactional
    override fun upsert(benchmark: MarketBenchmarkDaily): MarketBenchmarkDaily {
        entityRepository.upsert(
            benchmarkCode = benchmark.benchmarkCode.name,
            priceDate = benchmark.priceDate,
            dailyFactor = benchmark.dailyFactor,
            cumulativeFrom = benchmark.cumulativeFrom,
            source = benchmark.source,
        )
        entityManager.clear()
        return entityRepository.findByKey(benchmark.benchmarkCode, benchmark.priceDate)
            ?.let(MarketBenchmarkDailyMapper::toDomain)
            ?: throw IllegalStateException(
                "Benchmark daily upsert failed for ${benchmark.benchmarkCode} @ ${benchmark.priceDate}",
            )
    }

    override fun findByCodeAndDateRange(
        code: MarketBenchmarkCode,
        from: LocalDate,
        to: LocalDate,
    ): List<MarketBenchmarkDaily> =
        entityRepository.findByCodeAndDateRange(code, from, to)
            .map(MarketBenchmarkDailyMapper::toDomain)
}
