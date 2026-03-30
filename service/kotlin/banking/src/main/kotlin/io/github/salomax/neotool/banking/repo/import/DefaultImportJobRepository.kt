package io.github.salomax.neotool.banking.repo.import

import io.github.salomax.neotool.banking.domain.import.ImportJob
import io.github.salomax.neotool.banking.repo.import.ImportJobRepository
import io.github.salomax.neotool.banking.domain.import.ImportJobStatus
import io.github.salomax.neotool.banking.model.import.TransactionImportEntity
import io.github.salomax.neotool.banking.model.import.TransactionImportMapper
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.util.UUID

/**
 * Default implementation of [ImportJobRepository].
 * All operations scope by account_id (tenant isolation).
 */
@Singleton
open class DefaultImportJobRepository(
    private val entityRepository: TransactionImportEntityRepository,
) : ImportJobRepository {
    private val jobKind = TransactionImportEntity.JOB_KIND

    override fun findById(
        id: UUID,
        accountId: UUID,
    ): ImportJob? =
        entityRepository.findByIdAndAccountIdAndJobKind(id, accountId, jobKind)
            ?.let(TransactionImportMapper::toDomain)

    override fun findByFileHash(
        financialAccountId: UUID,
        fileHash: String,
    ): ImportJob? =
        entityRepository.findByFinancialAccountIdAndFileHashAndJobKind(financialAccountId, fileHash, jobKind)
            ?.let(TransactionImportMapper::toDomain)

    override fun findLatestByAccountAndFileHash(
        accountId: UUID,
        fileHash: String,
    ): ImportJob? =
        entityRepository.findLatestByAccountIdAndFileHash(accountId, fileHash, jobKind)
            ?.let(TransactionImportMapper::toDomain)

    override fun findByFinancialAccount(
        financialAccountId: UUID,
        accountId: UUID,
    ): List<ImportJob> =
        entityRepository.findByFinancialAccountIdAndAccountIdAndJobKindOrderByCreatedAtDesc(
            financialAccountId,
            accountId,
            jobKind,
        ).map(TransactionImportMapper::toDomain)

    override fun findByAccount(accountId: UUID): List<ImportJob> =
        entityRepository.findByAccountIdAndJobKindOrderByCreatedAtDesc(accountId, jobKind)
            .map(TransactionImportMapper::toDomain)

    override fun findPendingReviews(
        accountId: UUID,
        financialAccountId: UUID?,
    ): List<ImportJob> {
        val status = ImportJobStatus.REVIEW_REQUIRED
        return if (financialAccountId != null) {
            entityRepository.findByFinancialAccountIdAndAccountIdAndStatusOrderByCreatedAtDesc(
                financialAccountId,
                accountId,
                status,
                jobKind,
            )
        } else {
            entityRepository.findByAccountIdAndStatusAndJobKindOrderByCreatedAtDesc(accountId, status, jobKind)
        }.map(TransactionImportMapper::toDomain)
    }

    @Transactional
    override fun markAssistantRequestPublished(id: UUID): Boolean {
        entityRepository.flush()
        return entityRepository.markAssistantRequestPublished(id, jobKind) > 0
    }

    @Transactional
    override fun save(job: ImportJob): ImportJob {
        val entity = TransactionImportMapper.toEntity(job)
        val saved = entityRepository.save(entity)
        return TransactionImportMapper.toDomain(saved)
    }

    @Transactional
    override fun update(job: ImportJob): ImportJob {
        val id = requireNotNull(job.id) { "ImportJob id is required for update" }
        val entity =
            entityRepository.findByIdAndAccountIdAndJobKind(id, job.accountId, jobKind)
                ?: throw NoSuchElementException("ImportJob $id not found")
        TransactionImportMapper.updateEntity(entity, job)
        val saved = entityRepository.update(entity)
        return TransactionImportMapper.toDomain(saved)
    }
}
