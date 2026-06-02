package io.github.salomax.neotool.banking.model.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.InvestmentAssetCorporateEvent

object InvestmentAssetCorporateEventMapper {
    fun toDomain(entity: InvestmentAssetCorporateEventEntity): InvestmentAssetCorporateEvent =
        InvestmentAssetCorporateEvent(
            id = entity.id,
            assetId = entity.assetId,
            ticker = entity.ticker,
            isinCode = entity.isinCode,
            eventType = entity.eventType,
            factor = entity.factor,
            priceUnit = entity.priceUnit,
            tradingStart = entity.tradingStart,
            tradingEnd = entity.tradingEnd,
            subscriptionDate = entity.subscriptionDate,
            lastDatePrior = entity.lastDatePrior,
            approvedOn = entity.approvedOn,
            remarks = entity.remarks,
            source = entity.source,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
}
