package io.github.salomax.neotool.banking.repo.investmentimport

import io.github.salomax.neotool.banking.domain.investmentimport.InvestmentImportJob
import io.github.salomax.neotool.banking.model.investmentimport.InvestmentImportJobEntity
import io.github.salomax.neotool.banking.model.investmentimport.InvestmentImportJobMapper
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
open class DefaultInvestmentImportJobRepository(
    private val entityRepository: InvestmentImportJobEntityRepository,
) : InvestmentImportJobRepository {
    private val jobKind = InvestmentImportJobEntity.JOB_KIND

    override fun findById(
        id: UUID,
        accountId: UUID,
    ): InvestmentImportJob? =
        entityRepository.findByIdAndAccountIdAndJobKind(
            id,
            accountId,
            jobKind,
        )?.let(InvestmentImportJobMapper::toDomain)

    override fun findByAccountIdAndFileHash(
        accountId: UUID,
        fileHash: String,
    ): InvestmentImportJob? =
        entityRepository.findByAccountIdAndFileHashAndJobKind(
            accountId,
            fileHash,
            jobKind,
        )?.let(InvestmentImportJobMapper::toDomain)

    @Transactional
    override fun save(job: InvestmentImportJob): InvestmentImportJob {
        val saved = entityRepository.save(InvestmentImportJobMapper.toEntity(job))
        return InvestmentImportJobMapper.toDomain(saved)
    }

    @Transactional
    override fun update(job: InvestmentImportJob): InvestmentImportJob {
        val id = requireNotNull(job.id) { "InvestmentImportJob id is required for update" }
        val entity =
            entityRepository.findByIdAndAccountIdAndJobKind(id, job.accountId, jobKind)
                ?: throw NoSuchElementException("InvestmentImportJob $id not found")
        InvestmentImportJobMapper.updateEntity(entity, job)
        return InvestmentImportJobMapper.toDomain(entityRepository.update(entity))
    }
}
