package io.github.salomax.neotool.banking.domain.investmentimport

import io.github.salomax.neotool.banking.domain.billimport.ImportDocumentType
import java.time.Instant
import java.util.UUID

data class InvestmentImportJob(
    val id: UUID? = null,
    val importType: ImportDocumentType = ImportDocumentType.INVESTMENT_POSITION,
    val accountId: UUID,
    val createdByUserId: UUID,
    val status: InvestmentImportJobStatus,
    val assetId: UUID,
    val fileName: String? = null,
    val fileHash: String,
    val extractionMetadata: String? = null,
    val reviewPayload: String? = null,
    val errorMessage: String? = null,
    val errorDetails: String? = null,
    val completedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
