package io.github.salomax.neotool.banking.graphql.import

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.salomax.neotool.banking.client.import.nfce.sanitizeNfceUrl
import io.github.salomax.neotool.banking.domain.import.DuplicateResolution
import io.github.salomax.neotool.banking.domain.import.ImportJob
import io.github.salomax.neotool.banking.domain.import.isFileImportSource
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.graphql.transaction.AppliedRuleHistoryEntryDTO
import io.github.salomax.neotool.banking.graphql.transaction.TransactionDTO
import io.github.salomax.neotool.banking.model.dto.imports.ImportReviewPayloadMapper
import io.github.salomax.neotool.banking.service.import.ImportJobService
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import jakarta.inject.Singleton
import java.net.URI
import java.util.Base64
import java.util.UUID

private const val MAX_FILE_SIZE_BYTES = 3 * 1024 * 1024 // 3 MB

@Singleton
class ImportResolver(
    private val importJobService: ImportJobService,
) {
    private val objectMapper = jacksonObjectMapper().apply { registerModule(JavaTimeModule()) }

    // ── Queries ─────────────────────────────────────────────────────────────

    fun transactionImports(
        financialAccountId: String?,
        principal: RequestPrincipal,
    ): List<TransactionImportDTO> {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val faId = financialAccountId?.let { UUID.fromString(it) }
        val jobs = importJobService.listJobs(faId, accountId)
        return jobs.map { job -> job.toDTO(objectMapper) }
    }

    fun transactionImport(
        id: String,
        principal: RequestPrincipal,
    ): TransactionImportDTO? {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val jobId = UUID.fromString(id)
        val job = importJobService.getJob(jobId, accountId) ?: return null
        return job.toDTO(objectMapper)
    }

    fun pendingReviewImports(
        financialAccountId: String?,
        principal: RequestPrincipal,
    ): List<TransactionImportDTO> {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val faId = financialAccountId?.let { UUID.fromString(it) }
        return importJobService.listPendingReviews(accountId, faId).map { it.toDTO(objectMapper) }
    }

    // ── Mutations ────────────────────────────────────────────────────────────

    fun importTransactionsFile(
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): ImportResultDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val userId = requireNotNull(principal.userId) { "No user in token" }
        val financialAccountId = UUID.fromString(input["financialAccountId"] as String)
        val fileName = input["fileName"] as String
        val sourceStr = input["source"] as? String
        val source =
            sourceStr?.let {
                runCatching { io.github.salomax.neotool.banking.domain.import.ImportSource.valueOf(it) }
                    .getOrElse { ex ->
                        throw ValidationException(
                            CommonErrorCode.INVALID_INPUT,
                            field = "source",
                            parameters = mapOf("reason" to "Invalid source: $it"),
                            cause = ex,
                        )
                    }
            }
        if (source != null && !source.isFileImportSource()) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "source",
                parameters = mapOf("reason" to "Unsupported source for file import: ${source.name}"),
            )
        }
        val fileBase64 =
            input["fileBase64"] as? String ?: throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "fileBase64",
                parameters = mapOf("reason" to "fileBase64 is required"),
            )
        val fileBytes =
            try {
                Base64.getDecoder().decode(fileBase64)
            } catch (e: IllegalArgumentException) {
                throw ValidationException(
                    CommonErrorCode.INVALID_INPUT,
                    field = "fileBase64",
                    parameters = mapOf("reason" to "Invalid base64"),
                    cause = e,
                )
            }
        if (fileBytes.size > MAX_FILE_SIZE_BYTES) {
            throw ValidationException(
                CommonErrorCode.VALIDATION_ERROR,
                field = "fileBase64",
                parameters =
                    mapOf(
                        "maxSizeBytes" to MAX_FILE_SIZE_BYTES,
                        "actualSizeBytes" to fileBytes.size,
                    ),
            )
        }
        val job =
            importJobService.initiateFileImport(
                financialAccountId = financialAccountId,
                accountId = accountId,
                userId = userId,
                fileBytes = fileBytes,
                fileName = fileName,
                source = source,
            )
        return ImportResultDTO(import = job.toDTO(objectMapper))
    }

    fun importNfce(
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): ImportResultDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val userId = requireNotNull(principal.userId) { "No user in token" }
        val financialAccountId = UUID.fromString(input["financialAccountId"] as String)
        val qrCodeUrl =
            input["qrCodeUrl"] as? String ?: throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "qrCodeUrl",
                parameters = mapOf("reason" to "qrCodeUrl is required"),
            )
        if (!isValidUrl(qrCodeUrl)) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "qrCodeUrl",
                parameters = mapOf("reason" to "Invalid URL format"),
            )
        }
        val job =
            importJobService.importNfce(
                financialAccountId = financialAccountId,
                accountId = accountId,
                userId = userId,
                qrCodeUrl = qrCodeUrl,
            )
        return ImportResultDTO(import = job.toDTO(objectMapper))
    }

    fun cancelImportReview(
        jobId: String,
        principal: RequestPrincipal,
    ): Boolean {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        importJobService.cancelImportReview(UUID.fromString(jobId), accountId)
        return true
    }

    fun resolveImportDuplicate(
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): TransactionImportDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val jobId = UUID.fromString(input["jobId"] as String)
        val rowIndex = (input["rowIndex"] as Number).toInt()
        val resolutionStr = input["resolution"] as String
        val resolution =
            try {
                DuplicateResolution.valueOf(resolutionStr)
            } catch (e: IllegalArgumentException) {
                throw ValidationException(
                    CommonErrorCode.INVALID_INPUT,
                    field = "resolution",
                    parameters = mapOf("reason" to "Invalid resolution: $resolutionStr"),
                    cause = e,
                )
            }
        val job =
            importJobService.resolveImportDuplicate(
                jobId = jobId,
                incomingId = rowIndex,
                resolution = resolution,
                accountId = accountId,
            )
        return job.toDTO(objectMapper)
    }

    private fun isValidUrl(url: String): Boolean =
        try {
            val parsed = URI(sanitizeNfceUrl(url))
            parsed.host?.isNotBlank() == true && (parsed.scheme == "http" || parsed.scheme == "https")
        } catch (_: Exception) {
            false
        }

    private fun ImportJob.toDTO(mapper: com.fasterxml.jackson.databind.ObjectMapper): TransactionImportDTO {
        val duplicateCandidates =
            when {
                status != io.github.salomax.neotool.banking.domain.import.ImportJobStatus.REVIEW_REQUIRED -> emptyList()
                reviewPayload.isNullOrBlank() -> emptyList()
                else ->
                    ImportReviewPayloadMapper
                        .duplicateCandidates(mapper, reviewPayload)
                        .map { c ->
                            ImportDuplicateCandidateDTO(
                                rowIndex = c.rowIndex,
                                parsed = c.parsed.toDTO(),
                                candidates = c.candidates.map { it.toDTO() },
                            )
                        }
            }
        return TransactionImportDTO(
            id = id!!,
            financialAccountId = financialAccountId,
            source = source,
            fileName = fileName,
            status = status,
            totalRows = totalRows,
            importedCount = importedCount,
            skippedCount = skippedCount,
            duplicateReviewCount = duplicateReviewCount,
            errorCount = errorCount,
            errorDetails = errorDetails,
            duplicateCandidates = duplicateCandidates,
            dateFrom = dateFrom,
            dateTo = dateTo,
            completedAt = completedAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun io.github.salomax.neotool.banking.domain.import.ParsedTransaction.toDTO(): ParsedImportRowDTO =
        ParsedImportRowDTO(
            creditDebitType = creditDebitType,
            amount = amount.toDouble(),
            date = date,
            description = description,
            currencyCode = currencyCode,
            counterpartyName = counterpartyName,
            counterpartyDocument = counterpartyDocument,
        )

    private fun Transaction.toDTO(): TransactionDTO =
        TransactionDTO(
            id = id!!,
            financialAccountId = financialAccountId,
            belongsToUserId = belongsToUserId,
            belongsToName = belongsToName,
            creditDebitType = creditDebitType,
            type = type,
            status = status,
            source = source,
            hasNfceDocument = false,
            amount = amount.toDouble(),
            currencyCode = currencyCode,
            amountBrl = amountBrl?.toDouble(),
            date = date,
            transactionDatetime = transactionDatetime,
            description = description,
            notes = notes,
            tags = tags,
            category = null,
            categorySource = categorySource,
            merchant = null,
            counterpartyName = counterpartyName,
            counterpartyDocument = counterpartyDocument,
            counterpartyBankCode = counterpartyBankCode,
            counterpartyAccountNumber = counterpartyAccountNumber,
            paymentReason = paymentReason,
            transferPeerTransactionId = transferPeerTransactionId,
            investmentTransactionId = investmentTransactionId,
            recurringPaymentId = recurringPaymentId,
            installmentNumber = installmentNumber,
            totalInstallments = totalInstallments,
            billId = billId,
            cardIdentifier = cardIdentifier,
            payeeMcc = payeeMcc,
            billPostDate = billPostDate,
            appliedRuleIds = appliedRuleIds,
            appliedRuleHistory =
                appliedRuleHistory.map {
                    AppliedRuleHistoryEntryDTO(ruleId = it.ruleId, appliedAt = it.appliedAt)
                },
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}
