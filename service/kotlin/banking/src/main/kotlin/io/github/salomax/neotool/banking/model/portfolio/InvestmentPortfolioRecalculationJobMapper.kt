package io.github.salomax.neotool.banking.model.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationJob

object InvestmentPortfolioRecalculationJobMapper {
    fun toDomain(entity: InvestmentPortfolioRecalculationJobEntity): PortfolioRecalculationJob =
        PortfolioRecalculationJob(
            id = entity.id,
            financialAccountId = entity.financialAccountId,
            accountId = entity.accountId,
            status = entity.status,
            recalculateFrom = entity.recalculateFrom,
            triggeredBy = entity.triggeredBy,
            daysProcessed = entity.daysProcessed,
            errorDetails = entity.errorDetails,
            startedAt = entity.startedAt,
            completedAt = entity.completedAt,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    fun toEntity(domain: PortfolioRecalculationJob): InvestmentPortfolioRecalculationJobEntity =
        InvestmentPortfolioRecalculationJobEntity(
            id = null,
            financialAccountId = domain.financialAccountId,
            accountId = domain.accountId,
            status = domain.status,
            recalculateFrom = domain.recalculateFrom,
            triggeredBy = domain.triggeredBy,
            daysProcessed = domain.daysProcessed,
            errorDetails = domain.errorDetails,
            startedAt = domain.startedAt,
            completedAt = domain.completedAt,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
        )

    fun updateEntity(
        entity: InvestmentPortfolioRecalculationJobEntity,
        domain: PortfolioRecalculationJob,
    ) {
        entity.financialAccountId = domain.financialAccountId
        entity.status = domain.status
        entity.recalculateFrom = domain.recalculateFrom
        entity.triggeredBy = domain.triggeredBy
        entity.daysProcessed = domain.daysProcessed
        entity.errorDetails = domain.errorDetails
        entity.startedAt = domain.startedAt
        entity.completedAt = domain.completedAt
        entity.updatedAt = domain.updatedAt
    }
}
