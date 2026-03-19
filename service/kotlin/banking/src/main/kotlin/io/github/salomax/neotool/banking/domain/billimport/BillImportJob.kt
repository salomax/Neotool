package io.github.salomax.neotool.banking.domain.billimport

import java.time.Instant
import java.util.UUID

/**
 * Bill-import projection of banking.document_import_jobs.
 * JSONB fields are represented as JSON strings.
 */
data class BillImportJob(
    val id: UUID? = null,
    val importType: ImportDocumentType = ImportDocumentType.CREDIT_CARD_BILL,
    val financialAccountId: UUID? = null,
    val accountId: UUID,
    val createdByUserId: UUID,
    val status: BillImportJobStatus,
    val assetId: UUID,
    val fileName: String? = null,
    val fileHash: String,
    val extractedText: String? = null,
    /** JSON string (from JSONB). */
    val extractionMetadata: String? = null,
    /** JSON string (from JSONB). */
    val assistantResponse: String? = null,
    /** JSON string (from JSONB). */
    val reviewPayload: String? = null,
    val errorMessage: String? = null,
    /** JSON string (from JSONB). */
    val errorDetails: String? = null,
    val completedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
