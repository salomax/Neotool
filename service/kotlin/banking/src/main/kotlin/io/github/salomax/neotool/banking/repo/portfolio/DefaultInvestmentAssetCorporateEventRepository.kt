package io.github.salomax.neotool.banking.repo.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.InvestmentAssetCorporateEvent
import io.github.salomax.neotool.banking.model.portfolio.InvestmentAssetCorporateEventMapper
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext

@Singleton
open class DefaultInvestmentAssetCorporateEventRepository(
    private val entityRepository: InvestmentAssetCorporateEventEntityRepository,
    @PersistenceContext private val entityManager: EntityManager,
) : InvestmentAssetCorporateEventRepository {
    @Transactional
    override fun upsert(event: InvestmentAssetCorporateEvent): InvestmentAssetCorporateEvent {
        entityRepository.upsert(
            assetId = event.assetId,
            ticker = event.ticker,
            isinCode = event.isinCode,
            eventType = event.eventType,
            factor = event.factor,
            priceUnit = event.priceUnit,
            tradingStart = event.tradingStart,
            tradingEnd = event.tradingEnd,
            subscriptionDate = event.subscriptionDate,
            lastDatePrior = event.lastDatePrior,
            approvedOn = event.approvedOn,
            remarks = event.remarks,
            source = event.source,
        )
        entityManager.clear()
        return entityRepository.findByNaturalKey(
            isinCode = event.isinCode,
            eventType = event.eventType,
            lastDatePrior = event.lastDatePrior,
            approvedOn = event.approvedOn,
        )?.let(InvestmentAssetCorporateEventMapper::toDomain)
            ?: throw IllegalStateException(
                "Corporate event upsert failed for ${event.isinCode}/${event.eventType} @ ${event.lastDatePrior}",
            )
    }
}
