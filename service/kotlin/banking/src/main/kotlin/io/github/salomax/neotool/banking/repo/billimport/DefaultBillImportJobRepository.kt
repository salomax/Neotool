package io.github.salomax.neotool.banking.repo.billimport

import io.github.salomax.neotool.banking.domain.billimport.BillImportJob
import io.github.salomax.neotool.banking.model.billimport.BillImportJobEntity
import io.github.salomax.neotool.banking.model.billimport.BillImportJobMapper
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
open class DefaultBillImportJobRepository(
    private val entityRepository: BillImportJobEntityRepository,
) : BillImportJobRepository {
    private val jobKind = BillImportJobEntity.JOB_KIND

    override fun findById(
        id: UUID,
        accountId: UUID,
    ): BillImportJob? =
        entityRepository.findByIdAndAccountIdAndJobKind(id, accountId, jobKind)?.let(BillImportJobMapper::toDomain)

    override fun findByAccountIdAndFileHash(
        accountId: UUID,
        fileHash: String,
    ): BillImportJob? =
        entityRepository.findByAccountIdAndFileHashAndJobKind(
            accountId,
            fileHash,
            jobKind,
        )?.let(BillImportJobMapper::toDomain)

    override fun findByAccount(accountId: UUID): List<BillImportJob> =
        entityRepository.findByAccountIdAndJobKindOrderByCreatedAtDesc(
            accountId,
            jobKind,
        ).map(BillImportJobMapper::toDomain)

    override fun findByFinancialAccount(
        financialAccountId: UUID,
        accountId: UUID,
    ): List<BillImportJob> =
        entityRepository.findByFinancialAccountIdAndAccountIdAndJobKindOrderByCreatedAtDesc(
            financialAccountId,
            accountId,
            jobKind,
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
            entityRepository.findByIdAndAccountIdAndJobKind(id, job.accountId, jobKind)
                ?: throw NoSuchElementException("BillImportJob $id not found")
        BillImportJobMapper.updateEntity(entity, job)
        val saved = entityRepository.update(entity)
        return BillImportJobMapper.toDomain(saved)
    }
}
