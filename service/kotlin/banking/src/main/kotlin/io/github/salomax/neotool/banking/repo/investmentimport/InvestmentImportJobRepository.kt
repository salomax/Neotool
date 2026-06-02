package io.github.salomax.neotool.banking.repo.investmentimport

import io.github.salomax.neotool.banking.domain.investmentimport.InvestmentImportJob
import java.util.UUID

interface InvestmentImportJobRepository {
    fun save(job: InvestmentImportJob): InvestmentImportJob

    fun update(job: InvestmentImportJob): InvestmentImportJob

    fun findById(
        id: UUID,
        accountId: UUID,
    ): InvestmentImportJob?

    fun findByAccountIdAndFileHash(
        accountId: UUID,
        fileHash: String,
    ): InvestmentImportJob?
}
