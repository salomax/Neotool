package io.github.salomax.neotool.banking.repo.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.MarketDataSyncControl
import io.github.salomax.neotool.banking.domain.portfolio.MarketDataSyncJobName
import io.github.salomax.neotool.banking.model.portfolio.MarketDataSyncControlMapper
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext

@Singleton
open class DefaultMarketDataSyncControlRepository(
    private val entityRepository: MarketDataSyncControlEntityRepository,
    @PersistenceContext private val entityManager: EntityManager,
) : MarketDataSyncControlRepository {
    override fun findByJobName(jobName: MarketDataSyncJobName): MarketDataSyncControl? =
        entityRepository.findByKey(jobName.jobName)?.let(MarketDataSyncControlMapper::toDomain)

    override fun findAll(): List<MarketDataSyncControl> =
        entityRepository.findAllOrdered().map(MarketDataSyncControlMapper::toDomain)

    @Transactional
    override fun upsert(control: MarketDataSyncControl): MarketDataSyncControl {
        entityRepository.upsert(
            jobName = control.jobName.jobName,
            sourceLastDataDate = control.sourceLastDataDate,
            sourceLastRunAt = control.sourceLastRunAt,
            lastSyncedDate = control.lastSyncedDate,
            lastSyncStartedAt = control.lastSyncStartedAt,
            lastSyncCompletedAt = control.lastSyncCompletedAt,
            status = control.status.name,
            lastEventId = control.lastEventId,
            lastError = control.lastError,
        )
        entityManager.clear()
        return entityRepository.findByKey(control.jobName.jobName)
            ?.let(MarketDataSyncControlMapper::toDomain)
            ?: throw IllegalStateException("Market data sync control upsert failed for ${control.jobName.jobName}")
    }
}
