package io.github.salomax.neotool.banking.graphql.resolver

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.banking.domain.pdfimport.ConfirmPdfBillImportCommand
import io.github.salomax.neotool.banking.domain.pdfimport.ConfirmPdfCardCommand
import io.github.salomax.neotool.banking.domain.pdfimport.ConfirmPdfTransactionCommand
import io.github.salomax.neotool.banking.domain.pdfimport.DateRange
import io.github.salomax.neotool.banking.domain.pdfimport.InstallmentInfo
import io.github.salomax.neotool.banking.domain.pdfimport.PdfBillExtractionResult
import io.github.salomax.neotool.banking.domain.pdfimport.PdfExtractedCard
import io.github.salomax.neotool.banking.domain.pdfimport.PdfExtractedTransaction
import io.github.salomax.neotool.banking.domain.pdfimport.PdfImportJob
import io.github.salomax.neotool.banking.domain.pdfimport.PdfImportJobStatus
import io.github.salomax.neotool.banking.domain.pdfimport.PdfImportService
import io.github.salomax.neotool.banking.domain.pdfimport.PdfImportType
import io.github.salomax.neotool.banking.domain.pdfimport.PdfMatchCandidate
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.graphql.dto.DateRangeDTO
import io.github.salomax.neotool.banking.graphql.dto.InstallmentInfoDTO
import io.github.salomax.neotool.banking.graphql.dto.PdfBillExtractionResultDTO
import io.github.salomax.neotool.banking.graphql.dto.PdfExtractedCardDTO
import io.github.salomax.neotool.banking.graphql.dto.PdfExtractedTransactionDTO
import io.github.salomax.neotool.banking.graphql.dto.PdfImportJobDTO
import io.github.salomax.neotool.banking.graphql.dto.PdfMatchCandidateDTO
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID

@Singleton
class PdfImportResolver(
    private val pdfImportService: PdfImportService,
    private val objectMapper: ObjectMapper,
) {
    fun submitPdfBillImport(
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): PdfImportJobDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val userId = requireNotNull(principal.userId) { "No user in token" }

        val fileName =
            input["fileName"] as? String ?: throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "fileName",
                parameters = mapOf("reason" to "fileName is required"),
            )
        val fileBase64 =
            input["fileBase64"] as? String ?: throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "fileBase64",
                parameters = mapOf("reason" to "fileBase64 is required"),
            )
        val importType =
            parseImportType(
                input["importType"] as? String,
                "importType",
            )
        val financialAccountId = (input["financialAccountId"] as? String)?.let { UUID.fromString(it) }
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

        val job =
            pdfImportService.submitPdfBillImport(
                accountId = accountId,
                userId = userId,
                importType = importType,
                financialAccountId = financialAccountId,
                fileName = fileName,
                fileBytes = fileBytes,
            )
        return job.toDTO()
    }

    fun confirmPdfBillImport(
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): PdfImportJobDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val userId = requireNotNull(principal.userId) { "No user in token" }

        val jobId = parseUuid(input["jobId"] as? String, "jobId")
        val cardsInput = asMapList(input["cards"], "cards")

        val command =
            ConfirmPdfBillImportCommand(
                jobId = jobId,
                cards =
                    cardsInput.mapIndexed { cardIndex, cardInput ->
                        val cardLastFourDigits =
                            cardInput["lastFourDigits"] as? String ?: throw ValidationException(
                                CommonErrorCode.INVALID_INPUT,
                                field = "cards[$cardIndex].lastFourDigits",
                                parameters = mapOf("reason" to "lastFourDigits is required"),
                            )
                        val financialAccountId =
                            parseUuid(
                                cardInput["financialAccountId"] as? String,
                                "cards[$cardIndex].financialAccountId",
                            )
                        val transactionsInput = asMapList(cardInput["transactions"], "cards[$cardIndex].transactions")
                        ConfirmPdfCardCommand(
                            lastFourDigits = cardLastFourDigits,
                            financialAccountId = financialAccountId,
                            transactions =
                                transactionsInput.mapIndexed { txIndex, txInput ->
                                    val date =
                                        parseDate(
                                            txInput["date"],
                                            "cards[$cardIndex].transactions[$txIndex].date",
                                        )
                                    val description =
                                        txInput["description"] as? String ?: throw ValidationException(
                                            CommonErrorCode.INVALID_INPUT,
                                            field = "cards[$cardIndex].transactions[$txIndex].description",
                                            parameters = mapOf("reason" to "description is required"),
                                        )
                                    val amount =
                                        (txInput["amount"] as? Number)?.let { BigDecimal(it.toString()) }
                                            ?: throw ValidationException(
                                                CommonErrorCode.INVALID_INPUT,
                                                field = "cards[$cardIndex].transactions[$txIndex].amount",
                                                parameters = mapOf("reason" to "amount is required"),
                                            )
                                    val creditDebitType =
                                        parseCreditDebitType(
                                            txInput["creditDebitType"] as? String,
                                            "cards[$cardIndex].transactions[$txIndex].creditDebitType",
                                        )
                                    val include =
                                        txInput["include"] as? Boolean ?: throw ValidationException(
                                            CommonErrorCode.INVALID_INPUT,
                                            field = "cards[$cardIndex].transactions[$txIndex].include",
                                            parameters = mapOf("reason" to "include is required"),
                                        )
                                    val isBillPayment = txInput["isBillPayment"] as? Boolean ?: false
                                    ConfirmPdfTransactionCommand(
                                        date = date,
                                        description = description,
                                        amount = amount,
                                        creditDebitType = creditDebitType,
                                        include = include,
                                        isBillPayment = isBillPayment,
                                    )
                                },
                        )
                    },
            )

        val job =
            pdfImportService.confirmPdfBillImport(
                accountId = accountId,
                userId = userId,
                command = command,
            )
        return job.toDTO()
    }

    fun pdfImportJob(
        jobId: String,
        principal: RequestPrincipal,
    ): PdfImportJobDTO? {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val job = pdfImportService.getJob(UUID.fromString(jobId), accountId) ?: return null
        return job.toDTO()
    }

    fun pdfImportJobs(
        financialAccountId: String?,
        principal: RequestPrincipal,
    ): List<PdfImportJobDTO> {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val faId = financialAccountId?.let { UUID.fromString(it) }
        return pdfImportService.listJobs(faId, accountId).map { it.toDTO() }
    }

    private fun PdfImportJob.toDTO(): PdfImportJobDTO =
        resolveErrorInfo().let { errorInfo ->
            PdfImportJobDTO(
                id = id ?: throw IllegalStateException("PdfImportJob id is required"),
                importType = importType,
                status = status,
                fileName = fileName,
                // GraphQL DateTime scalar expects OffsetDateTime values.
                createdAt = createdAt.atOffset(ZoneOffset.UTC),
                reviewPayload = parseReviewPayloadForGraphQL(this),
                errorMessage = errorMessage,
                errorCode = errorInfo.code,
                errorParameters = errorInfo.parameters,
            )
        }

    @Suppress("UNCHECKED_CAST")
    private fun PdfImportJob.resolveErrorInfo(): JobErrorInfo {
        val detailsMap =
            errorDetails?.let { detailsJson ->
                runCatching { objectMapper.readValue(detailsJson, Map::class.java) as? Map<String, Any?> }.getOrNull()
            }

        val explicitCode = detailsMap?.get("errorCode")?.toString()
        val explicitParams = detailsMap?.get("errorParameters") as? Map<String, Any?>

        if (!explicitCode.isNullOrBlank()) {
            return JobErrorInfo(code = explicitCode, parameters = explicitParams)
        }

        val nestedError = detailsMap?.get("error") as? Map<*, *>
        val nestedCode = nestedError?.get("code")?.toString()
        if (!nestedCode.isNullOrBlank()) {
            val nestedParams =
                buildMap<String, Any?> {
                    val metadata = detailsMap["error_metadata"]
                    if (metadata != null) put("errorMetadata", metadata)
                    nestedError["retryable"]?.let { put("retryable", it) }
                }.ifEmpty { null }
            return JobErrorInfo(code = nestedCode, parameters = nestedParams)
        }

        return if (status == PdfImportJobStatus.FAILED) {
            JobErrorInfo(
                code = CommonErrorCode.INTERNAL_ERROR.code,
                parameters = errorMessage?.let { mapOf("reason" to it) },
            )
        } else {
            JobErrorInfo(code = null, parameters = null)
        }
    }

    private fun parseReviewPayloadForGraphQL(job: PdfImportJob): PdfBillExtractionResultDTO? {
        if (job.status != PdfImportJobStatus.AWAITING_REVIEW) {
            return null
        }
        val payload = job.reviewPayload ?: return null
        val looksCanonical = payload.contains("\"schemaId\"") || payload.contains("\"schema_id\"")
        if (looksCanonical) {
            return pdfImportService.projectReviewPayload(payload, job.accountId)?.toDTO()
        }

        val llmShape =
            runCatching {
                objectMapper.readValue(payload, PdfBillExtractionResult::class.java)
            }.getOrNull()
        if (llmShape != null) {
            return llmShape.toDTO()
        }

        return pdfImportService.projectReviewPayload(payload, job.accountId)?.toDTO()
    }

    private fun PdfBillExtractionResult.toDTO(): PdfBillExtractionResultDTO =
        PdfBillExtractionResultDTO(
            bankName = bankName,
            statementPeriod = statementPeriod?.toDTO(),
            cards = cards.map { it.toDTO() },
            extractionConfidence = extractionConfidence,
            needsReview = needsReview,
            warnings = warnings,
        )

    private fun DateRange.toDTO(): DateRangeDTO =
        DateRangeDTO(
            from = from,
            to = to,
        )

    private fun PdfExtractedCard.toDTO(): PdfExtractedCardDTO =
        PdfExtractedCardDTO(
            lastFourDigits = lastFourDigits,
            holderName = holderName,
            closingDate = closingDate,
            dueDate = dueDate,
            declaredTotal = declaredTotal.toDouble(),
            computedTotal = computedTotal.toDouble(),
            totalMatchesDeclared = totalMatchesDeclared,
            transactions = transactions.map { it.toDTO() },
            matchedFinancialAccountId = matchedFinancialAccountId,
            matchAmbiguous = matchAmbiguous,
            matchCandidates = matchCandidates.map { it.toDTO() },
            confidence = confidence,
        )

    private fun PdfMatchCandidate.toDTO(): PdfMatchCandidateDTO =
        PdfMatchCandidateDTO(
            id = id,
            name = name,
        )

    private fun PdfExtractedTransaction.toDTO(): PdfExtractedTransactionDTO =
        PdfExtractedTransactionDTO(
            date = date,
            description = description,
            amount = amount.toDouble(),
            creditDebitType = creditDebitType,
            installment = installment?.toDTO(),
            isBillPayment = isBillPayment,
            confidence = confidence,
        )

    private fun InstallmentInfo.toDTO(): InstallmentInfoDTO =
        InstallmentInfoDTO(
            current = current,
            total = total,
        )

    private fun parseUuid(
        value: String?,
        field: String,
    ): UUID {
        if (value.isNullOrBlank()) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = field,
                parameters = mapOf("reason" to "$field is required"),
            )
        }
        return try {
            UUID.fromString(value)
        } catch (e: IllegalArgumentException) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = field,
                parameters = mapOf("reason" to "Invalid UUID"),
                cause = e,
            )
        }
    }

    private fun parseDate(
        value: Any?,
        field: String,
    ): LocalDate {
        return when (value) {
            is LocalDate -> value
            is String ->
                if (value.isBlank()) {
                    throw ValidationException(
                        CommonErrorCode.INVALID_INPUT,
                        field = field,
                        parameters = mapOf("reason" to "$field is required"),
                    )
                } else {
                    try {
                        LocalDate.parse(value)
                    } catch (e: Exception) {
                        throw ValidationException(
                            CommonErrorCode.INVALID_INPUT,
                            field = field,
                            parameters = mapOf("reason" to "Invalid date format"),
                            cause = e,
                        )
                    }
                }
            null ->
                throw ValidationException(
                    CommonErrorCode.INVALID_INPUT,
                    field = field,
                    parameters = mapOf("reason" to "$field is required"),
                )
            else ->
                try {
                    LocalDate.parse(value.toString())
                } catch (e: Exception) {
                    throw ValidationException(
                        CommonErrorCode.INVALID_INPUT,
                        field = field,
                        parameters = mapOf("reason" to "Invalid date format"),
                        cause = e,
                    )
                }
        }
    }

    private fun parseCreditDebitType(
        value: String?,
        field: String,
    ): CreditDebitType {
        if (value.isNullOrBlank()) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = field,
                parameters = mapOf("reason" to "$field is required"),
            )
        }
        return try {
            CreditDebitType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = field,
                parameters = mapOf("reason" to "Invalid creditDebitType"),
                cause = e,
            )
        }
    }

    private fun parseImportType(
        value: String?,
        field: String,
    ): PdfImportType {
        if (value.isNullOrBlank()) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = field,
                parameters = mapOf("reason" to "$field is required"),
            )
        }
        return try {
            PdfImportType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = field,
                parameters = mapOf("reason" to "Invalid importType"),
                cause = e,
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun asMapList(
        value: Any?,
        field: String,
    ): List<Map<String, Any?>> {
        val list =
            value as? List<*> ?: throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = field,
                parameters = mapOf("reason" to "$field must be a list"),
            )
        return list.mapIndexed { index, entry ->
            entry as? Map<String, Any?> ?: throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "$field[$index]",
                parameters = mapOf("reason" to "$field[$index] must be an object"),
            )
        }
    }

    private data class JobErrorInfo(
        val code: String?,
        val parameters: Map<String, Any?>?,
    )
}
