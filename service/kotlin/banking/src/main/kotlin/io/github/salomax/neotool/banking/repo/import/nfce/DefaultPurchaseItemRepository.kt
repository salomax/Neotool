package io.github.salomax.neotool.banking.repo.import.nfce

import io.github.salomax.neotool.banking.domain.import.nfce.PurchaseItem
import io.github.salomax.neotool.banking.model.import.nfce.PurchaseItemMapper
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import java.util.UUID

@Singleton
open class DefaultPurchaseItemRepository(
    private val entityRepository: PurchaseItemEntityRepository,
    @PersistenceContext private val entityManager: EntityManager,
) : PurchaseItemRepository {
    @Transactional
    override fun saveAll(items: List<PurchaseItem>): List<PurchaseItem> {
        if (items.isEmpty()) return emptyList()
        val saved = entityRepository.saveAll(items.map(PurchaseItemMapper::toEntity)).toList()
        entityManager.flush()
        return saved.map(PurchaseItemMapper::toDomain)
    }

    override fun findByTransactionId(
        transactionId: UUID,
        accountId: UUID,
    ): List<PurchaseItem> =
        entityRepository.findByTransactionIdAndAccountIdOrderBySequenceAsc(transactionId, accountId)
            .map(PurchaseItemMapper::toDomain)

    override fun findTop3UnmappedByRawDescription(
        accountId: UUID,
        rawDescription: String,
    ): List<PurchaseItem> =
        entityRepository
            .findTop3ByAccountIdAndRawDescriptionAndProductVariantIdIsNullOrderByCreatedAtDesc(
                accountId,
                rawDescription,
            )
            .map(PurchaseItemMapper::toDomain)
}
