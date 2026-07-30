package io.github.salomax.neotool.banking.repo.investment

import io.github.salomax.neotool.banking.domain.investment.ComeCotasCheckpoint
import io.github.salomax.neotool.banking.model.investment.ComeCotasCheckpointMapper
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import java.time.LocalDate
import java.util.UUID

@Singleton
open class DefaultComeCotasCheckpointRepository(
    private val entityRepository: ComeCotasCheckpointEntityRepository,
    @PersistenceContext private val entityManager: EntityManager,
) : ComeCotasCheckpointRepository {
    @Transactional
    override fun upsert(checkpoint: ComeCotasCheckpoint): ComeCotasCheckpoint {
        entityRepository.upsert(
            holdingId = checkpoint.holdingId,
            checkpointDate = checkpoint.checkpointDate,
            computedAmount = checkpoint.computedAmount,
            withheldAmount = checkpoint.withheldAmount,
        )
        entityManager.clear()
        return requireNotNull(
            entityRepository.findByHoldingAndCheckpointDate(checkpoint.holdingId, checkpoint.checkpointDate),
        ).let(ComeCotasCheckpointMapper::toDomain)
    }

    override fun findByHoldingAndDateRange(
        holdingId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<ComeCotasCheckpoint> =
        entityRepository.findByHoldingAndDateRange(holdingId, from, to)
            .map(ComeCotasCheckpointMapper::toDomain)
}
