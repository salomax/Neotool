package io.github.salomax.neotool.banking.model.portfolio

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "market_indicator_point", schema = "banking")
open class MarketIndicatorPointEntity(
    @EmbeddedId
    open var id: MarketIndicatorPointId,
    @Column(name = "publication_date")
    open var publicationDate: LocalDate? = null,
    @Column(nullable = false, precision = 18, scale = 8)
    open var value: BigDecimal,
    @Column(nullable = false, length = 50)
    open var source: String,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
)
