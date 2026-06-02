package io.github.salomax.neotool.banking.graphql.billimport

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.banking.domain.billimport.BillImportJob
import io.github.salomax.neotool.banking.domain.billimport.BillImportJobStatus
import io.github.salomax.neotool.banking.domain.billimport.BillImportMatchCandidate
import io.github.salomax.neotool.banking.domain.billimport.BillImportReview
import io.github.salomax.neotool.banking.domain.billimport.BillImportReviewCard
import io.github.salomax.neotool.banking.domain.billimport.BillImportReviewTransaction
import io.github.salomax.neotool.banking.domain.billimport.DateRange
import io.github.salomax.neotool.banking.domain.billimport.ImportDocumentType
import io.github.salomax.neotool.banking.domain.billimport.InstallmentInfo
import io.github.salomax.neotool.banking.domain.transaction.CategorySource
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.model.dto.billimport.ConfirmBillImportCommand
import io.github.salomax.neotool.banking.model.dto.billimport.ConfirmBillReviewCardCommand
import io.github.salomax.neotool.banking.model.dto.billimport.ConfirmBillReviewTransactionCommand
import io.github.salomax.neotool.banking.service.billimport.BillImportService
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
class BillImportResolver(
    private val billImportService: BillImportService,
    private val objectMapper: ObjectMapper,
) {
    fun submitBillImport(
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): BillImportJobDTO {
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
        val password = (input["password"] as? String)?.takeIf { it.isNotBlank() }
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
            billImportService.submitBillImport(
                accountId = accountId,
                userId = userId,
                importType = importType,
                financialAccountId = financialAccountId,
                fileName = fileName,
                fileBytes = fileBytes,
                password = password,
            )
        return job.toDTO()
    }

    fun confirmBillImport(
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): BillImportJobDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val userId = requireNotNull(principal.userId) { "No user in token" }

        val jobId = parseUuid(input["jobId"] as? String, "jobId")
        val closingDate = input["closingDate"]?.let { parseDate(it, "closingDate") }
        val dueDate = input["dueDate"]?.let { parseDate(it, "dueDate") }
        val cardsInput = asMapList(input["cards"], "cards")

        val command =
            ConfirmBillImportCommand(
                jobId = jobId,
                closingDate = closingDate,
                dueDate = dueDate,
                cards =
                    cardsInput.mapIndexed { cardIndex, cardInput ->
                        val reviewCardId =
                            cardInput["reviewCardId"] as? String ?: throw ValidationException(
                                CommonErrorCode.INVALID_INPUT,
                                field = "cards[$cardIndex].reviewCardId",
                                parameters = mapOf("reason" to "reviewCardId is required"),
                            )
                        val financialAccountId =
                            parseUuid(
                                cardInput["financialAccountId"] as? String,
                                "cards[$cardIndex].financialAccountId",
                            )
                        val transactionsInput = asMapList(cardInput["transactions"], "cards[$cardIndex].transactions")
                        ConfirmBillReviewCardCommand(
                            reviewCardId = reviewCardId,
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
                                    val categoryId =
                                        (txInput["categoryId"] as? String)?.let { UUID.fromString(it) }
                                    val categorySource =
                                        (txInput["categorySource"] as? String)?.let { CategorySource.valueOf(it) }
                                    val installment =
                                        parseInstallmentInput(
                                            txInput["installment"],
                                            "cards[$cardIndex].transactions[$txIndex].installment",
                                        )
                                    ConfirmBillReviewTransactionCommand(
                                        date = date,
                                        description = description,
                                        amount = amount,
                                        creditDebitType = creditDebitType,
                                        include = include,
                                        isBillPayment = isBillPayment,
                                        installment = installment,
                                        categoryId = categoryId,
                                        categorySource = categorySource,
                                    )
                                },
                        )
                    },
            )

        val job =
            billImportService.confirmBillImport(
                accountId = accountId,
                userId = userId,
                command = command,
            )
        return job.toDTO()
    }

    fun billImportJob(
        jobId: String,
        principal: RequestPrincipal,
    ): BillImportJobDTO? {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val job = billImportService.getJob(UUID.fromString(jobId), accountId) ?: return null
        return job.toDTO()
    }

    fun billImportJobs(
        financialAccountId: String?,
        principal: RequestPrincipal,
    ): List<BillImportJobDTO> {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val faId = financialAccountId?.let { UUID.fromString(it) }
        return billImportService.listJobs(faId, accountId).map { it.toDTO() }
    }

    private fun BillImportJob.toDTO(): BillImportJobDTO =
        resolveErrorInfo().let { errorInfo ->
            BillImportJobDTO(
                id = id ?: throw IllegalStateException("BillImportJob id is required"),
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
    private fun BillImportJob.resolveErrorInfo(): JobErrorInfo {
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

        return if (status == BillImportJobStatus.FAILED) {
            JobErrorInfo(
                code = CommonErrorCode.INTERNAL_ERROR.code,
                parameters = errorMessage?.let { mapOf("reason" to it) },
            )
        } else {
            JobErrorInfo(code = null, parameters = null)
        }
    }

    private fun parseReviewPayloadForGraphQL(job: BillImportJob): BillImportReviewDTO? {
        if (job.status != BillImportJobStatus.AWAITING_REVIEW) {
            return null
        }
        val payload = job.reviewPayload ?: return null
        val createdByUserId = job.createdByUserId
        val looksCanonical = payload.contains("\"schemaId\"") || payload.contains("\"schema_id\"")
        if (looksCanonical) {
            return billImportService.projectReviewPayload(payload, job.accountId, createdByUserId)?.toDTO()
        }
        // Always go through projectReviewPayload so we can enrich with categorization suggestions
        // (CAT-25) before the review screen is rendered.
        return billImportService.projectReviewPayload(payload, job.accountId, createdByUserId)?.toDTO()
    }

    private fun BillImportReview.toDTO(): BillImportReviewDTO =
        BillImportReviewDTO(
            bankName = bankName,
            reviewMode = reviewMode,
            statementPeriod = statementPeriod?.toDTO(),
            cards = cards.map { it.toDTO() },
        )

    private fun DateRange.toDTO(): DateRangeDTO =
        DateRangeDTO(
            from = from,
            to = to,
        )

    private fun BillImportReviewCard.toDTO(): BillImportReviewCardDTO =
        BillImportReviewCardDTO(
            reviewCardId = reviewCardId,
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
        )

    private fun BillImportMatchCandidate.toDTO(): BillImportMatchCandidateDTO =
        BillImportMatchCandidateDTO(
            id = id,
            name = name,
        )

    private fun BillImportReviewTransaction.toDTO(): BillImportReviewTransactionDTO =
        BillImportReviewTransactionDTO(
            date = date,
            description = description,
            amount = amount.toDouble(),
            creditDebitType = creditDebitType,
            installment = installment?.toDTO(),
            isBillPayment = isBillPayment,
            categoryId = categoryId,
            categorySource = categorySource,
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

    @Suppress("UNCHECKED_CAST")
    private fun parseInstallmentInput(
        value: Any?,
        field: String,
    ): InstallmentInfo? {
        if (value == null) return null
        val installmentInput =
            value as? Map<String, Any?> ?: throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = field,
                parameters = mapOf("reason" to "$field must be an object"),
            )
        val current =
            (installmentInput["current"] as? Number)?.toInt() ?: throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "$field.current",
                parameters = mapOf("reason" to "current is required"),
            )
        val total =
            (installmentInput["total"] as? Number)?.toInt() ?: throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "$field.total",
                parameters = mapOf("reason" to "total is required"),
            )
        return InstallmentInfo(current = current, total = total)
    }

    private fun parseImportType(
        value: String?,
        field: String,
    ): ImportDocumentType {
        if (value.isNullOrBlank()) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = field,
                parameters = mapOf("reason" to "$field is required"),
            )
        }
        return try {
            ImportDocumentType.valueOf(value)
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
