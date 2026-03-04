package io.github.salomax.neotool.banking.repo

import io.github.salomax.neotool.banking.domain.import.ImportJob
import io.github.salomax.neotool.banking.domain.import.ImportJobRepository
import io.github.salomax.neotool.banking.model.importjob.TransactionImportMapper
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

    override fun findById(id: UUID, accountId: UUID): ImportJob? =
        entityRepository.findByIdAndAccountId(id, accountId)
            ?.let(TransactionImportMapper::toDomain)

    override fun findByFileHash(financialAccountId: UUID, fileHash: String): ImportJob? =
        entityRepository.findByFinancialAccountIdAndFileHash(financialAccountId, fileHash)
            ?.let(TransactionImportMapper::toDomain)

    override fun findByFinancialAccount(financialAccountId: UUID, accountId: UUID): List<ImportJob> =
        entityRepository.findByFinancialAccountIdAndAccountIdOrderByCreatedAtDesc(
            financialAccountId,
            accountId,
        ).map(TransactionImportMapper::toDomain)

    override fun findByAccount(accountId: UUID): List<ImportJob> =
        entityRepository.findByAccountIdOrderByCreatedAtDesc(accountId)
            .map(TransactionImportMapper::toDomain)

    @Transactional
    override fun save(job: ImportJob): ImportJob {
        val entity = TransactionImportMapper.toEntity(job)
        val saved = entityRepository.save(entity)
        return TransactionImportMapper.toDomain(saved)
    }

    @Transactional
    override fun update(job: ImportJob): ImportJob {
        val id = requireNotNull(job.id) { "ImportJob id is required for update" }
        val entity = entityRepository.findByIdAndAccountId(id, job.accountId)
            ?: throw NoSuchElementException("ImportJob $id not found")
        TransactionImportMapper.updateEntity(entity, job)
        val saved = entityRepository.update(entity)
        return TransactionImportMapper.toDomain(saved)
    }
}
