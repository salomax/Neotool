package io.github.salomax.neotool.banking.model.portfolio

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "market_benchmark_daily", schema = "banking")
open class MarketBenchmarkDailyEntity(
    @EmbeddedId
    open var id: MarketBenchmarkDailyId,
    @Column(name = "daily_factor", nullable = false, precision = 18, scale = 10)
    open var dailyFactor: BigDecimal,
    @Column(name = "cumulative_from", precision = 18, scale = 8)
    open var cumulativeFrom: BigDecimal? = null,
    @Column(nullable = false, length = 50)
    open var source: String,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
)
