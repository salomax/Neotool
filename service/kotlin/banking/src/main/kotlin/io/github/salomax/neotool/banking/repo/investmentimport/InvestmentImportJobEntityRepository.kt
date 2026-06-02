package io.github.salomax.neotool.banking.repo.investmentimport

import io.github.salomax.neotool.banking.model.investmentimport.InvestmentImportJobEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface InvestmentImportJobEntityRepository : JpaRepository<InvestmentImportJobEntity, UUID> {
    fun findByIdAndAccountIdAndJobKind(
        id: UUID,
        accountId: UUID,
        jobKind: String,
    ): InvestmentImportJobEntity?

    fun findByAccountIdAndFileHashAndJobKind(
        accountId: UUID,
        fileHash: String,
        jobKind: String,
    ): InvestmentImportJobEntity?
}
