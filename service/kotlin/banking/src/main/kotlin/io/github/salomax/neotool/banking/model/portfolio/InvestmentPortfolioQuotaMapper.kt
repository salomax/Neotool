package io.github.salomax.neotool.banking.model.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.InvestmentPortfolioQuota

object InvestmentPortfolioQuotaMapper {
    fun toDomain(entity: InvestmentPortfolioQuotaEntity): InvestmentPortfolioQuota =
        InvestmentPortfolioQuota(
            id = entity.id,
            financialAccountId = entity.financialAccountId,
            dimensionType = entity.dimensionType,
            dimensionKey = entity.dimensionKey.ifBlank { null },
            quotaDate = entity.quotaDate,
            quotaValue = entity.quotaValue,
            totalQuotas = entity.totalQuotas,
            totalPortfolioValue = entity.totalPortfolioValue,
            hasEstimatedValues = entity.hasEstimatedValues,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
}
