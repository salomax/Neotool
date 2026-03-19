package io.github.salomax.neotool.banking.model.billimportjob

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.banking.domain.billimport.BillImportJob

object BillImportJobMapper {
    private val objectMapper = ObjectMapper()

    fun toDomain(entity: BillImportJobEntity): BillImportJob =
        BillImportJob(
            id = entity.id,
            importType = entity.importType,
            financialAccountId = entity.financialAccountId,
            accountId = entity.accountId,
            createdByUserId = entity.createdByUserId,
            status = entity.status,
            assetId = entity.assetId,
            fileName = entity.fileName,
            fileHash = entity.fileHash,
            extractedText = entity.extractedText,
            extractionMetadata = deserializeJsonb(entity.extractionMetadata),
            assistantResponse = deserializeJsonb(entity.assistantResponse),
            reviewPayload = deserializeJsonb(entity.reviewPayload),
            errorMessage = entity.errorMessage,
            errorDetails = deserializeJsonb(entity.errorDetails),
            completedAt = entity.completedAt,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    fun toEntity(job: BillImportJob): BillImportJobEntity =
        BillImportJobEntity(
            id = null,
            jobKind = BillImportJobEntity.JOB_KIND,
            financialAccountId = job.financialAccountId,
            accountId = job.accountId,
            createdByUserId = job.createdByUserId,
            importType = job.importType,
            status = job.status,
            assetId = job.assetId,
            fileName = job.fileName,
            fileHash = job.fileHash,
            extractedText = job.extractedText,
            extractionMetadata = job.extractionMetadata,
            assistantResponse = job.assistantResponse,
            reviewPayload = job.reviewPayload,
            errorMessage = job.errorMessage,
            errorDetails = job.errorDetails,
            completedAt = job.completedAt,
            createdAt = job.createdAt,
            updatedAt = job.updatedAt,
        )

    fun updateEntity(
        entity: BillImportJobEntity,
        job: BillImportJob,
    ) {
        entity.jobKind = BillImportJobEntity.JOB_KIND
        entity.financialAccountId = job.financialAccountId
        entity.importType = job.importType
        entity.status = job.status
        entity.assetId = job.assetId
        entity.fileName = job.fileName
        entity.fileHash = job.fileHash
        entity.extractedText = job.extractedText
        entity.extractionMetadata = job.extractionMetadata
        entity.assistantResponse = job.assistantResponse
        entity.reviewPayload = job.reviewPayload
        entity.errorMessage = job.errorMessage
        entity.errorDetails = job.errorDetails
        entity.completedAt = job.completedAt
        entity.updatedAt = job.updatedAt
    }

    private fun deserializeJsonb(value: String?): String? = value?.let { objectMapper.readTree(it).toString() }
}
