package io.github.salomax.neotool.banking.model.investment

import io.github.salomax.neotool.banking.domain.investment.Holding

/**
 * Maps between [Holding] domain model and [HoldingEntity].
 */
object HoldingMapper {
    fun toDomain(entity: HoldingEntity): Holding =
        Holding(
            id = entity.id,
            financialAccountId = entity.financialAccountId,
            accountId = entity.accountId,
            assetId = entity.assetId,
            quantity = entity.quantity,
            averageCost = entity.averageCost,
            indexer = entity.indexer,
            indexerPercentage = entity.indexerPercentage,
            fixedAnnualRate = entity.fixedAnnualRate,
            issueDate = entity.issueDate,
            maturityDate = entity.maturityDate,
            gracePeriodDate = entity.gracePeriodDate,
            isTaxExempt = entity.isTaxExempt,
            grossValue = entity.grossValue,
            netValue = entity.netValue,
            incomeTax = entity.incomeTax,
            iof = entity.iof,
            lastValuedAt = entity.lastValuedAt,
            pricingMode = entity.pricingMode,
            manualGrossValue = entity.manualGrossValue,
            manualValuedAt = entity.manualValuedAt,
            source = entity.source,
            providerInvestmentId = entity.providerInvestmentId,
            deletedAt = entity.deletedAt,
            closedAt = entity.closedAt,
            closeReason = entity.closeReason,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    fun toEntity(domain: Holding): HoldingEntity =
        HoldingEntity(
            id = null,
            financialAccountId = domain.financialAccountId,
            accountId = domain.accountId,
            assetId = domain.assetId,
            quantity = domain.quantity,
            averageCost = domain.averageCost,
            indexer = domain.indexer,
            indexerPercentage = domain.indexerPercentage,
            fixedAnnualRate = domain.fixedAnnualRate,
            issueDate = domain.issueDate,
            maturityDate = domain.maturityDate,
            gracePeriodDate = domain.gracePeriodDate,
            isTaxExempt = domain.isTaxExempt,
            grossValue = domain.grossValue,
            netValue = domain.netValue,
            incomeTax = domain.incomeTax,
            iof = domain.iof,
            lastValuedAt = domain.lastValuedAt,
            pricingMode = domain.pricingMode,
            manualGrossValue = domain.manualGrossValue,
            manualValuedAt = domain.manualValuedAt,
            source = domain.source,
            providerInvestmentId = domain.providerInvestmentId,
            deletedAt = domain.deletedAt,
            closedAt = domain.closedAt,
            closeReason = domain.closeReason,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
        )

    fun updateEntity(
        entity: HoldingEntity,
        domain: Holding,
    ) {
        entity.quantity = domain.quantity
        entity.averageCost = domain.averageCost
        entity.indexer = domain.indexer
        entity.indexerPercentage = domain.indexerPercentage
        entity.fixedAnnualRate = domain.fixedAnnualRate
        entity.issueDate = domain.issueDate
        entity.maturityDate = domain.maturityDate
        entity.gracePeriodDate = domain.gracePeriodDate
        entity.isTaxExempt = domain.isTaxExempt
        entity.grossValue = domain.grossValue
        entity.netValue = domain.netValue
        entity.incomeTax = domain.incomeTax
        entity.iof = domain.iof
        entity.lastValuedAt = domain.lastValuedAt
        entity.pricingMode = domain.pricingMode
        entity.manualGrossValue = domain.manualGrossValue
        entity.manualValuedAt = domain.manualValuedAt
        entity.providerInvestmentId = domain.providerInvestmentId
        entity.deletedAt = domain.deletedAt
        entity.closedAt = domain.closedAt
        entity.closeReason = domain.closeReason
        entity.updatedAt = domain.updatedAt
    }
}
