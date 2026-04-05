package io.github.salomax.neotool.banking.domain.portfolio

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class InvestmentAssetCorporateEvent(
    val id: UUID? = null,
    val assetId: UUID? = null,
    val ticker: String? = null,
    val isinCode: String,
    val eventType: String,
    val factor: BigDecimal? = null,
    val priceUnit: BigDecimal? = null,
    val tradingStart: LocalDate? = null,
    val tradingEnd: LocalDate? = null,
    val subscriptionDate: LocalDate? = null,
    val lastDatePrior: LocalDate,
    val approvedOn: LocalDate,
    val remarks: String? = null,
    val source: String,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
