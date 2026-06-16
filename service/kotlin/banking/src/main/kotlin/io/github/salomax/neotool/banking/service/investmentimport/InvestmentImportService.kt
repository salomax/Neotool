package io.github.salomax.neotool.banking.service.investmentimport

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.banking.domain.investmentimport.ConfirmInvestmentImportCommand
import io.github.salomax.neotool.banking.domain.investmentimport.InvestmentImportJob
import io.github.salomax.neotool.banking.domain.investmentimport.InvestmentImportJobStatus
import io.github.salomax.neotool.banking.domain.investmentimport.InvestmentImportReview
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.banking.repo.investmentimport.InvestmentImportJobRepository
import io.github.salomax.neotool.banking.service.investmentimport.b3.B3ExcelInvestmentImportInputStrategy
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ConflictException
import io.github.salomax.neotool.common.error.ValidationException
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

private const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024

@Singleton
open class InvestmentImportService(
    private val repository: InvestmentImportJobRepository,
    private val b3Strategy: B3ExcelInvestmentImportInputStrategy,
    private val commitPreparer: InvestmentImportCommitPreparer,
    private val objectMapper: ObjectMapper,
) {
    private val strategies: List<InvestmentImportInputStrategy> = listOf(b3Strategy)

    @Transactional
    open fun submitInvestmentImport(
        accountId: UUID,
        userId: UUID,
        fileName: String,
        fileBytes: ByteArray,
    ): InvestmentImportJob {
        if (fileBytes.size > MAX_FILE_SIZE_BYTES) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "fileBase64",
                parameters = mapOf("reason" to "Arquivo muito grande. Limite: 10 MB."),
            )
        }

        val fileHash = sha256Hex(fileBytes)
        val existing = repository.findByAccountIdAndFileHash(accountId, fileHash)
        if (existing != null) {
            when (existing.status) {
                InvestmentImportJobStatus.DONE ->
                    throw ConflictException(
                        errorCode = BankingErrorCode.DUPLICATE_IMPORT,
                        field = "fileBase64",
                        parameters = mapOf("existingJobId" to existing.id?.toString().orEmpty()),
                    )
                InvestmentImportJobStatus.AWAITING_REVIEW,
                InvestmentImportJobStatus.IMPORTING,
                -> return existing
                InvestmentImportJobStatus.FAILED -> {
                    // Retry by reprocessing and updating existing job.
                }
            }
        }

        val strategy =
            strategies
                .sortedByDescending { it.priority }
                .firstOrNull { it.supports(fileName) }
                ?: throw ValidationException(
                    CommonErrorCode.INVALID_INPUT,
                    field = "fileName",
                    parameters = mapOf("reason" to "Formato não suportado. Envie um arquivo .xlsx exportado da B3."),
                )

        return strategy.submit(
            InvestmentImportSubmissionRequest(
                accountId = accountId,
                userId = userId,
                fileName = fileName,
                fileHash = fileHash,
                fileBytes = fileBytes,
                existing = existing,
            ),
        )
    }

    open fun getJob(
        id: UUID,
        accountId: UUID,
    ): InvestmentImportJob? = repository.findById(id, accountId)

    @Transactional
    open fun confirmInvestmentImport(command: ConfirmInvestmentImportCommand): InvestmentImportJob {
        val job =
            repository.findById(command.jobId, command.accountId)
                ?: throw NoSuchElementException("InvestmentImportJob ${command.jobId} not found")

        if (job.status == InvestmentImportJobStatus.DONE) {
            return job
        }

        if (job.status != InvestmentImportJobStatus.AWAITING_REVIEW) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "jobId",
                parameters = mapOf("reason" to "Job is not in AWAITING_REVIEW status"),
            )
        }

        val review = parseReviewPayload(job.reviewPayload)
        val importingJob =
            repository.update(
                job.copy(status = InvestmentImportJobStatus.IMPORTING, updatedAt = Instant.now()),
            )

        return try {
            commitPreparer.commit(command, review)
            val doneAt = Instant.now()
            repository.update(
                importingJob.copy(
                    status = InvestmentImportJobStatus.DONE,
                    completedAt = doneAt,
                    updatedAt = doneAt,
                ),
            )
        } catch (ex: Exception) {
            val failedAt = Instant.now()
            repository.update(
                importingJob.copy(
                    status = InvestmentImportJobStatus.FAILED,
                    errorMessage = ex.message ?: "Commit failed",
                    completedAt = failedAt,
                    updatedAt = failedAt,
                ),
            )
            throw ex
        }
    }

    private fun parseReviewPayload(reviewPayload: String?): InvestmentImportReview {
        if (reviewPayload.isNullOrBlank()) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "jobId",
                parameters = mapOf("reason" to "Review payload is missing"),
            )
        }
        return objectMapper.readValue(reviewPayload, InvestmentImportReview::class.java)
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
