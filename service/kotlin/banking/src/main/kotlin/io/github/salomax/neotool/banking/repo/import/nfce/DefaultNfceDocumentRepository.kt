package io.github.salomax.neotool.banking.repo.import.nfce

import io.github.salomax.neotool.banking.domain.import.nfce.NfceDocument
import io.github.salomax.neotool.banking.model.import.nfce.NfceDocumentMapper
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import java.util.UUID

@Singleton
open class DefaultNfceDocumentRepository(
    private val entityRepository: NfceDocumentEntityRepository,
    @PersistenceContext private val entityManager: EntityManager,
) : NfceDocumentRepository {
    override fun findByAccessKeyAndAccountId(
        accessKey: String,
        accountId: UUID,
    ): NfceDocument? =
        entityRepository.findByAccessKeyAndAccountId(accessKey, accountId)
            ?.let(NfceDocumentMapper::toDomain)

    override fun findByTransactionId(
        transactionId: UUID,
        accountId: UUID,
    ): NfceDocument? =
        entityRepository.findByTransactionIdAndAccountId(transactionId, accountId)
            ?.let(NfceDocumentMapper::toDomain)

    override fun findLinkedTransactionIds(
        accountId: UUID,
        transactionIds: List<UUID>,
    ): Set<UUID> {
        if (transactionIds.isEmpty()) return emptySet()
        return entityRepository.findTransactionIdsByAccountIdAndTransactionIdIn(accountId, transactionIds).toSet()
    }

    @Transactional
    override fun deleteByAccessKeyAndAccountIdForDeletedTransactions(
        accessKey: String,
        accountId: UUID,
    ): Int = entityRepository.deleteByAccessKeyAndAccountIdForDeletedTransactions(accessKey, accountId)

    @Transactional
    override fun save(document: NfceDocument): NfceDocument =
        entityRepository.save(NfceDocumentMapper.toEntity(document))
            .also { entityManager.flush() }
            .let(NfceDocumentMapper::toDomain)
}
