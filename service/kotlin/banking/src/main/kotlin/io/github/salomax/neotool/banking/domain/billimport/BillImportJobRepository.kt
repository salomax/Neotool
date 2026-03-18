package io.github.salomax.neotool.banking.domain.billimport

import java.util.UUID

interface BillImportJobRepository {
    fun save(job: BillImportJob): BillImportJob

    fun update(job: BillImportJob): BillImportJob

    fun findById(
        id: UUID,
        accountId: UUID,
    ): BillImportJob?

    fun findByAccountIdAndFileHash(
        accountId: UUID,
        fileHash: String,
    ): BillImportJob?

    fun findByAccount(accountId: UUID): List<BillImportJob>

    fun findByFinancialAccount(
        financialAccountId: UUID,
        accountId: UUID,
    ): List<BillImportJob>
}
