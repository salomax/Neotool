package io.github.salomax.neotool.banking.domain.portfolio

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class InvestmentAssetDailyPrice(
    val assetId: UUID,
    val priceDate: LocalDate,
    val closePrice: BigDecimal,
    val currencyCode: String = "BRL",
    val source: String,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
