package io.github.salomax.neotool.banking.model.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.InvestmentAssetDailyPrice

object InvestmentAssetDailyPriceMapper {
    fun toDomain(entity: InvestmentAssetDailyPriceEntity): InvestmentAssetDailyPrice =
        InvestmentAssetDailyPrice(
            assetId = entity.id.assetId,
            priceDate = entity.id.priceDate,
            closePrice = entity.closePrice,
            currencyCode = entity.currencyCode,
            source = entity.source,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    fun toEntity(domain: InvestmentAssetDailyPrice): InvestmentAssetDailyPriceEntity =
        InvestmentAssetDailyPriceEntity(
            id =
                InvestmentAssetDailyPriceId(
                    assetId = domain.assetId,
                    priceDate = domain.priceDate,
                ),
            closePrice = domain.closePrice,
            currencyCode = domain.currencyCode,
            source = domain.source,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
        )
}
