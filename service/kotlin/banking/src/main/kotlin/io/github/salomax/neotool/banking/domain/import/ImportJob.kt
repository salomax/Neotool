package io.github.salomax.neotool.banking.domain.import

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Domain model for banking.transaction_imports (import job audit and idempotency).
 * JSONB columns [errorDetails] and [reviewPayload] are held as JSON strings.
 */
data class ImportJob(
    val id: UUID? = null,
    val financialAccountId: UUID,
    val accountId: UUID,
    val createdByUserId: UUID,
    val source: ImportSource,
    val fileName: String? = null,
    val fileHash: String,
    val status: ImportJobStatus,
    val totalRows: Int? = null,
    val importedCount: Int? = null,
    val skippedCount: Int? = null,
    val duplicateReviewCount: Int? = null,
    val errorCount: Int? = null,
    /** JSON string (from JSONB). */
    val errorDetails: String? = null,
    /** JSON string (from JSONB). */
    val reviewPayload: String? = null,
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,
    val completedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
