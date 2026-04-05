package io.github.salomax.neotool.banking.domain.portfolio

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class InvestmentPortfolioQuota(
    val id: UUID? = null,
    val financialAccountId: UUID,
    val dimensionType: PortfolioDimensionType,
    val dimensionKey: String? = null,
    val quotaDate: LocalDate,
    val quotaValue: BigDecimal,
    val totalQuotas: BigDecimal,
    val totalPortfolioValue: BigDecimal,
    val hasEstimatedValues: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
