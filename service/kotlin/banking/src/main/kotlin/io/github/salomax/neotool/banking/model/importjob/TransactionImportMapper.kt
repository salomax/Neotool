package io.github.salomax.neotool.banking.model.importjob

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.banking.domain.import.ImportJob
import io.github.salomax.neotool.banking.domain.import.ImportJobStatus
import io.github.salomax.neotool.banking.domain.import.ImportSource

/**
 * Maps between [TransactionImportEntity] and [ImportJob].
 * JSONB fields are serialized/deserialized with [ObjectMapper].
 */
object TransactionImportMapper {

    private val objectMapper = ObjectMapper()

    fun toDomain(entity: TransactionImportEntity): ImportJob =
        ImportJob(
            id = entity.id,
            financialAccountId = entity.financialAccountId,
            accountId = entity.accountId,
            createdByUserId = entity.createdByUserId,
            source = entity.source,
            fileName = entity.fileName,
            fileHash = entity.fileHash,
            status = entity.status,
            totalRows = entity.totalRows,
            importedCount = entity.importedCount,
            skippedCount = entity.skippedCount,
            duplicateReviewCount = entity.duplicateReviewCount,
            errorCount = entity.errorCount,
            errorDetails = deserializeJsonb(entity.errorDetails),
            reviewPayload = deserializeJsonb(entity.reviewPayload),
            dateFrom = entity.dateFrom,
            dateTo = entity.dateTo,
            completedAt = entity.completedAt,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    fun toEntity(job: ImportJob): TransactionImportEntity =
        TransactionImportEntity(
            id = null,
            financialAccountId = job.financialAccountId,
            accountId = job.accountId,
            createdByUserId = job.createdByUserId,
            source = job.source,
            fileName = job.fileName,
            fileHash = job.fileHash,
            status = job.status,
            totalRows = job.totalRows,
            importedCount = job.importedCount,
            skippedCount = job.skippedCount,
            duplicateReviewCount = job.duplicateReviewCount,
            errorCount = job.errorCount,
            errorDetails = job.errorDetails,
            reviewPayload = job.reviewPayload,
            dateFrom = job.dateFrom,
            dateTo = job.dateTo,
            completedAt = job.completedAt,
            createdAt = job.createdAt,
            updatedAt = job.updatedAt,
        )

    fun updateEntity(entity: TransactionImportEntity, job: ImportJob) {
        entity.source = job.source
        entity.status = job.status
        entity.totalRows = job.totalRows
        entity.importedCount = job.importedCount
        entity.skippedCount = job.skippedCount
        entity.duplicateReviewCount = job.duplicateReviewCount
        entity.errorCount = job.errorCount
        entity.errorDetails = job.errorDetails
        entity.reviewPayload = job.reviewPayload
        entity.dateFrom = job.dateFrom
        entity.dateTo = job.dateTo
        entity.completedAt = job.completedAt
        entity.updatedAt = job.updatedAt
    }

    /**
     * Deserializes a JSONB string with ObjectMapper (validates and normalizes).
     * Returns null for null input.
     */
    private fun deserializeJsonb(value: String?): String? =
        value?.let { objectMapper.readTree(it).toString() }
}
