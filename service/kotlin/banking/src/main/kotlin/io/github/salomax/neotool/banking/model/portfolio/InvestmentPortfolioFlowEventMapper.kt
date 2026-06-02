package io.github.salomax.neotool.banking.model.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.InvestmentPortfolioFlowEvent

object InvestmentPortfolioFlowEventMapper {
    fun toDomain(entity: InvestmentPortfolioFlowEventEntity): InvestmentPortfolioFlowEvent =
        InvestmentPortfolioFlowEvent(
            id = entity.id,
            financialAccountId = entity.financialAccountId,
            accountId = entity.accountId,
            eventDate = entity.eventDate,
            eventType = entity.eventType,
            grossAmount = entity.grossAmount,
            portfolioValueBefore = entity.portfolioValueBefore,
            quotaValueAtEvent = entity.quotaValueAtEvent,
            quotasTransacted = entity.quotasTransacted,
            relatedHoldingId = entity.relatedHoldingId,
            sourceReferenceId = entity.sourceReferenceId,
            sourceReferenceType = entity.sourceReferenceType,
            createdAt = entity.createdAt,
        )

    fun toEntity(domain: InvestmentPortfolioFlowEvent): InvestmentPortfolioFlowEventEntity =
        InvestmentPortfolioFlowEventEntity(
            id = null,
            financialAccountId = domain.financialAccountId,
            accountId = domain.accountId,
            eventDate = domain.eventDate,
            eventType = domain.eventType,
            grossAmount = domain.grossAmount,
            portfolioValueBefore = domain.portfolioValueBefore,
            quotaValueAtEvent = domain.quotaValueAtEvent,
            quotasTransacted = domain.quotasTransacted,
            relatedHoldingId = domain.relatedHoldingId,
            sourceReferenceId = domain.sourceReferenceId,
            sourceReferenceType = domain.sourceReferenceType,
            createdAt = domain.createdAt,
        )

    fun updateEntity(
        entity: InvestmentPortfolioFlowEventEntity,
        domain: InvestmentPortfolioFlowEvent,
    ) {
        entity.eventDate = domain.eventDate
        entity.eventType = domain.eventType
        entity.grossAmount = domain.grossAmount
        entity.portfolioValueBefore = domain.portfolioValueBefore
        entity.quotaValueAtEvent = domain.quotaValueAtEvent
        entity.quotasTransacted = domain.quotasTransacted
        entity.relatedHoldingId = domain.relatedHoldingId
        entity.sourceReferenceId = domain.sourceReferenceId
        entity.sourceReferenceType = domain.sourceReferenceType
        entity.createdAt = domain.createdAt
    }
}
