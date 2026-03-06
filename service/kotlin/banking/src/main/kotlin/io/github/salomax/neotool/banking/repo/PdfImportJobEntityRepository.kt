package io.github.salomax.neotool.banking.repo

import io.github.salomax.neotool.banking.model.pdfimportjob.PdfImportJobEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface PdfImportJobEntityRepository : JpaRepository<PdfImportJobEntity, UUID> {
    fun findByIdAndAccountId(
        id: UUID,
        accountId: UUID,
    ): PdfImportJobEntity?

    fun findByAccountIdAndFileHash(
        accountId: UUID,
        fileHash: String,
    ): PdfImportJobEntity?

    fun findByAccountIdOrderByCreatedAtDesc(accountId: UUID): List<PdfImportJobEntity>

    fun findByFinancialAccountIdAndAccountIdOrderByCreatedAtDesc(
        financialAccountId: UUID,
        accountId: UUID,
    ): List<PdfImportJobEntity>
}
