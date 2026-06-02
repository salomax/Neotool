package io.github.salomax.neotool.banking.service.investmentimport

import io.github.salomax.neotool.banking.domain.investmentimport.InvestmentImportJob
import java.util.UUID

data class InvestmentImportSubmissionRequest(
    val accountId: UUID,
    val userId: UUID,
    val fileName: String,
    val fileHash: String,
    val fileBytes: ByteArray,
    val existing: InvestmentImportJob?,
)

interface InvestmentImportInputStrategy {
    val priority: Int

    fun supports(fileName: String): Boolean

    fun submit(request: InvestmentImportSubmissionRequest): InvestmentImportJob
}
