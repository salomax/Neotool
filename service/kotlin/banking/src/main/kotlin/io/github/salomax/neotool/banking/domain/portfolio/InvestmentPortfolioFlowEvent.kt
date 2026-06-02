package io.github.salomax.neotool.banking.domain.portfolio

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class InvestmentPortfolioFlowEvent(
    val id: UUID? = null,
    val financialAccountId: UUID,
    val accountId: UUID,
    val eventDate: LocalDate,
    val eventType: PortfolioFlowEventType,
    val grossAmount: BigDecimal,
    val portfolioValueBefore: BigDecimal,
    val quotaValueAtEvent: BigDecimal,
    val quotasTransacted: BigDecimal,
    val relatedHoldingId: UUID? = null,
    val sourceReferenceId: UUID? = null,
    val sourceReferenceType: String? = null,
    val createdAt: Instant = Instant.now(),
)
