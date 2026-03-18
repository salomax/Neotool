package io.github.salomax.neotool.banking.repo

import io.github.salomax.neotool.banking.model.billimportjob.BillImportJobEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface BillImportJobEntityRepository : JpaRepository<BillImportJobEntity, UUID> {
    fun findByIdAndAccountId(
        id: UUID,
        accountId: UUID,
    ): BillImportJobEntity?

    fun findByAccountIdAndFileHash(
        accountId: UUID,
        fileHash: String,
    ): BillImportJobEntity?

    fun findByAccountIdOrderByCreatedAtDesc(accountId: UUID): List<BillImportJobEntity>

    fun findByFinancialAccountIdAndAccountIdOrderByCreatedAtDesc(
        financialAccountId: UUID,
        accountId: UUID,
    ): List<BillImportJobEntity>
}
