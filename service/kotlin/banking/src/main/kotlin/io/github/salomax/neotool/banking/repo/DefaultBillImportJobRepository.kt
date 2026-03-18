package io.github.salomax.neotool.banking.repo

import io.github.salomax.neotool.banking.domain.billimport.BillImportJob
import io.github.salomax.neotool.banking.domain.billimport.BillImportJobRepository
import io.github.salomax.neotool.banking.model.billimportjob.BillImportJobMapper
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
open class DefaultBillImportJobRepository(
    private val entityRepository: BillImportJobEntityRepository,
) : BillImportJobRepository {
    override fun findById(
        id: UUID,
        accountId: UUID,
    ): BillImportJob? = entityRepository.findByIdAndAccountId(id, accountId)?.let(BillImportJobMapper::toDomain)

    override fun findByAccountIdAndFileHash(
        accountId: UUID,
        fileHash: String,
    ): BillImportJob? =
        entityRepository.findByAccountIdAndFileHash(accountId, fileHash)?.let(BillImportJobMapper::toDomain)

    override fun findByAccount(accountId: UUID): List<BillImportJob> =
        entityRepository.findByAccountIdOrderByCreatedAtDesc(accountId).map(BillImportJobMapper::toDomain)

    override fun findByFinancialAccount(
        financialAccountId: UUID,
        accountId: UUID,
    ): List<BillImportJob> =
        entityRepository.findByFinancialAccountIdAndAccountIdOrderByCreatedAtDesc(
            financialAccountId,
            accountId,
        ).map(BillImportJobMapper::toDomain)

    @Transactional
    override fun save(job: BillImportJob): BillImportJob {
        val saved = entityRepository.save(BillImportJobMapper.toEntity(job))
        return BillImportJobMapper.toDomain(saved)
    }

    @Transactional
    override fun update(job: BillImportJob): BillImportJob {
        val id = requireNotNull(job.id) { "BillImportJob id is required for update" }
        val entity =
            entityRepository.findByIdAndAccountId(id, job.accountId)
                ?: throw NoSuchElementException("BillImportJob $id not found")
        BillImportJobMapper.updateEntity(entity, job)
        val saved = entityRepository.update(entity)
        return BillImportJobMapper.toDomain(saved)
    }
}
