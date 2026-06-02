package io.github.salomax.neotool.banking.domain.portfolio

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class MarketIndicatorPoint(
    val indicatorLabel: String,
    val variationLabel: String,
    val referenceDate: LocalDate,
    val publicationDate: LocalDate? = null,
    val value: BigDecimal,
    val source: String,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
