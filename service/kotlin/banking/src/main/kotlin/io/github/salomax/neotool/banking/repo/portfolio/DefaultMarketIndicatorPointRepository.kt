package io.github.salomax.neotool.banking.repo.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.MarketIndicatorPoint
import io.github.salomax.neotool.banking.model.portfolio.MarketIndicatorPointMapper
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext

@Singleton
open class DefaultMarketIndicatorPointRepository(
    private val entityRepository: MarketIndicatorPointEntityRepository,
    @PersistenceContext private val entityManager: EntityManager,
) : MarketIndicatorPointRepository {
    @Transactional
    override fun upsert(point: MarketIndicatorPoint): MarketIndicatorPoint {
        entityRepository.upsert(
            indicatorLabel = point.indicatorLabel,
            variationLabel = point.variationLabel,
            referenceDate = point.referenceDate,
            publicationDate = point.publicationDate,
            value = point.value,
            source = point.source,
        )
        entityManager.clear()
        return entityRepository.findByKey(point.indicatorLabel, point.variationLabel, point.referenceDate)
            ?.let(MarketIndicatorPointMapper::toDomain)
            ?: throw IllegalStateException(
                "Market indicator point upsert failed for ${point.indicatorLabel}/${point.variationLabel} @ ${point.referenceDate}",
            )
    }
}
