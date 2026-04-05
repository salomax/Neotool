package io.github.salomax.neotool.banking.model.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.MarketIndicatorPoint

object MarketIndicatorPointMapper {
    fun toDomain(entity: MarketIndicatorPointEntity): MarketIndicatorPoint =
        MarketIndicatorPoint(
            indicatorLabel = entity.id.indicatorLabel,
            variationLabel = entity.id.variationLabel,
            referenceDate = entity.id.referenceDate,
            publicationDate = entity.publicationDate,
            value = entity.value,
            source = entity.source,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
}
