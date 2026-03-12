package io.github.salomax.neotool.banking.domain.pdfimport

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.domain.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.domain.import.CanonicalReaderOutput
import io.github.salomax.neotool.banking.domain.import.dedup.ImportFingerprintService
import io.github.salomax.neotool.banking.domain.transaction.CreditCardBillService
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
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
import io.github.salomax.neotool.common.error.ConflictException
import io.github.salomax.neotool.common.error.DomainException
import io.github.salomax.neotool.common.error.ValidationException
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private const val MAX_PDF_FILE_SIZE_BYTES = 20 * 1024 * 1024 // 20 MB
private const val RECORD_TYPE_CARD_SUMMARY = "PDF_CARD_SUMMARY"
private const val RECORD_TYPE_CARD_TRANSACTION = "PDF_CARD_TRANSACTION"

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
        importType: PdfImportType,
        financialAccountId: UUID?,
        fileName: String,
        fileBytes: ByteArray,
    ): PdfImportJob {
        validateInputs(fileName, fileBytes)
        validateFinancialAccountOwnership(financialAccountId, accountId, importType)

        val producer =
            submittedProducer ?: throw BusinessRuleException(
                errorCode = BankingErrorCode.IMPORT_ASYNC_PIPELINE_UNAVAILABLE,
                parameters = mapOf("kafkaEnabled" to false, "pipeline" to "pdf"),
            )

        val fileHash = sha256Hex(fileBytes)
        val existing = repository.findByAccountIdAndFileHash(accountId, fileHash)
        if (existing != null) {
            when {
                existing.status == PdfImportJobStatus.DONE ->
                    throw ConflictException(
                        errorCode = BankingErrorCode.DUPLICATE_IMPORT,
                        field = "fileBase64",
                        parameters =
                            mapOf(
                                "existingJobId" to existing.id?.toString().orEmpty(),
                                "fileHash" to fileHash,
                            ),
                    )
                !isRetryableExistingJob(existing) -> return existing
            }
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
                        importType = importType,
                        financialAccountId = financialAccountId,
                        status = PdfImportJobStatus.PENDING,
                        assetId = uploadedAsset.assetId,
                        fileName = fileName,
                        extractedText = null,
                        extractionMetadata = null,
                        reviewPayload = null,
                        errorMessage = null,
                        errorDetails = null,
                        completedAt = null,
                        updatedAt = now,
                    ),
                )
            } else {
                repository.save(
                    PdfImportJob(
                        importType = importType,
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
                document_type = importType.name,
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

    private fun isRetryableExistingJob(existing: PdfImportJob): Boolean =
        existing.status == PdfImportJobStatus.FAILED ||
            existing.status == PdfImportJobStatus.AWAITING_REVIEW

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
    open fun markExtracted(
        jobId: UUID,
        accountId: UUID,
        extractedText: String?,
        extractionMetadata: Any?,
    ): PdfImportJob? {
        val job = repository.findById(jobId, accountId) ?: return null
        val metadataJson = extractionMetadata?.let { objectMapper.writeValueAsString(it) }
        return repository.update(
            job.copy(
                status = PdfImportJobStatus.EXTRACTED,
                extractedText = extractedText,
                extractionMetadata = metadataJson,
                updatedAt = Instant.now(),
            ),
        )
    }

    @Transactional
    open fun markLlmProcessing(
        jobId: UUID,
        accountId: UUID,
    ): PdfImportJob? {
        val job = repository.findById(jobId, accountId) ?: return null
        return repository.update(
            job.copy(
                status = PdfImportJobStatus.LLM_PROCESSING,
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
        markExtracted(jobId, accountId, extractedText, extractionMetadata) ?: return null
        return markLlmProcessing(jobId, accountId)
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
                errorDetails = null,
                updatedAt = Instant.now(),
            ),
        )
    }

    @Transactional
    open fun markAwaitingReviewFromCanonical(
        jobId: UUID,
        accountId: UUID,
        canonicalOutput: CanonicalReaderOutput,
    ): PdfImportJob? {
        val job = repository.findById(jobId, accountId) ?: return null
        val enrichedCanonical = enrichCanonicalWithCardMatches(canonicalOutput, accountId)
        val reviewPayloadJson = objectMapper.writeValueAsString(enrichedCanonical)
        return repository.update(
            job.copy(
                status = PdfImportJobStatus.AWAITING_REVIEW,
                reviewPayload = reviewPayloadJson,
                errorMessage = null,
                errorDetails = null,
                updatedAt = Instant.now(),
            ),
        )
    }

    open fun projectReviewPayload(
        reviewPayload: String?,
        accountId: UUID,
    ): PdfBillExtractionResult? = runCatching { parsePdfReviewPayload(reviewPayload, accountId) }.getOrNull()

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
        if (job.importType != PdfImportType.CREDIT_CARD_BILL) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "jobId",
                parameters = mapOf("reason" to "confirmPdfBillImport only supports CREDIT_CARD_BILL jobs"),
            )
        }

        val tenantAccountsById =
            financialAccountRepository.findByAccountId(accountId)
                .associateBy { requireNotNull(it.id) { "FinancialAccount id is required" } }
        val reviewPayload = parsePdfReviewPayload(job.reviewPayload, accountId)
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
                        val fingerprintCreditDebitType =
                            if (tx.isBillPayment) {
                                CreditDebitType.CREDIT
                            } else {
                                tx.creditDebitType
                            }
                        val fingerprint =
                            importFingerprintService.compute(
                                financialAccountId = card.financialAccountId,
                                date = tx.date,
                                amountCents = amountCents,
                                normalizedDescription = tx.description,
                                creditDebitType = fingerprintCreditDebitType,
                            )
                        val duplicate = importFingerprintService.isDuplicate(card.financialAccountId, fingerprint)
                        if (duplicate) return@forEach

                        val now = Instant.now()
                        if (tx.isBillPayment) {
                            // Bill payment = TRANSFER pair: CREDIT on card, DEBIT on linked bank (if configured)
                            val cardSide =
                                transactionRepository.save(
                                    Transaction(
                                        financialAccountId = card.financialAccountId,
                                        accountId = accountId,
                                        createdByUserId = userId,
                                        belongsToUserId = userId,
                                        creditDebitType = CreditDebitType.CREDIT,
                                        type = TransactionType.TRANSFER,
                                        status = TransactionStatus.POSTED,
                                        source = TransactionSource.PDF,
                                        amount = tx.amount,
                                        currencyCode = "BRL",
                                        date = tx.date,
                                        description = tx.description.trim(),
                                        cardIdentifier = normalizeLastFourDigits(card.lastFourDigits),
                                        importFingerprint = fingerprint,
                                        importJobId = command.jobId,
                                        createdAt = now,
                                        updatedAt = now,
                                    ),
                                )
                            trackSnapshotRecompute(snapshotRecomputeByAccount, cardSide)

                            val linkedBankAccountId = account.cardData?.linkedBankAccountId
                            if (linkedBankAccountId != null) {
                                val bankSide =
                                    transactionRepository.save(
                                        Transaction(
                                            financialAccountId = linkedBankAccountId,
                                            accountId = accountId,
                                            createdByUserId = userId,
                                            belongsToUserId = userId,
                                            creditDebitType = CreditDebitType.DEBIT,
                                            type = TransactionType.TRANSFER,
                                            status = TransactionStatus.POSTED,
                                            source = TransactionSource.PDF,
                                            amount = tx.amount,
                                            currencyCode = "BRL",
                                            date = tx.date,
                                            description = tx.description.trim(),
                                            transferPeerTransactionId = cardSide.id,
                                            importJobId = command.jobId,
                                            createdAt = now,
                                            updatedAt = now,
                                        ),
                                    )
                                transactionRepository.update(cardSide.copy(transferPeerTransactionId = bankSide.id))
                                trackSnapshotRecompute(snapshotRecomputeByAccount, bankSide)
                            }
                        } else {
                            val billId =
                                creditCardBillService.resolveBillForTransaction(
                                    account = account,
                                    transactionDate = tx.date,
                                    type = TransactionType.CARD,
                                )
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
                            trackSnapshotRecompute(snapshotRecomputeByAccount, saved)
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
                    errorDetails = null,
                    updatedAt = completedAt,
                ),
            )
        } catch (e: Exception) {
            val failedAt = Instant.now()
            val domain = e as? DomainException
            val errorCode = domain?.errorCode?.code ?: CommonErrorCode.INTERNAL_ERROR.code
            val errorParameters = domain?.parameters
            repository.update(
                importingJob.copy(
                    status = PdfImportJobStatus.FAILED,
                    errorMessage = e.message ?: "PDF import commit failed",
                    errorDetails =
                        objectMapper.writeValueAsString(
                            buildErrorDetailsEnvelope(
                                errorCode = errorCode,
                                errorParameters = errorParameters,
                                errorMessage = e.message,
                                errorDetails = mapOf("source" to "confirmPdfBillImport"),
                            ),
                        ),
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
        errorCode: String? = null,
        errorParameters: Map<String, Any?>? = null,
        errorDetails: Any? = null,
    ): PdfImportJob? {
        val job = repository.findById(jobId, accountId) ?: return null
        val now = Instant.now()
        val errorDetailsJson =
            buildErrorDetailsEnvelope(
                errorCode = errorCode,
                errorParameters = errorParameters,
                errorMessage = errorMessage,
                errorDetails = errorDetails,
            )?.let { objectMapper.writeValueAsString(it) }
        return repository.update(
            job.copy(
                status = PdfImportJobStatus.FAILED,
                errorMessage = errorMessage,
                errorDetails = errorDetailsJson,
                completedAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun buildErrorDetailsEnvelope(
        errorCode: String?,
        errorParameters: Map<String, Any?>?,
        errorMessage: String?,
        errorDetails: Any?,
    ): Map<String, Any?>? {
        if (
            errorCode.isNullOrBlank() &&
            errorParameters.isNullOrEmpty() &&
            errorMessage == null &&
            errorDetails == null
        ) {
            return null
        }
        return buildMap {
            if (!errorCode.isNullOrBlank()) put("errorCode", errorCode)
            if (!errorParameters.isNullOrEmpty()) put("errorParameters", errorParameters)
            errorMessage?.let { put("errorMessage", it) }
            errorDetails?.let { put("details", it) }
        }
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
        importType: PdfImportType,
    ) {
        if (financialAccountId == null) {
            if (importType == PdfImportType.BANK_STATEMENT) {
                throw ValidationException(
                    CommonErrorCode.INVALID_INPUT,
                    field = "financialAccountId",
                    parameters = mapOf("reason" to "financialAccountId is required for BANK_STATEMENT"),
                )
            }
            return
        }
        val account =
            financialAccountRepository.findByAccountId(accountId)
                .firstOrNull { it.id == financialAccountId }
        if (account == null) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "financialAccountId",
                parameters = mapOf("reason" to "Financial account does not belong to current account"),
            )
        }
        if (importType == PdfImportType.BANK_STATEMENT && account.type != FinancialAccountType.BANK) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "financialAccountId",
                parameters = mapOf("reason" to "financialAccountId must reference a BANK account for BANK_STATEMENT"),
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
        return objectMapper.writeValueAsString(applyCardMatches(parsed, accountId))
    }

    private fun enrichCanonicalWithCardMatches(
        canonicalOutput: CanonicalReaderOutput,
        accountId: UUID,
    ): CanonicalReaderOutput {
        val cache = mutableMapOf<String, CardMatchProjection>()
        val enrichedRecords =
            canonicalOutput.normalizedRecords.map { record ->
                if (record["record_type"]?.toString() != RECORD_TYPE_CARD_SUMMARY) {
                    return@map record
                }
                val normalizedLastFour = normalizeLastFourDigits(record["card_last_four_digits"]?.toString().orEmpty())
                if (normalizedLastFour.length != 4) {
                    return@map record +
                        mapOf(
                            "card_last_four_digits" to normalizedLastFour,
                            "matched_financial_account_id" to null,
                            "match_ambiguous" to false,
                            "match_candidates" to emptyList<Map<String, Any?>>(),
                        )
                }
                val projection =
                    cache.getOrPut(
                        normalizedLastFour,
                    ) { resolveCardMatchProjection(accountId, normalizedLastFour) }
                record +
                    mapOf("card_last_four_digits" to normalizedLastFour) +
                    projection.toCanonicalFields()
            }

        return canonicalOutput.copy(
            normalizedRecords = enrichedRecords,
            rawMetadata = canonicalOutput.rawMetadata + mapOf("card_match_enriched" to true),
        )
    }

    private fun applyCardMatches(
        payload: PdfBillExtractionResult,
        accountId: UUID,
    ): PdfBillExtractionResult {
        val cache = mutableMapOf<String, CardMatchProjection>()
        val enrichedCards =
            payload.cards.map { card ->
                val normalizedLastFour = normalizeLastFourDigits(card.lastFourDigits)
                if (normalizedLastFour.length != 4) {
                    card.copy(
                        lastFourDigits = normalizedLastFour,
                        matchedFinancialAccountId = null,
                        matchAmbiguous = false,
                        matchCandidates = emptyList(),
                    )
                } else {
                    val projection =
                        cache.getOrPut(
                            normalizedLastFour,
                        ) { resolveCardMatchProjection(accountId, normalizedLastFour) }
                    card.copy(
                        lastFourDigits = normalizedLastFour,
                        matchedFinancialAccountId = projection.matchedFinancialAccountId,
                        matchAmbiguous = projection.matchAmbiguous,
                        matchCandidates = projection.matchCandidates,
                    )
                }
            }
        return payload.copy(cards = enrichedCards)
    }

    private fun resolveCardMatchProjection(
        accountId: UUID,
        normalizedLastFour: String,
    ): CardMatchProjection {
        val matches =
            financialAccountRepository.findCardAccountsByLastFourDigits(
                accountId,
                normalizedLastFour,
            )
        return when {
            matches.size == 1 ->
                CardMatchProjection(
                    matchedFinancialAccountId = matches.first().id,
                    matchAmbiguous = false,
                    matchCandidates = emptyList(),
                )
            matches.isEmpty() ->
                CardMatchProjection(
                    matchedFinancialAccountId = null,
                    matchAmbiguous = false,
                    matchCandidates = emptyList(),
                )
            else ->
                CardMatchProjection(
                    matchedFinancialAccountId = null,
                    matchAmbiguous = true,
                    matchCandidates =
                        matches.mapNotNull { match ->
                            match.id?.let { PdfMatchCandidate(id = it, name = match.name) }
                        },
                )
        }
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

    private fun parsePdfReviewPayload(
        reviewPayload: String?,
        accountId: UUID,
    ): PdfBillExtractionResult {
        if (reviewPayload.isNullOrBlank()) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "jobId",
                parameters = mapOf("reason" to "Review payload is missing"),
            )
        }

        val canonical =
            runCatching {
                objectMapper.readValue(
                    reviewPayload,
                    CanonicalReaderOutput::class.java,
                )
            }.getOrNull()
        if (canonical != null) {
            return applyCardMatches(canonicalToPdfBillExtractionResult(canonical), accountId)
        }

        val llmStyle =
            runCatching {
                objectMapper.readValue(
                    reviewPayload,
                    PdfBillExtractionResult::class.java,
                )
            }.getOrNull()
        if (llmStyle != null) {
            return applyCardMatches(llmStyle, accountId)
        }

        throw ValidationException(
            CommonErrorCode.INVALID_INPUT,
            field = "jobId",
            parameters = mapOf("reason" to "Review payload is invalid"),
        )
    }

    private fun canonicalToPdfBillExtractionResult(canonical: CanonicalReaderOutput): PdfBillExtractionResult {
        val cards = linkedMapOf<String, CanonicalCardAccumulator>()

        canonical.normalizedRecords.forEachIndexed { index, record ->
            val type = record["record_type"]?.toString() ?: return@forEachIndexed
            when (type) {
                RECORD_TYPE_CARD_SUMMARY -> {
                    val lastFour = normalizeLastFourDigits(record["card_last_four_digits"]?.toString().orEmpty())
                    val key = lastFour.ifBlank { "__unknown_summary_$index" }
                    val card = cards.getOrPut(key) { CanonicalCardAccumulator(lastFourDigits = lastFour) }
                    card.lastFourDigits = lastFour
                    card.declaredTotal = record["declared_total"].toBigDecimalOrNull() ?: card.declaredTotal
                    card.computedTotal = record["computed_total"].toBigDecimalOrNull() ?: card.computedTotal
                    card.totalMatchesDeclared =
                        record["total_matches_declared"].toBooleanOrNull()
                            ?: card.totalMatchesDeclared
                    card.confidence = record["confidence"].toDoubleOrNull() ?: card.confidence
                    card.matchedFinancialAccountId =
                        record["matched_financial_account_id"].toUuidOrNull() ?: card.matchedFinancialAccountId
                    card.matchAmbiguous = record["match_ambiguous"].toBooleanOrNull() ?: card.matchAmbiguous
                    card.matchCandidates = record["match_candidates"].toMatchCandidates()
                }
                RECORD_TYPE_CARD_TRANSACTION -> {
                    val lastFour = normalizeLastFourDigits(record["card_last_four_digits"]?.toString().orEmpty())
                    val key = lastFour.ifBlank { "__unknown_tx_$index" }
                    val card = cards.getOrPut(key) { CanonicalCardAccumulator(lastFourDigits = lastFour) }
                    val transaction =
                        PdfExtractedTransaction(
                            date = record["date"].toLocalDateOrNull(),
                            description = record["description"]?.toString().orEmpty(),
                            amount = record["amount"].toBigDecimalOrNull() ?: BigDecimal.ZERO,
                            creditDebitType = record["credit_debit_type"].toCreditDebitTypeOrDefault(),
                            installment =
                                record["installment_current"].toIntOrNull()?.let { current ->
                                    record["installment_total"].toIntOrNull()?.let { total ->
                                        InstallmentInfo(current = current, total = total)
                                    }
                                },
                            isBillPayment = record["is_bill_payment"].toBooleanOrNull() ?: false,
                            confidence = record["confidence"].toDoubleOrNull() ?: canonical.confidence,
                        )
                    card.transactions += transaction
                }
            }
        }

        val mappedCards =
            cards.values.map { card ->
                val computedIfMissing =
                    if (card.computedTotal == BigDecimal.ZERO && card.transactions.isNotEmpty()) {
                        computeSignedTotal(card.transactions)
                    } else {
                        card.computedTotal
                    }
                val declaredIfMissing =
                    if (card.declaredTotal == BigDecimal.ZERO && card.transactions.isNotEmpty()) {
                        computedIfMissing
                    } else {
                        card.declaredTotal
                    }
                val totalMatches =
                    if (card.transactions.isEmpty()) {
                        card.totalMatchesDeclared
                    } else {
                        declaredIfMissing.subtract(computedIfMissing).abs() <= BigDecimal("0.10")
                    }

                PdfExtractedCard(
                    lastFourDigits = card.lastFourDigits,
                    declaredTotal = declaredIfMissing,
                    computedTotal = computedIfMissing,
                    totalMatchesDeclared = totalMatches,
                    transactions = card.transactions.sortedWith(compareBy({ it.date }, { it.description })),
                    matchedFinancialAccountId = card.matchedFinancialAccountId,
                    matchAmbiguous = card.matchAmbiguous,
                    matchCandidates = card.matchCandidates,
                    confidence = card.confidence,
                )
            }

        return PdfBillExtractionResult(
            bankName = canonical.rawMetadata["bank_name"]?.toString(),
            statementPeriod =
                DateRange(
                    from = canonical.rawMetadata["statement_from"].toLocalDateOrNull(),
                    to = canonical.rawMetadata["statement_to"].toLocalDateOrNull(),
                ),
            cards = mappedCards,
            extractionConfidence = canonical.confidence,
            needsReview = canonical.warnings.isNotEmpty(),
            warnings = canonical.warnings,
        )
    }

    private fun computeSignedTotal(transactions: List<PdfExtractedTransaction>): BigDecimal =
        transactions.fold(BigDecimal.ZERO) { acc, tx ->
            if (tx.creditDebitType == CreditDebitType.CREDIT) {
                acc.subtract(tx.amount)
            } else {
                acc.add(tx.amount)
            }
        }

    private fun Any?.toBigDecimalOrNull(): BigDecimal? =
        when (this) {
            null -> null
            is BigDecimal -> this
            is Number -> this.toString().toBigDecimalOrNull()
            else -> this.toString().toBigDecimalOrNull()
        }

    private fun Any?.toLocalDateOrNull(): LocalDate? =
        when (this) {
            null -> null
            is LocalDate -> this
            else -> runCatching { LocalDate.parse(this.toString()) }.getOrNull()
        }

    private fun Any?.toIntOrNull(): Int? =
        when (this) {
            null -> null
            is Int -> this
            is Number -> this.toInt()
            else -> this.toString().toIntOrNull()
        }

    private fun Any?.toDoubleOrNull(): Double? =
        when (this) {
            null -> null
            is Double -> this
            is Float -> this.toDouble()
            is Number -> this.toDouble()
            else -> this.toString().toDoubleOrNull()
        }

    private fun Any?.toBooleanOrNull(): Boolean? =
        when (this) {
            null -> null
            is Boolean -> this
            else ->
                when (this.toString().trim().lowercase()) {
                    "true" -> true
                    "false" -> false
                    else -> null
                }
        }

    private fun Any?.toUuidOrNull(): UUID? =
        when (this) {
            null -> null
            is UUID -> this
            else -> runCatching { UUID.fromString(this.toString()) }.getOrNull()
        }

    private fun Any?.toCreditDebitTypeOrDefault(): CreditDebitType =
        runCatching { CreditDebitType.valueOf(this?.toString().orEmpty()) }.getOrDefault(CreditDebitType.DEBIT)

    private fun Any?.toMatchCandidates(): List<PdfMatchCandidate> =
        when (this) {
            is List<*> ->
                this.mapNotNull { item ->
                    val map = item as? Map<*, *> ?: return@mapNotNull null
                    val id = map["id"].toUuidOrNull() ?: return@mapNotNull null
                    val name = map["name"]?.toString() ?: return@mapNotNull null
                    PdfMatchCandidate(id = id, name = name)
                }
            else -> emptyList()
        }

    private data class CardMatchProjection(
        val matchedFinancialAccountId: UUID?,
        val matchAmbiguous: Boolean,
        val matchCandidates: List<PdfMatchCandidate>,
    ) {
        fun toCanonicalFields(): Map<String, Any?> =
            mapOf(
                "matched_financial_account_id" to matchedFinancialAccountId,
                "match_ambiguous" to matchAmbiguous,
                "match_candidates" to matchCandidates.map { mapOf("id" to it.id, "name" to it.name) },
            )
    }

    private data class CanonicalCardAccumulator(
        var lastFourDigits: String,
        var declaredTotal: BigDecimal = BigDecimal.ZERO,
        var computedTotal: BigDecimal = BigDecimal.ZERO,
        var totalMatchesDeclared: Boolean = false,
        var confidence: Double = 0.0,
        var matchedFinancialAccountId: UUID? = null,
        var matchAmbiguous: Boolean = false,
        var matchCandidates: List<PdfMatchCandidate> = emptyList(),
        val transactions: MutableList<PdfExtractedTransaction> = mutableListOf(),
    )

    private fun trackSnapshotRecompute(
        map: MutableMap<UUID, Pair<Int, Int>>,
        tx: Transaction,
    ) {
        val current = map[tx.financialAccountId]
        val candidate = tx.date.year to tx.date.monthValue
        map[tx.financialAccountId] =
            when {
                current == null -> candidate
                tx.date.year < current.first -> candidate
                tx.date.year == current.first && tx.date.monthValue < current.second -> candidate
                else -> current
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
