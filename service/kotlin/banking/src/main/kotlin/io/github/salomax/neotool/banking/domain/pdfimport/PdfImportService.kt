package io.github.salomax.neotool.banking.domain.pdfimport

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.domain.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.import.dedup.ImportFingerprintService
import io.github.salomax.neotool.banking.domain.transaction.CreditCardBillService
import io.github.salomax.neotool.banking.domain.transaction.SnapshotComputationService
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionRepository
import io.github.salomax.neotool.banking.domain.transaction.TransactionSource
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
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
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

private const val MAX_PDF_FILE_SIZE_BYTES = 20 * 1024 * 1024 // 20 MB
private const val PDF_DOCUMENT_TYPE = "CREDIT_CARD_BILL"

@Singleton
open class PdfImportService(
    private val repository: PdfImportJobRepository,
    private val financialAccountRepository: FinancialAccountRepository,
    private val transactionRepository: TransactionRepository,
    private val importFingerprintService: ImportFingerprintService,
    private val snapshotComputationService: SnapshotComputationService,
    private val creditCardBillService: CreditCardBillService,
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

        val producer =
            submittedProducer ?: throw BusinessRuleException(
                errorCode = BankingErrorCode.IMPORT_ASYNC_PIPELINE_UNAVAILABLE,
                parameters = mapOf("kafkaEnabled" to false, "pipeline" to "pdf"),
            )

        val fileHash = sha256Hex(fileBytes)
        val existing = repository.findByAccountIdAndFileHash(accountId, fileHash)
        if (existing != null && existing.status != PdfImportJobStatus.FAILED) {
            return existing
        }

        val uploadedAsset =
            pdfImportAssetStorage.uploadPdf(
                accountId = accountId,
                fileName = fileName,
                bytes = fileBytes,
            )

        val now = Instant.now()
        val job =
            if (existing != null) {
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
                callback_context =
                    PdfImportCallbackContext(
                        job_id = savedJobId,
                        account_id = accountId,
                        user_id = userId,
                    ),
            ),
        )

        return job
    }

    open fun getJob(
        jobId: UUID,
        accountId: UUID,
    ): PdfImportJob? = repository.findById(jobId, accountId)

    open fun listJobs(
        financialAccountId: UUID?,
        accountId: UUID,
    ): List<PdfImportJob> =
        if (financialAccountId == null) {
            repository.findByAccount(accountId)
        } else {
            repository.findByFinancialAccount(financialAccountId, accountId)
        }

    @Transactional
    open fun markExtracting(
        jobId: UUID,
        accountId: UUID,
    ): PdfImportJob? {
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
        val extracted =
            repository.update(
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
        val reviewPayloadJson = enrichReviewPayloadWithCardMatches(llmOutput, accountId)
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
    open fun confirmPdfBillImport(
        accountId: UUID,
        userId: UUID,
        command: ConfirmPdfBillImportCommand,
    ): PdfImportJob {
        val job =
            repository.findById(command.jobId, accountId)
                ?: throw NoSuchElementException("PdfImportJob ${command.jobId} not found")
        if (job.status == PdfImportJobStatus.DONE) {
            return job
        }
        if (job.status != PdfImportJobStatus.AWAITING_REVIEW) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "jobId",
                parameters = mapOf("reason" to "Pdf import job is not in AWAITING_REVIEW"),
            )
        }
        if (command.cards.isEmpty()) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "cards",
                parameters = mapOf("reason" to "At least one card is required"),
            )
        }

        val tenantAccountsById =
            financialAccountRepository.findByAccountId(accountId)
                .associateBy { requireNotNull(it.id) { "FinancialAccount id is required" } }
        val reviewPayload = parsePdfReviewPayload(job.reviewPayload)
        validateConfirmInput(command, tenantAccountsById, reviewPayload)

        val importingJob =
            repository.update(
                job.copy(
                    status = PdfImportJobStatus.IMPORTING,
                    updatedAt = Instant.now(),
                ),
            )

        return try {
            val affectedBills = mutableSetOf<UUID>()
            val snapshotRecomputeByAccount = mutableMapOf<UUID, Pair<Int, Int>>()

            command.cards.forEach { card ->
                val account =
                    tenantAccountsById[card.financialAccountId]
                        ?: throw ValidationException(
                            CommonErrorCode.INVALID_INPUT,
                            field = "financialAccountId",
                            parameters = mapOf("reason" to "Financial account does not belong to current account"),
                        )
                card.transactions
                    .filter { it.include }
                    .forEach { tx ->
                        val amountCents = toAmountCents(tx.amount)
                        val fingerprint =
                            importFingerprintService.compute(
                                financialAccountId = card.financialAccountId,
                                date = tx.date,
                                amountCents = amountCents,
                                normalizedDescription = tx.description,
                            )
                        val duplicate = importFingerprintService.isDuplicate(card.financialAccountId, fingerprint)
                        if (duplicate) return@forEach

                        val billId =
                            creditCardBillService.resolveBillForTransaction(
                                account = account,
                                transactionDate = tx.date,
                                type = TransactionType.CARD,
                            )
                        val now = Instant.now()
                        val saved =
                            transactionRepository.save(
                                Transaction(
                                    financialAccountId = card.financialAccountId,
                                    accountId = accountId,
                                    createdByUserId = userId,
                                    belongsToUserId = userId,
                                    creditDebitType = tx.creditDebitType,
                                    type = TransactionType.CARD,
                                    status = TransactionStatus.POSTED,
                                    source = TransactionSource.PDF,
                                    amount = tx.amount,
                                    currencyCode = "BRL",
                                    date = tx.date,
                                    description = tx.description.trim(),
                                    billId = billId,
                                    cardIdentifier = normalizeLastFourDigits(card.lastFourDigits),
                                    importFingerprint = fingerprint,
                                    importJobId = command.jobId,
                                    createdAt = now,
                                    updatedAt = now,
                                ),
                            )
                        saved.billId?.let(affectedBills::add)
                        val current = snapshotRecomputeByAccount[saved.financialAccountId]
                        val candidate = saved.date.year to saved.date.monthValue
                        snapshotRecomputeByAccount[saved.financialAccountId] =
                            when {
                                current == null -> candidate
                                saved.date.year < current.first -> candidate
                                saved.date.year == current.first && saved.date.monthValue < current.second -> candidate
                                else -> current
                            }
                    }
            }

            affectedBills.forEach { billId ->
                creditCardBillService.recomputeBill(billId, accountId)
            }
            snapshotRecomputeByAccount.forEach { (financialAccountId, yearMonth) ->
                snapshotComputationService.recomputeChain(
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    fromYear = yearMonth.first,
                    fromMonth = yearMonth.second,
                )
            }

            val completedAt = Instant.now()
            repository.update(
                importingJob.copy(
                    status = PdfImportJobStatus.DONE,
                    completedAt = completedAt,
                    errorMessage = null,
                    updatedAt = completedAt,
                ),
            )
        } catch (e: Exception) {
            val failedAt = Instant.now()
            repository.update(
                importingJob.copy(
                    status = PdfImportJobStatus.FAILED,
                    errorMessage = e.message ?: "PDF import commit failed",
                    completedAt = failedAt,
                    updatedAt = failedAt,
                ),
            )
            throw e
        }
    }

    @Transactional
    open fun markFailed(
        jobId: UUID,
        accountId: UUID,
        errorMessage: String?,
    ): PdfImportJob? {
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

    private fun validateInputs(
        fileName: String,
        fileBytes: ByteArray,
    ) {
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
                parameters =
                    mapOf(
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

    private fun validateFinancialAccountOwnership(
        financialAccountId: UUID?,
        accountId: UUID,
    ) {
        if (financialAccountId == null) {
            return
        }
        val accountBelongsToTenant =
            financialAccountRepository.findByAccountId(accountId)
                .any { it.id == financialAccountId }
        if (!accountBelongsToTenant) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "financialAccountId",
                parameters = mapOf("reason" to "Financial account does not belong to current account"),
            )
        }
    }

    private fun enrichReviewPayloadWithCardMatches(
        llmOutput: Any?,
        accountId: UUID,
    ): String? {
        if (llmOutput == null) return null
        val parsed =
            runCatching { objectMapper.convertValue(llmOutput, PdfBillExtractionResult::class.java) }.getOrNull()
                ?: return objectMapper.writeValueAsString(llmOutput)
        val enrichedCards =
            parsed.cards.map { card ->
                val normalizedLastFour = normalizeLastFourDigits(card.lastFourDigits)
                if (normalizedLastFour.length != 4) {
                    card.copy(
                        lastFourDigits = normalizedLastFour,
                        matchedFinancialAccountId = null,
                        matchAmbiguous = false,
                        matchCandidates = emptyList(),
                    )
                } else {
                    val matches =
                        financialAccountRepository.findCardAccountsByLastFourDigits(
                            accountId,
                            normalizedLastFour,
                        )
                    when {
                        matches.size == 1 ->
                            card.copy(
                                lastFourDigits = normalizedLastFour,
                                matchedFinancialAccountId = matches.first().id,
                                matchAmbiguous = false,
                                matchCandidates = emptyList(),
                            )
                        matches.isEmpty() ->
                            card.copy(
                                lastFourDigits = normalizedLastFour,
                                matchedFinancialAccountId = null,
                                matchAmbiguous = false,
                                matchCandidates = emptyList(),
                            )
                        else ->
                            card.copy(
                                lastFourDigits = normalizedLastFour,
                                matchedFinancialAccountId = null,
                                matchAmbiguous = true,
                                matchCandidates =
                                    matches.mapNotNull { match ->
                                        match.id?.let { PdfMatchCandidate(id = it, name = match.name) }
                                    },
                            )
                    }
                }
            }
        return objectMapper.writeValueAsString(parsed.copy(cards = enrichedCards))
    }

    private fun validateConfirmInput(
        command: ConfirmPdfBillImportCommand,
        tenantFinancialAccountsById: Map<UUID, FinancialAccount>,
        reviewPayload: PdfBillExtractionResult,
    ) {
        val reviewLastFour = reviewPayload.cards.map { normalizeLastFourDigits(it.lastFourDigits) }.toSet()
        command.cards.forEachIndexed { cardIndex, card ->
            if (normalizeLastFourDigits(card.lastFourDigits).length != 4) {
                throw ValidationException(
                    CommonErrorCode.INVALID_INPUT,
                    field = "cards[$cardIndex].lastFourDigits",
                    parameters = mapOf("reason" to "lastFourDigits must contain exactly 4 digits"),
                )
            }
            val account = tenantFinancialAccountsById[card.financialAccountId]
            if (account == null) {
                throw ValidationException(
                    CommonErrorCode.INVALID_INPUT,
                    field = "cards[$cardIndex].financialAccountId",
                    parameters = mapOf("reason" to "Financial account does not belong to current account"),
                )
            }
            if (account.subtype != FinancialAccountSubtype.CREDIT_CARD &&
                account.subtype != FinancialAccountSubtype.CHARGE_CARD
            ) {
                throw ValidationException(
                    CommonErrorCode.INVALID_INPUT,
                    field = "cards[$cardIndex].financialAccountId",
                    parameters = mapOf("reason" to "Financial account must be a credit/charge card"),
                )
            }
            if (reviewLastFour.isNotEmpty() && normalizeLastFourDigits(card.lastFourDigits) !in reviewLastFour) {
                throw ValidationException(
                    CommonErrorCode.INVALID_INPUT,
                    field = "cards[$cardIndex].lastFourDigits",
                    parameters = mapOf("reason" to "Card is not present in review payload"),
                )
            }
            card.transactions.forEachIndexed { txIndex, tx ->
                if (!tx.include) return@forEachIndexed
                if (tx.description.isBlank()) {
                    throw ValidationException(
                        CommonErrorCode.INVALID_INPUT,
                        field = "cards[$cardIndex].transactions[$txIndex].description",
                        parameters = mapOf("reason" to "Description is required"),
                    )
                }
                if (tx.amount <= BigDecimal.ZERO) {
                    throw ValidationException(
                        CommonErrorCode.INVALID_INPUT,
                        field = "cards[$cardIndex].transactions[$txIndex].amount",
                        parameters = mapOf("reason" to "Amount must be greater than zero"),
                    )
                }
            }
        }
    }

    private fun parsePdfReviewPayload(reviewPayload: String?): PdfBillExtractionResult {
        if (reviewPayload.isNullOrBlank()) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "jobId",
                parameters = mapOf("reason" to "Review payload is missing"),
            )
        }
        return try {
            objectMapper.readValue(reviewPayload, PdfBillExtractionResult::class.java)
        } catch (e: Exception) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "jobId",
                parameters = mapOf("reason" to "Review payload is invalid"),
                cause = e,
            )
        }
    }

    private fun normalizeLastFourDigits(value: String): String = value.filter(Char::isDigit).takeLast(4)

    private fun looksLikePdf(fileBytes: ByteArray): Boolean {
        if (fileBytes.size < 5) {
            return false
        }
        val header = fileBytes.copyOfRange(0, 5).toString(Charsets.US_ASCII)
        return header == "%PDF-"
    }

    private fun toAmountCents(amount: BigDecimal): Long =
        amount
            .multiply(BigDecimal(100))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
