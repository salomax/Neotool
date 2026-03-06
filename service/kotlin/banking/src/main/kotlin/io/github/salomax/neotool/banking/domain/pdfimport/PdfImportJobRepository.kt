package io.github.salomax.neotool.banking.domain.pdfimport

import java.util.UUID

interface PdfImportJobRepository {
    fun save(job: PdfImportJob): PdfImportJob

    fun update(job: PdfImportJob): PdfImportJob

    fun findById(
        id: UUID,
        accountId: UUID,
    ): PdfImportJob?

    fun findByAccountIdAndFileHash(
        accountId: UUID,
        fileHash: String,
    ): PdfImportJob?

    fun findByAccount(accountId: UUID): List<PdfImportJob>

    fun findByFinancialAccount(
        financialAccountId: UUID,
        accountId: UUID,
    ): List<PdfImportJob>
}
