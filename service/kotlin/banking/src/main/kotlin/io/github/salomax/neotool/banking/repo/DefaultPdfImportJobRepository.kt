package io.github.salomax.neotool.banking.repo

import io.github.salomax.neotool.banking.domain.pdfimport.PdfImportJob
import io.github.salomax.neotool.banking.domain.pdfimport.PdfImportJobRepository
import io.github.salomax.neotool.banking.model.pdfimportjob.PdfImportJobMapper
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
open class DefaultPdfImportJobRepository(
    private val entityRepository: PdfImportJobEntityRepository,
) : PdfImportJobRepository {
    override fun findById(
        id: UUID,
        accountId: UUID,
    ): PdfImportJob? = entityRepository.findByIdAndAccountId(id, accountId)?.let(PdfImportJobMapper::toDomain)

    override fun findByAccountIdAndFileHash(
        accountId: UUID,
        fileHash: String,
    ): PdfImportJob? =
        entityRepository.findByAccountIdAndFileHash(accountId, fileHash)?.let(PdfImportJobMapper::toDomain)

    override fun findByAccount(accountId: UUID): List<PdfImportJob> =
        entityRepository.findByAccountIdOrderByCreatedAtDesc(accountId).map(PdfImportJobMapper::toDomain)

    override fun findByFinancialAccount(
        financialAccountId: UUID,
        accountId: UUID,
    ): List<PdfImportJob> =
        entityRepository.findByFinancialAccountIdAndAccountIdOrderByCreatedAtDesc(
            financialAccountId,
            accountId,
        ).map(PdfImportJobMapper::toDomain)

    @Transactional
    override fun save(job: PdfImportJob): PdfImportJob {
        val saved = entityRepository.save(PdfImportJobMapper.toEntity(job))
        return PdfImportJobMapper.toDomain(saved)
    }

    @Transactional
    override fun update(job: PdfImportJob): PdfImportJob {
        val id = requireNotNull(job.id) { "PdfImportJob id is required for update" }
        val entity =
            entityRepository.findByIdAndAccountId(id, job.accountId)
                ?: throw NoSuchElementException("PdfImportJob $id not found")
        PdfImportJobMapper.updateEntity(entity, job)
        val saved = entityRepository.update(entity)
        return PdfImportJobMapper.toDomain(saved)
    }
}
