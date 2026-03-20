package io.github.salomax.neotool.banking.domain.billimport

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.banking.domain.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.domain.import.CanonicalReaderOutput
import io.github.salomax.neotool.banking.domain.import.dedup.ImportFingerprintService
import io.github.salomax.neotool.banking.domain.transaction.CreditCardBillService
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.TransactionRuleEngine
import io.github.salomax.neotool.banking.domain.transaction.CategorySource
import io.github.salomax.neotool.banking.domain.transaction.SnapshotComputationService
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionRepository
import io.github.salomax.neotool.banking.domain.transaction.TransactionSource
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.banking.infra.assets.BillImportAssetStorage
import io.github.salomax.neotool.banking.infra.extractor.pdf.PdfExtractorSubmittedProducer
import io.github.salomax.neotool.banking.infra.kafka.AssistantBankingReaderRequestedProducer
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
import java.time.YearMonth
import java.util.UUID

private const val MAX_PDF_FILE_SIZE_BYTES = 20 * 1024 * 1024 // 20 MB
private const val RECORD_TYPE_CARD_SUMMARY = "PDF_CARD_SUMMARY"
private const val RECORD_TYPE_CARD_TRANSACTION = "PDF_CARD_TRANSACTION"

@Singleton
open class BillImportService(
    private val repository: BillImportJobRepository,
    private val financialAccountRepository: FinancialAccountRepository,
    private val transactionRepository: TransactionRepository,
    private val importFingerprintService: ImportFingerprintService,
    private val categorizationService: TransactionRuleEngine,
    private val snapshotComputationService: SnapshotComputationService,
    private val creditCardBillService: CreditCardBillService,
    private val billImportCommitPreparer: BillImportCommitPreparer,
    private val assistantBillReviewBuilder: AssistantBillReviewBuilder,
    private val billAssetStorage: BillImportAssetStorage,
    private val submittedProducer: PdfExtractorSubmittedProducer?,
    private val assistantRequestProducer: AssistantBankingReaderRequestedProducer?,
    private val objectMapper: ObjectMapper,
) {
    private val billInputStrategies: List<BillInputStrategy> =
        listOf(
            DeterministicOfxBillInputStrategy(repository, objectMapper),
            CsvBillInputStrategy(repository, objectMapper),
            ExcelBillInputStrategy(repository, objectMapper, assistantRequestProducer = assistantRequestProducer),
            AsyncPdfBillInputStrategy(repository, billAssetStorage, submittedProducer),
        )

    @Transactional
    open fun submitBillImport(
        accountId: UUID,
        userId: UUID,
        importType: ImportDocumentType,
        financialAccountId: UUID?,
        fileName: String,
        fileBytes: ByteArray,
        password: String? = null,
    ): BillImportJob {
        validateInputs(fileName, fileBytes, importType)
        validateFinancialAccountOwnership(financialAccountId, accountId, importType)

        val fileHash = sha256Hex(fileBytes)
        val existing = repository.findByAccountIdAndFileHash(accountId, fileHash)
        if (existing != null) {
            when {
                existing.status == BillImportJobStatus.DONE ->
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

        val strategy =
            billInputStrategies
                .sortedByDescending { it.priority }
                .firstOrNull { it.supports(fileName, importType) }
                ?: throw ValidationException(
                    CommonErrorCode.INVALID_INPUT,
                    field = "fileName",
                    parameters = mapOf("reason" to "Unsupported bill import file type"),
                )

        return strategy.submit(
            BillImportSubmissionRequest(
                accountId = accountId,
                userId = userId,
                importType = importType,
                financialAccountId = financialAccountId,
                fileName = fileName,
                fileHash = fileHash,
                fileBytes = fileBytes,
                password = password,
                existing = existing,
            )
        )
    }

    private fun isRetryableExistingJob(existing: BillImportJob): Boolean =
        existing.status == BillImportJobStatus.FAILED ||
            existing.status == BillImportJobStatus.AWAITING_REVIEW

    open fun getJob(
        jobId: UUID,
        accountId: UUID,
    ): BillImportJob? = repository.findById(jobId, accountId)

    open fun listJobs(
        financialAccountId: UUID?,
        accountId: UUID,
    ): List<BillImportJob> =
        if (financialAccountId == null) {
            repository.findByAccount(accountId)
        } else {
            repository.findByFinancialAccount(financialAccountId, accountId)
        }

    @Transactional
    open fun markExtracting(
        jobId: UUID,
        accountId: UUID,
    ): BillImportJob? {
        val job = repository.findById(jobId, accountId) ?: return null
        return repository.update(
            job.copy(
                status = BillImportJobStatus.EXTRACTING,
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
    ): BillImportJob? {
        val job = repository.findById(jobId, accountId) ?: return null
        val metadataJson = extractionMetadata?.let { objectMapper.writeValueAsString(it) }
        return repository.update(
            job.copy(
                status = BillImportJobStatus.EXTRACTED,
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
    ): BillImportJob? {
        val job = repository.findById(jobId, accountId) ?: return null
        return repository.update(
            job.copy(
                status = BillImportJobStatus.LLM_PROCESSING,
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
    ): BillImportJob? {
        markExtracted(jobId, accountId, extractedText, extractionMetadata) ?: return null
        return markLlmProcessing(jobId, accountId)
    }

    @Transactional
    open fun markAwaitingReview(
        jobId: UUID,
        accountId: UUID,
        llmOutput: Any?,
        assistantResponse: Any? = llmOutput,
    ): BillImportJob? {
        val job = repository.findById(jobId, accountId) ?: return null
        val reviewPayloadJson = buildReviewPayloadFromAssistantOutput(job, llmOutput, accountId)
        val assistantResponseJson = assistantResponse?.let { objectMapper.writeValueAsString(it) }
        return repository.update(
            job.copy(
                status = BillImportJobStatus.AWAITING_REVIEW,
                assistantResponse = assistantResponseJson,
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
    ): BillImportJob? {
        val job = repository.findById(jobId, accountId) ?: return null
        val enrichedCanonical = enrichCanonicalWithCardMatches(canonicalOutput, accountId)
        val reviewPayloadJson = objectMapper.writeValueAsString(enrichedCanonical)
        return repository.update(
            job.copy(
                status = BillImportJobStatus.AWAITING_REVIEW,
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
    ): BillImportReview? =
        runCatching { parsePdfReviewPayload(reviewPayload, accountId) }
            .getOrNull()
            ?.let { enrichReviewPayloadWithCategorizationSuggestions(it, accountId) }

    @Transactional
    open fun confirmBillImport(
        accountId: UUID,
        userId: UUID,
        command: ConfirmBillImportCommand,
    ): BillImportJob {
        val job =
            repository.findById(command.jobId, accountId)
                ?: throw NoSuchElementException("BillImportJob ${command.jobId} not found")
        if (job.status == BillImportJobStatus.DONE) {
            return job
        }
        if (job.status != BillImportJobStatus.AWAITING_REVIEW) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "jobId",
                parameters = mapOf("reason" to "Bill import job is not in AWAITING_REVIEW"),
            )
        }
        if (job.importType != ImportDocumentType.CREDIT_CARD_BILL) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "jobId",
                parameters = mapOf("reason" to "confirmBillImport only supports CREDIT_CARD_BILL jobs"),
            )
        }

        val tenantAccountsById =
            financialAccountRepository.findByAccountId(accountId)
                .associateBy { requireNotNull(it.id) { "FinancialAccount id is required" } }
        val reviewPayload = parsePdfReviewPayload(job.reviewPayload, accountId)
        val preparedCommit = billImportCommitPreparer.prepare(command, tenantAccountsById, reviewPayload)

        val importingJob =
            repository.update(
                job.copy(
                    status = BillImportJobStatus.IMPORTING,
                    updatedAt = Instant.now(),
                ),
            )

        return try {
            val affectedBills = mutableSetOf<UUID>()
            val snapshotRecomputeByAccount = mutableMapOf<UUID, Pair<Int, Int>>()

            preparedCommit.cards.forEach { card ->
                val account = card.financialAccount
                val financialAccountId = requireNotNull(account.id) { "FinancialAccount id is required" }
                val reviewCard = card.reviewCard
                val cycleAnchor = card.closingDate
                var cardBillIdResolved = false
                var cardBillId: UUID? = null
                fun resolveCardBillId(): UUID? {
                    if (!cardBillIdResolved) {
                        cardBillId =
                            creditCardBillService.resolveBillForReviewedCycle(
                                account = account,
                                closingDate = card.closingDate,
                                dueDate = card.dueDate,
                                type = TransactionType.CARD,
                            )
                        cardBillIdResolved = true
                    }
                    return cardBillId
                }

                card.transactions
                    .forEach { tx ->
                        val amountCents = toAmountCents(tx.amount)

                        val now = Instant.now()
                        if (tx.isBillPayment) {
                            val fingerprint =
                                importFingerprintService.compute(
                                    financialAccountId = financialAccountId,
                                    date = tx.date,
                                    amountCents = amountCents,
                                    normalizedDescription = tx.description,
                                    creditDebitType = CreditDebitType.CREDIT,
                                )
                            val duplicate = importFingerprintService.isDuplicate(financialAccountId, fingerprint)
                            if (duplicate) return@forEach

                            // Bill payment = TRANSFER pair: CREDIT on card, DEBIT on linked bank (if configured)
                            val cardSide =
                                transactionRepository.save(
                                    Transaction(
                                        financialAccountId = financialAccountId,
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
                                        cardIdentifier = normalizedCardIdentifier(reviewCard?.lastFourDigits),
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
                            // tx.date is the original purchase date (year-inference already applied at review time).
                            // cardBillId was resolved once above for this card — all transactions share the same bill.
                            val transactionDate =
                                normalizeInstallmentDate(
                                    date = tx.date,
                                    installment = tx.installment,
                                    cycleAnchor = cycleAnchor,
                                )
                            val billId = resolveCardBillId()
                            val fingerprint =
                                if (billId != null) {
                                    importFingerprintService.computeForBill(
                                        financialAccountId = financialAccountId,
                                        billId = billId,
                                        amountCents = amountCents,
                                        normalizedDescription = tx.description,
                                    )
                                } else {
                                    importFingerprintService.compute(
                                        financialAccountId = financialAccountId,
                                        date = transactionDate,
                                        amountCents = amountCents,
                                        normalizedDescription = tx.description,
                                        creditDebitType = tx.creditDebitType,
                                    )
                                }
                            val duplicate =
                                if (billId != null) {
                                    importFingerprintService.isDuplicateForBill(financialAccountId, billId, fingerprint)
                                } else {
                                    importFingerprintService.isDuplicate(financialAccountId, fingerprint)
                            }
                            if (duplicate) return@forEach

                            val (effectiveCategoryId, effectiveCategorySource, shouldLearnImplicitRule) =
                                when {
                                    tx.categorySource == CategorySource.MANUAL && tx.categoryId != null -> Triple(
                                        tx.categoryId,
                                        CategorySource.MANUAL,
                                        true,
                                    )
                                    tx.categorySource == CategorySource.RULE && tx.categoryId != null -> Triple(
                                        tx.categoryId,
                                        CategorySource.RULE,
                                        false,
                                    )
                                    tx.categoryId == null -> {
                                        // Empty category => re-run rules on confirm (CAT-11 idempotency).
                                        val suggestion =
                                            categorizationService.suggest(
                                                accountId = accountId,
                                                description = tx.description,
                                                creditDebitType = tx.creditDebitType,
                                            )
                                        Triple(
                                            suggestion?.categoryId,
                                            suggestion?.let { CategorySource.RULE },
                                            false,
                                        )
                                    }
                                    else -> {
                                        // categoryId present but categorySource missing: treat as MANUAL override.
                                        val source = tx.categorySource ?: CategorySource.MANUAL
                                        Triple(
                                            tx.categoryId,
                                            source,
                                            source == CategorySource.MANUAL,
                                        )
                                    }
                                }

                            if (shouldLearnImplicitRule && effectiveCategoryId != null) {
                                categorizationService.learnFromManualAssignment(
                                    accountId = accountId,
                                    description = tx.description,
                                    categoryId = effectiveCategoryId,
                                    merchantId = null,
                                )
                            }

                            val saved =
                                transactionRepository.save(
                                    Transaction(
                                        financialAccountId = financialAccountId,
                                        accountId = accountId,
                                        createdByUserId = userId,
                                        belongsToUserId = userId,
                                        creditDebitType = tx.creditDebitType,
                                        type = TransactionType.CARD,
                                        status = TransactionStatus.POSTED,
                                        source = TransactionSource.PDF,
                                        amount = tx.amount,
                                        currencyCode = "BRL",
                                        date = transactionDate,
                                        description = tx.description.trim(),
                                        billId = billId,
                                        installmentNumber = tx.installment?.current,
                                        totalInstallments = tx.installment?.total,
                                        cardIdentifier = normalizedCardIdentifier(reviewCard?.lastFourDigits),
                                        importFingerprint = fingerprint,
                                        importJobId = command.jobId,
                                        categoryId = effectiveCategoryId,
                                        categorySource = effectiveCategorySource,
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
                    status = BillImportJobStatus.DONE,
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
                    status = BillImportJobStatus.FAILED,
                    errorMessage = e.message ?: "PDF import commit failed",
                    errorDetails =
                        objectMapper.writeValueAsString(
                            buildErrorDetailsEnvelope(
                                errorCode = errorCode,
                                errorParameters = errorParameters,
                                errorMessage = e.message,
                                errorDetails = mapOf("source" to "confirmBillImport"),
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
    ): BillImportJob? {
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
                status = BillImportJobStatus.FAILED,
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
        importType: ImportDocumentType,
    ) {
        val lowerName = fileName.lowercase()
        if (billInputStrategies.any { it.supports(fileName, importType) && !lowerName.endsWith(".pdf") }) {
            return
        }
        if (!lowerName.endsWith(".pdf")) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "fileName",
                parameters = mapOf("reason" to "File extension must be .pdf, .ofx, .csv, .xls, or .xlsx"),
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
        importType: ImportDocumentType,
    ) {
        if (financialAccountId == null) {
            if (importType == ImportDocumentType.BANK_STATEMENT) {
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
        if (importType == ImportDocumentType.BANK_STATEMENT && account.type != FinancialAccountType.BANK) {
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
        val normalizedInput = normalizeLlmBillReviewPayload(llmOutput)
        val parsed =
            runCatching { objectMapper.convertValue(normalizedInput, BillImportReview::class.java) }.getOrNull()
                ?: return objectMapper.writeValueAsString(normalizedInput)
        return objectMapper.writeValueAsString(applyCardMatches(parsed, accountId))
    }

    private fun buildReviewPayloadFromAssistantOutput(
        job: BillImportJob,
        assistantOutput: Any?,
        accountId: UUID,
    ): String? {
        assistantBillReviewBuilder.build(job.extractedText, assistantOutput)?.let { builtReview ->
            return objectMapper.writeValueAsString(applyCardMatches(builtReview, accountId))
        }
        return enrichReviewPayloadWithCardMatches(assistantOutput, accountId)
    }

    private fun enrichReviewPayloadWithCategorizationSuggestions(
        payload: BillImportReview,
        accountId: UUID,
    ): BillImportReview {
        // Load rules once per preview call (avoid N+1 rule loading).
        val rules = categorizationService.preloadRules(accountId)

        return payload.copy(
            cards =
                payload.cards.map { card ->
                    card.copy(
                        transactions =
                            card.transactions.map { tx ->
                                if (tx.isBillPayment) {
                                    tx.copy(categoryId = null, categorySource = null)
                                } else {
                                    val suggestion = categorizationService.suggestFromRules(rules, tx.description, tx.creditDebitType)
                                    tx.copy(
                                        categoryId = suggestion?.categoryId,
                                        categorySource = suggestion?.let { CategorySource.RULE },
                                    )
                                }
                            },
                    )
                },
        )
    }

    private fun normalizeLlmBillReviewPayload(llmOutput: Any?): Any? {
        val root = llmOutput as? Map<*, *> ?: return llmOutput
        val normalizedRoot = root.toMutableStringKeyMap()
        val billClosingDate = normalizedRoot["closing_date"]?.toString()?.takeIf { it.isNotBlank() }
        val billDueDate = normalizedRoot["due_date"]?.toString()?.takeIf { it.isNotBlank() }
        val rawCards = normalizedRoot["cards"] as? List<*> ?: return normalizedRoot

        normalizedRoot["cards"] =
            rawCards.mapIndexed { index, rawCard ->
                val card = (rawCard as? Map<*, *>)?.toMutableStringKeyMap() ?: return@mapIndexed rawCard
                if (card["review_card_id"] == null) {
                    card["review_card_id"] = "card-${index + 1}"
                }
                if (card["closing_date"] == null && billClosingDate != null) {
                    card["closing_date"] = billClosingDate
                }
                if (card["due_date"] == null && billDueDate != null) {
                    card["due_date"] = billDueDate
                }
                card
            }

        return normalizedRoot
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
        payload: BillImportReview,
        accountId: UUID,
    ): BillImportReview {
        val cache = mutableMapOf<String, CardMatchProjection>()
        val enrichedCards =
            payload.cards.mapIndexed { index, card ->
                val cycleAnchorDate = cardCycleAnchorDate(card, payload.statementPeriod)
                val sanitizedTransactions =
                    card.transactions.map { transaction ->
                        val transactionDate = transaction.date ?: return@map transaction
                        val normalizedDate =
                            normalizeInstallmentDate(
                                date = transactionDate,
                                installment = transaction.installment,
                                cycleAnchor = cycleAnchorDate,
                            )
                        if (normalizedDate == transactionDate) {
                            transaction
                        } else {
                            transaction.copy(date = normalizedDate)
                        }
                    }
                val reviewCardId = card.reviewCardId.ifBlank { "card-${index + 1}" }
                val normalizedLastFour = normalizeLastFourDigits(card.lastFourDigits.orEmpty())
                if (normalizedLastFour.length != 4) {
                    card.copy(
                        reviewCardId = reviewCardId,
                        lastFourDigits = normalizedLastFour.ifBlank { null },
                        transactions = sanitizedTransactions,
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
                        reviewCardId = reviewCardId,
                        lastFourDigits = normalizedLastFour,
                        transactions = sanitizedTransactions,
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
                            match.id?.let { BillImportMatchCandidate(id = it, name = match.name) }
                        },
                )
        }
    }

    private fun parsePdfReviewPayload(
        reviewPayload: String?,
        accountId: UUID,
    ): BillImportReview {
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
            return applyCardMatches(canonicalToBillImportReview(canonical), accountId)
        }

        val llmStyle =
            runCatching {
                objectMapper.readValue(
                    reviewPayload,
                    BillImportReview::class.java,
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

    private fun canonicalToBillImportReview(canonical: CanonicalReaderOutput): BillImportReview {
        val cards = linkedMapOf<String, CanonicalCardAccumulator>()

        canonical.normalizedRecords.forEachIndexed { index, record ->
            val type = record["record_type"]?.toString() ?: return@forEachIndexed
            when (type) {
                RECORD_TYPE_CARD_SUMMARY -> {
                    val lastFour = normalizeLastFourDigits(record["card_last_four_digits"]?.toString().orEmpty())
                    val reviewCardId = record["review_card_id"]?.toString()?.takeIf { it.isNotBlank() }
                    val key = reviewCardId ?: lastFour.ifBlank { "__unknown_summary_$index" }
                    val card = cards.getOrPut(key) { CanonicalCardAccumulator(reviewCardId = key, lastFourDigits = lastFour.ifBlank { null }) }
                    card.reviewCardId = key
                    card.lastFourDigits = lastFour
                    card.declaredTotal = record["declared_total"].toBigDecimalOrNull() ?: card.declaredTotal
                    card.computedTotal = record["computed_total"].toBigDecimalOrNull() ?: card.computedTotal
                    card.totalMatchesDeclared =
                        record["total_matches_declared"].toBooleanOrNull()
                            ?: card.totalMatchesDeclared
                    card.matchedFinancialAccountId =
                        record["matched_financial_account_id"].toUuidOrNull() ?: card.matchedFinancialAccountId
                    card.matchAmbiguous = record["match_ambiguous"].toBooleanOrNull() ?: card.matchAmbiguous
                    card.matchCandidates = record["match_candidates"].toMatchCandidates()
                }
                RECORD_TYPE_CARD_TRANSACTION -> {
                    val lastFour = normalizeLastFourDigits(record["card_last_four_digits"]?.toString().orEmpty())
                    val reviewCardId = record["review_card_id"]?.toString()?.takeIf { it.isNotBlank() }
                    val key = reviewCardId ?: lastFour.ifBlank { "__unknown_tx_$index" }
                    val card = cards.getOrPut(key) { CanonicalCardAccumulator(reviewCardId = key, lastFourDigits = lastFour.ifBlank { null }) }
                    card.reviewCardId = key
                    val transaction =
                        BillImportReviewTransaction(
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

                BillImportReviewCard(
                    reviewCardId = card.reviewCardId,
                    lastFourDigits = card.lastFourDigits,
                    declaredTotal = declaredIfMissing,
                    computedTotal = computedIfMissing,
                    totalMatchesDeclared = totalMatches,
                    transactions = card.transactions.sortedWith(compareBy({ it.date }, { it.description })),
                    matchedFinancialAccountId = card.matchedFinancialAccountId,
                    matchAmbiguous = card.matchAmbiguous,
                    matchCandidates = card.matchCandidates,
                )
            }

        return BillImportReview(
            bankName = canonical.rawMetadata["bank_name"]?.toString(),
            reviewMode = BillImportReviewMode.CARD_BUCKET_MATCHING,
            statementPeriod =
                DateRange(
                    from = canonical.rawMetadata["statement_from"].toLocalDateOrNull(),
                    to = canonical.rawMetadata["statement_to"].toLocalDateOrNull(),
                ),
            cards = mappedCards,
        )
    }

    private fun computeSignedTotal(transactions: List<BillImportReviewTransaction>): BigDecimal =
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

    private fun Map<*, *>.toMutableStringKeyMap(): MutableMap<String, Any?> =
        entries.associate { (key, value) -> key.toString() to value }.toMutableMap()

    private fun Any?.toCreditDebitTypeOrDefault(): CreditDebitType =
        runCatching { CreditDebitType.valueOf(this?.toString().orEmpty()) }.getOrDefault(CreditDebitType.DEBIT)

    private fun Any?.toMatchCandidates(): List<BillImportMatchCandidate> =
        when (this) {
            is List<*> ->
                this.mapNotNull { item ->
                    val map = item as? Map<*, *> ?: return@mapNotNull null
                    val id = map["id"].toUuidOrNull() ?: return@mapNotNull null
                    val name = map["name"]?.toString() ?: return@mapNotNull null
                    BillImportMatchCandidate(id = id, name = name)
                }
            else -> emptyList()
        }

    private fun normalizedCardIdentifier(lastFourDigits: String?): String? =
        normalizeLastFourDigits(lastFourDigits.orEmpty()).takeIf { it.length == 4 }

    private data class CardMatchProjection(
        val matchedFinancialAccountId: UUID?,
        val matchAmbiguous: Boolean,
        val matchCandidates: List<BillImportMatchCandidate>,
    ) {
        fun toCanonicalFields(): Map<String, Any?> =
            mapOf(
                "matched_financial_account_id" to matchedFinancialAccountId,
                "match_ambiguous" to matchAmbiguous,
                "match_candidates" to matchCandidates.map { mapOf("id" to it.id, "name" to it.name) },
            )
    }

    private data class CanonicalCardAccumulator(
        var reviewCardId: String,
        var lastFourDigits: String?,
        var declaredTotal: BigDecimal = BigDecimal.ZERO,
        var computedTotal: BigDecimal = BigDecimal.ZERO,
        var totalMatchesDeclared: Boolean = false,
        var matchedFinancialAccountId: UUID? = null,
        var matchAmbiguous: Boolean = false,
        var matchCandidates: List<BillImportMatchCandidate> = emptyList(),
        val transactions: MutableList<BillImportReviewTransaction> = mutableListOf(),
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

    private fun cardCycleAnchorDate(
        card: BillImportReviewCard?,
        statementPeriod: DateRange?,
    ): LocalDate? = card?.closingDate ?: card?.dueDate ?: statementPeriod?.to ?: statementPeriod?.from

    private fun normalizeInstallmentDate(
        date: LocalDate,
        installment: InstallmentInfo?,
        cycleAnchor: LocalDate?,
    ): LocalDate {
        if (installment == null || installment.current < 1) {
            return date
        }
        val anchor = cycleAnchor ?: LocalDate.now()
        val expectedYearMonth = YearMonth.from(anchor).minusMonths(installment.current.toLong())
        val parsedYearMonth = YearMonth.from(date)
        val isTooRecentForInstallment = parsedYearMonth.isAfter(expectedYearMonth.plusMonths(1))
        if (!date.isAfter(anchor) && !isTooRecentForInstallment) {
            return date
        }
        var normalized = safeDate(expectedYearMonth.year, expectedYearMonth.monthValue, date.dayOfMonth)
        while (normalized.isAfter(anchor)) {
            normalized = safeDate(normalized.year - 1, normalized.monthValue, normalized.dayOfMonth)
        }
        return normalized
    }

    private fun safeDate(
        year: Int,
        month: Int,
        day: Int,
    ): LocalDate {
        val normalizedMonth = month.coerceIn(1, 12)
        val maxDay = YearMonth.of(year, normalizedMonth).lengthOfMonth()
        return LocalDate.of(year, normalizedMonth, day.coerceIn(1, maxDay))
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
