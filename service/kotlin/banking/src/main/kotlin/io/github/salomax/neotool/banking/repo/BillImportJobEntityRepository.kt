package io.github.salomax.neotool.banking.repo

import io.github.salomax.neotool.banking.model.billimportjob.BillImportJobEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface BillImportJobEntityRepository : JpaRepository<BillImportJobEntity, UUID> {
    fun findByIdAndAccountIdAndJobKind(
        id: UUID,
        accountId: UUID,
        jobKind: String,
    ): BillImportJobEntity?

    fun findByAccountIdAndFileHashAndJobKind(
        accountId: UUID,
        fileHash: String,
        jobKind: String,
    ): BillImportJobEntity?

    fun findByAccountIdAndJobKindOrderByCreatedAtDesc(
        accountId: UUID,
        jobKind: String,
    ): List<BillImportJobEntity>

    fun findByFinancialAccountIdAndAccountIdAndJobKindOrderByCreatedAtDesc(
        financialAccountId: UUID,
        accountId: UUID,
        jobKind: String,
    ): List<BillImportJobEntity>
}
