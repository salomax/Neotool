package io.github.salomax.neotool.banking.repo.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.MarketBenchmarkCode
import io.github.salomax.neotool.banking.model.portfolio.MarketBenchmarkDailyEntity
import io.github.salomax.neotool.banking.model.portfolio.MarketBenchmarkDailyId
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.math.BigDecimal
import java.time.LocalDate

@Repository
interface MarketBenchmarkDailyEntityRepository : JpaRepository<MarketBenchmarkDailyEntity, MarketBenchmarkDailyId> {
    @Query(
        """
        SELECT b FROM MarketBenchmarkDailyEntity b
        WHERE b.id.benchmarkCode = :code
          AND b.id.priceDate BETWEEN :from AND :to
        ORDER BY b.id.priceDate ASC
        """,
    )
    fun findByCodeAndDateRange(
        code: MarketBenchmarkCode,
        from: LocalDate,
        to: LocalDate,
    ): List<MarketBenchmarkDailyEntity>

    @Query(
        """
        SELECT b FROM MarketBenchmarkDailyEntity b
        WHERE b.id.benchmarkCode = :code
          AND b.id.priceDate = :priceDate
        """,
    )
    fun findByKey(
        code: MarketBenchmarkCode,
        priceDate: LocalDate,
    ): MarketBenchmarkDailyEntity?

    @Query(
        """
        INSERT INTO banking.market_benchmark_daily
            (benchmark_code, price_date, daily_factor, cumulative_from, source, created_at)
        VALUES
            (:benchmarkCode, :priceDate, :dailyFactor, :cumulativeFrom, :source, NOW())
        ON CONFLICT (benchmark_code, price_date)
        DO UPDATE SET
            daily_factor    = EXCLUDED.daily_factor,
            cumulative_from = EXCLUDED.cumulative_from,
            source          = EXCLUDED.source
        """,
        nativeQuery = true,
    )
    fun upsert(
        benchmarkCode: String,
        priceDate: LocalDate,
        dailyFactor: BigDecimal,
        cumulativeFrom: BigDecimal?,
        source: String,
    )
}
