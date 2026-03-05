package io.github.salomax.neotool.banking.model.pdfimportjob

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.banking.domain.pdfimport.PdfImportJob

object PdfImportJobMapper {

    private val objectMapper = ObjectMapper()

    fun toDomain(entity: PdfImportJobEntity): PdfImportJob =
        PdfImportJob(
            id = entity.id,
            financialAccountId = entity.financialAccountId,
            accountId = entity.accountId,
            createdByUserId = entity.createdByUserId,
            status = entity.status,
            assetId = entity.assetId,
            fileName = entity.fileName,
            fileHash = entity.fileHash,
            extractedText = entity.extractedText,
            extractionMetadata = deserializeJsonb(entity.extractionMetadata),
            reviewPayload = deserializeJsonb(entity.reviewPayload),
            errorMessage = entity.errorMessage,
            completedAt = entity.completedAt,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    fun toEntity(job: PdfImportJob): PdfImportJobEntity =
        PdfImportJobEntity(
            id = null,
            financialAccountId = job.financialAccountId,
            accountId = job.accountId,
            createdByUserId = job.createdByUserId,
            status = job.status,
            assetId = job.assetId,
            fileName = job.fileName,
            fileHash = job.fileHash,
            extractedText = job.extractedText,
            extractionMetadata = job.extractionMetadata,
            reviewPayload = job.reviewPayload,
            errorMessage = job.errorMessage,
            completedAt = job.completedAt,
            createdAt = job.createdAt,
            updatedAt = job.updatedAt,
        )

    fun updateEntity(entity: PdfImportJobEntity, job: PdfImportJob) {
        entity.financialAccountId = job.financialAccountId
        entity.status = job.status
        entity.assetId = job.assetId
        entity.fileName = job.fileName
        entity.fileHash = job.fileHash
        entity.extractedText = job.extractedText
        entity.extractionMetadata = job.extractionMetadata
        entity.reviewPayload = job.reviewPayload
        entity.errorMessage = job.errorMessage
        entity.completedAt = job.completedAt
        entity.updatedAt = job.updatedAt
    }

    private fun deserializeJsonb(value: String?): String? =
        value?.let { objectMapper.readTree(it).toString() }
}

