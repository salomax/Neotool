package io.github.salomax.neotool.banking.domain.pdfimport

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.banking.domain.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.banking.infra.assets.PdfImportAssetStorage
import io.github.salomax.neotool.banking.infra.kafka.PdfExtractorSubmittedEvent
import io.github.salomax.neotool.banking.infra.kafka.PdfExtractorSubmittedProducer
import io.github.salomax.neotool.banking.infra.kafka.PdfImportCallbackContext
import io.github.salomax.neotool.common.error.BusinessRuleException
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ValidationException
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

private const val MAX_PDF_FILE_SIZE_BYTES = 20 * 1024 * 1024 // 20 MB
private const val PDF_DOCUMENT_TYPE = "CREDIT_CARD_BILL"

@Singleton
open class PdfImportService(
    private val repository: PdfImportJobRepository,
    private val financialAccountRepository: FinancialAccountRepository,
    private val pdfImportAssetStorage: PdfImportAssetStorage,
    private val submittedProducer: PdfExtractorSubmittedProducer?,
    private val objectMapper: ObjectMapper,
) {

    @Transactional
    open fun submitPdfBillImport(
        accountId: UUID,
        userId: UUID,
        financialAccountId: UUID?,
        fileName: String,
        fileBytes: ByteArray,
    ): PdfImportJob {
        validateInputs(fileName, fileBytes)
        validateFinancialAccountOwnership(financialAccountId, accountId)

        val producer = submittedProducer ?: throw BusinessRuleException(
            errorCode = BankingErrorCode.IMPORT_ASYNC_PIPELINE_UNAVAILABLE,
            parameters = mapOf("kafkaEnabled" to false, "pipeline" to "pdf"),
        )

        val fileHash = sha256Hex(fileBytes)
        val existing = repository.findByAccountIdAndFileHash(accountId, fileHash)
        if (existing != null && existing.status != PdfImportJobStatus.FAILED) {
            return existing
        }

        val uploadedAsset = pdfImportAssetStorage.uploadPdf(
            accountId = accountId,
            fileName = fileName,
            bytes = fileBytes,
        )

        val now = Instant.now()
        val job = if (existing != null) {
            repository.update(
                existing.copy(
                    financialAccountId = financialAccountId,
                    status = PdfImportJobStatus.PENDING,
                    assetId = uploadedAsset.assetId,
                    fileName = fileName,
                    extractedText = null,
                    extractionMetadata = null,
                    reviewPayload = null,
                    errorMessage = null,
                    completedAt = null,
                    updatedAt = now,
                ),
            )
        } else {
            repository.save(
                PdfImportJob(
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    status = PdfImportJobStatus.PENDING,
                    assetId = uploadedAsset.assetId,
                    fileName = fileName,
                    fileHash = fileHash,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }

        val savedJobId = requireNotNull(job.id) { "PdfImportJob id is required after save" }
        producer.publish(
            PdfExtractorSubmittedEvent(
                job_id = savedJobId,
                asset_id = uploadedAsset.assetId,
                download_url = uploadedAsset.downloadUrl,
                document_type = PDF_DOCUMENT_TYPE,
                callback_context = PdfImportCallbackContext(
                    job_id = savedJobId,
                    account_id = accountId,
                    user_id = userId,
                ),
            ),
        )

        return job
    }

    open fun getJob(jobId: UUID, accountId: UUID): PdfImportJob? =
        repository.findById(jobId, accountId)

    open fun listJobs(financialAccountId: UUID?, accountId: UUID): List<PdfImportJob> =
        if (financialAccountId == null) {
            repository.findByAccount(accountId)
        } else {
            repository.findByFinancialAccount(financialAccountId, accountId)
        }

    @Transactional
    open fun markExtracting(jobId: UUID, accountId: UUID): PdfImportJob? {
        val job = repository.findById(jobId, accountId) ?: return null
        return repository.update(
            job.copy(
                status = PdfImportJobStatus.EXTRACTING,
                updatedAt = Instant.now(),
            ),
        )
    }

    @Transactional
    open fun markExtractedAndLlmProcessing(
        jobId: UUID,
        accountId: UUID,
        extractedText: String?,
        extractionMetadata: Any?,
    ): PdfImportJob? {
        val job = repository.findById(jobId, accountId) ?: return null
        val metadataJson = extractionMetadata?.let { objectMapper.writeValueAsString(it) }
        val now = Instant.now()
        val extracted = repository.update(
            job.copy(
                status = PdfImportJobStatus.EXTRACTED,
                extractedText = extractedText,
                extractionMetadata = metadataJson,
                updatedAt = now,
            ),
        )
        return repository.update(
            extracted.copy(
                status = PdfImportJobStatus.LLM_PROCESSING,
                updatedAt = Instant.now(),
            ),
        )
    }

    @Transactional
    open fun markAwaitingReview(
        jobId: UUID,
        accountId: UUID,
        llmOutput: Any?,
    ): PdfImportJob? {
        val job = repository.findById(jobId, accountId) ?: return null
        val reviewPayloadJson = llmOutput?.let { objectMapper.writeValueAsString(it) }
        return repository.update(
            job.copy(
                status = PdfImportJobStatus.AWAITING_REVIEW,
                reviewPayload = reviewPayloadJson,
                errorMessage = null,
                updatedAt = Instant.now(),
            ),
        )
    }

    @Transactional
    open fun markFailed(jobId: UUID, accountId: UUID, errorMessage: String?): PdfImportJob? {
        val job = repository.findById(jobId, accountId) ?: return null
        val now = Instant.now()
        return repository.update(
            job.copy(
                status = PdfImportJobStatus.FAILED,
                errorMessage = errorMessage,
                completedAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun validateInputs(fileName: String, fileBytes: ByteArray) {
        if (!fileName.endsWith(".pdf", ignoreCase = true)) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "fileName",
                parameters = mapOf("reason" to "File extension must be .pdf"),
            )
        }
        if (fileBytes.size > MAX_PDF_FILE_SIZE_BYTES) {
            throw ValidationException(
                CommonErrorCode.VALIDATION_ERROR,
                field = "fileBase64",
                parameters = mapOf(
                    "maxSizeBytes" to MAX_PDF_FILE_SIZE_BYTES,
                    "actualSizeBytes" to fileBytes.size,
                ),
            )
        }
        if (!looksLikePdf(fileBytes)) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "fileBase64",
                parameters = mapOf("reason" to "File is not a valid PDF document"),
            )
        }
    }

    private fun validateFinancialAccountOwnership(financialAccountId: UUID?, accountId: UUID) {
        if (financialAccountId == null) {
            return
        }
        val accountBelongsToTenant = financialAccountRepository.findByAccountId(accountId)
            .any { it.id == financialAccountId }
        if (!accountBelongsToTenant) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "financialAccountId",
                parameters = mapOf("reason" to "Financial account does not belong to current account"),
            )
        }
    }

    private fun looksLikePdf(fileBytes: ByteArray): Boolean {
        if (fileBytes.size < 5) {
            return false
        }
        val header = fileBytes.copyOfRange(0, 5).toString(Charsets.US_ASCII)
        return header == "%PDF-"
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}

