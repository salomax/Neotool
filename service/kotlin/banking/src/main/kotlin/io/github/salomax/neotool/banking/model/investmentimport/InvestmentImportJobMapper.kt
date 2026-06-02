package io.github.salomax.neotool.banking.model.investmentimport

import io.github.salomax.neotool.banking.domain.investmentimport.InvestmentImportJob

object InvestmentImportJobMapper {
    fun toDomain(entity: InvestmentImportJobEntity): InvestmentImportJob =
        InvestmentImportJob(
            id = entity.id,
            importType = entity.importType,
            accountId = entity.accountId,
            createdByUserId = entity.createdByUserId,
            status = entity.status,
            assetId = entity.assetId,
            fileName = entity.fileName,
            fileHash = entity.fileHash,
            extractionMetadata = entity.extractionMetadata,
            reviewPayload = entity.reviewPayload,
            errorMessage = entity.errorMessage,
            errorDetails = entity.errorDetails,
            completedAt = entity.completedAt,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    fun toEntity(job: InvestmentImportJob): InvestmentImportJobEntity =
        InvestmentImportJobEntity(
            id = null,
            jobKind = InvestmentImportJobEntity.JOB_KIND,
            accountId = job.accountId,
            createdByUserId = job.createdByUserId,
            importType = job.importType,
            status = job.status,
            assetId = job.assetId,
            fileName = job.fileName,
            fileHash = job.fileHash,
            extractionMetadata = job.extractionMetadata,
            reviewPayload = job.reviewPayload,
            errorMessage = job.errorMessage,
            errorDetails = job.errorDetails,
            completedAt = job.completedAt,
            createdAt = job.createdAt,
            updatedAt = job.updatedAt,
        )

    fun updateEntity(
        entity: InvestmentImportJobEntity,
        job: InvestmentImportJob,
    ) {
        entity.jobKind = InvestmentImportJobEntity.JOB_KIND
        entity.importType = job.importType
        entity.status = job.status
        entity.assetId = job.assetId
        entity.fileName = job.fileName
        entity.fileHash = job.fileHash
        entity.extractionMetadata = job.extractionMetadata
        entity.reviewPayload = job.reviewPayload
        entity.errorMessage = job.errorMessage
        entity.errorDetails = job.errorDetails
        entity.completedAt = job.completedAt
        entity.updatedAt = job.updatedAt
    }
}
