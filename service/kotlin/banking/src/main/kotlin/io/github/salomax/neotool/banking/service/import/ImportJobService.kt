package io.github.salomax.neotool.banking.service.import

import io.github.salomax.neotool.banking.domain.import.*
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.repo.import.ImportJobRepository
import io.github.salomax.neotool.banking.repo.import.nfce.NfceDocumentRepository
import io.github.salomax.neotool.banking.repo.import.nfce.ProductAliasEntityRepository
import io.github.salomax.neotool.banking.repo.import.nfce.ProductVariantEntityRepository
import io.github.salomax.neotool.banking.repo.import.nfce.PurchaseItemEntityRepository
import io.github.salomax.neotool.banking.repo.import.nfce.PurchaseItemRepository
import io.github.salomax.neotool.banking.service.import.dedup.CrossSourceDedupService
import io.github.salomax.neotool.banking.service.import.dedup.SameSourceDedupService
import io.github.salomax.neotool.banking.client.import.nfce.NfceClient
import io.github.salomax.neotool.banking.domain.import.nfce.NfceData
import io.github.salomax.neotool.banking.domain.import.nfce.NfceDocument
import io.github.salomax.neotool.banking.domain.import.nfce.NfceItem
import io.github.salomax.neotool.banking.domain.import.nfce.PurchaseItem
import io.github.salomax.neotool.banking.client.assets.ImportFileStoragePort
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.service.transaction.SnapshotComputationService
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.github.salomax.neotool.banking.domain.transaction.TransactionSource
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.banking.client.import.messaging.ImportFileProducer
import io.github.salomax.neotool.banking.client.import.messaging.ImportFileRequestedEvent
import io.github.salomax.neotool.banking.service.import.parser.ImportFileFormat
import io.github.salomax.neotool.banking.service.import.parser.ImportFormatDiscoveryService
import io.github.salomax.neotool.common.error.BusinessRuleException
import io.github.salomax.neotool.common.error.ConflictException
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import org.hibernate.exception.ConstraintViolationException
import org.postgresql.util.PSQLException
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

private const val DEFAULT_MIME = "application/octet-stream"
private const val DUPLICATE_FILE_HASH_CONSTRAINT = "uq_document_import_jobs_transaction_account_hash"
private const val DEFAULT_TRANSACTION_IMPORT_DOCUMENT_TYPE = "BANK_STATEMENT"

private data class NfceReviewMetadata(
    val qrCodeUrl: String,
)

@Singleton
open class ImportJobService(
    private val importJobRepository: ImportJobRepository,
    private val fileStorage: ImportFileStoragePort,
    private val importFileProducer: ImportFileProducer?,
    private val nfceClient: NfceClient,
    private val importProcessor: ImportProcessor,
    private val financialAccountRepository: FinancialAccountRepository,
    private val crossSourceDedupService: CrossSourceDedupService,
    private val sameSourceDedupService: SameSourceDedupService,
    private val transactionRepository: TransactionRepository,
    private val snapshotComputationService: SnapshotComputationService,
    private val nfceDocumentRepository: NfceDocumentRepository,
    private val purchaseItemRepository: PurchaseItemRepository,
    private val productVariantEntityRepository: ProductVariantEntityRepository,
    private val productAliasEntityRepository: ProductAliasEntityRepository,
    private val purchaseItemEntityRepository: PurchaseItemEntityRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper = jacksonObjectMapper().apply { registerModule(JavaTimeModule()) }
    private val importFormatDiscoveryService = ImportFormatDiscoveryService()

    /**
     * Initiates file import: validates ownership, Level 3 idempotency by file hash,
     * stores file, creates PENDING job, publishes [ImportFileRequestedEvent].
     */
    open fun initiateFileImport(
        financialAccountId: UUID,
        accountId: UUID,
        userId: UUID,
        fileBytes: ByteArray,
        fileName: String,
        source: ImportSource?,
    ): ImportJob {
        val producer =
            importFileProducer
                ?: throw BusinessRuleException(
                    errorCode = BankingErrorCode.IMPORT_ASYNC_PIPELINE_UNAVAILABLE,
                    parameters = mapOf("kafkaEnabled" to false),
                )

        validateFinancialAccountOwnership(financialAccountId, accountId)
        val fileHash = sha256Hex(fileBytes)
        val discovery = importFormatDiscoveryService.discover(fileBytes, fileName)
        val resolvedSource =
            source ?: when (discovery.format) {
                ImportFileFormat.OFX -> ImportSource.OFX
                ImportFileFormat.CSV -> discovery.detectedSource?.takeIf { it.isCsvSource() } ?: ImportSource.CSV_AUTO
                else -> ImportSource.AUTO
            }

        val existing = importJobRepository.findByFileHash(financialAccountId, fileHash)
        if (existing != null) {
            return handleExistingImportJob(
                existing = existing,
                financialAccountId = financialAccountId,
                accountId = accountId,
                userId = userId,
                fileHash = fileHash,
                fileBytes = fileBytes,
                fileName = fileName,
                source = resolvedSource,
                producer = producer,
            )
        }

        val now = Instant.now()
        val job =
            ImportJob(
                financialAccountId = financialAccountId,
                accountId = accountId,
                createdByUserId = userId,
                source = resolvedSource,
                documentType = DEFAULT_TRANSACTION_IMPORT_DOCUMENT_TYPE,
                fileName = fileName,
                fileHash = fileHash,
                status = ImportJobStatus.PENDING,
                createdAt = now,
                updatedAt = now,
            )
        val saved =
            try {
                importJobRepository.save(job)
            } catch (e: Exception) {
                if (isDuplicateFileHashConstraintViolation(e)) {
                    val concurrentExisting =
                        importJobRepository.findByFileHash(financialAccountId, fileHash)
                            ?: importJobRepository.findLatestByAccountAndFileHash(accountId, fileHash)
                    if (concurrentExisting != null && concurrentExisting.financialAccountId == financialAccountId) {
                        return handleExistingImportJob(
                            existing = concurrentExisting,
                            financialAccountId = financialAccountId,
                            accountId = accountId,
                            userId = userId,
                            fileHash = fileHash,
                            fileBytes = fileBytes,
                            fileName = fileName,
                            source = resolvedSource,
                            producer = producer,
                        )
                    }
                    throw duplicateFileHashConflict(
                        financialAccountId = financialAccountId,
                        fileHash = fileHash,
                        existing = concurrentExisting,
                        cause = e,
                    )
                }
                throw e
            }
        return enqueueImportJob(saved, financialAccountId, accountId, fileBytes, fileName, resolvedSource, producer)
    }

    /**
     * Synchronous NFC-e import: fetches from SEFAZ, Level 1 dedup on chNFe,
     * creates one transaction and a COMPLETED import job.
     */
    @Transactional
    open fun importNfce(
        financialAccountId: UUID,
        accountId: UUID,
        userId: UUID,
        qrCodeUrl: String,
    ): ImportJob {
        validateFinancialAccountOwnership(financialAccountId, accountId)
        val nfce = nfceClient.fetch(qrCodeUrl)
        val existingJob = importJobRepository.findByFileHash(financialAccountId, nfce.chNFe)
        if (existingJob != null) return existingJob

        if (sameSourceDedupService.isDuplicate(financialAccountId, nfce.chNFe, TransactionSource.MANUAL)) {
            return createSkippedNfceJob(financialAccountId, accountId, userId, nfce.chNFe)
        }

        val now = Instant.now()
        val nfceDate = LocalDate.ofInstant(nfce.dhEmi, ZoneId.systemDefault())
        val manualCandidates =
            crossSourceDedupService.findManualNfceCandidates(
                financialAccountId = financialAccountId,
                amount = nfce.vNF,
                referenceDate = nfceDate,
            )
        if (manualCandidates.isNotEmpty()) {
            val reviewJob =
                ImportJob(
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    source = ImportSource.NFC_E,
                    fileName = null,
                    fileHash = nfce.chNFe,
                    status = ImportJobStatus.REVIEW_REQUIRED,
                    extractionMetadata = objectMapper.writeValueAsString(NfceReviewMetadata(qrCodeUrl)),
                    totalRows = 1,
                    importedCount = 0,
                    skippedCount = 0,
                    duplicateReviewCount = 1,
                    errorCount = 0,
                    reviewPayload =
                        ImportReviewPayloadMapper.toJson(
                            objectMapper = objectMapper,
                            duplicateCandidates =
                                listOf(
                                    ImportDuplicateCandidate(
                                        rowIndex = 0,
                                        parsed = nfceToParsedTransaction(nfce),
                                        candidates = manualCandidates,
                                    ),
                                ),
                            gate = mapOf("kind" to "nfce_manual_link"),
                        ),
                    dateFrom = nfceDate,
                    dateTo = nfceDate,
                    createdAt = now,
                    updatedAt = now,
                )
            return importJobRepository.save(reviewJob)
        }

        val job =
            ImportJob(
                financialAccountId = financialAccountId,
                accountId = accountId,
                createdByUserId = userId,
                source = ImportSource.NFC_E,
                fileName = null,
                fileHash = nfce.chNFe,
                status = ImportJobStatus.PENDING,
                totalRows = 0,
                importedCount = 0,
                skippedCount = 0,
                duplicateReviewCount = 0,
                errorCount = 0,
                extractionMetadata = objectMapper.writeValueAsString(NfceReviewMetadata(qrCodeUrl)),
                dateFrom = nfceDate,
                dateTo = nfceDate,
                createdAt = now,
                updatedAt = now,
            )
        val pendingJob = importJobRepository.save(job)
        val savedTransaction = createNfceTransaction(pendingJob, nfce, qrCodeUrl, now)
        val savedJob =
            importJobRepository.update(
                pendingJob.copy(
                    status = ImportJobStatus.COMPLETED,
                    totalRows = 1,
                    importedCount = 1,
                    skippedCount = 0,
                    duplicateReviewCount = 0,
                    errorCount = 0,
                    completedAt = now,
                    updatedAt = now,
                ),
            )
        try {
            snapshotComputationService.recomputeChain(
                financialAccountId = financialAccountId,
                accountId = accountId,
                fromYear = savedTransaction.date.year,
                fromMonth = savedTransaction.date.monthValue,
            )
        } catch (e: Exception) {
            log.warn("Snapshot recompute failed after NFC-e import; sweeper will heal: jobId={}", savedJob.id, e)
        }
        return savedJob
    }

    open fun getJob(
        id: UUID,
        accountId: UUID,
    ): ImportJob? = importJobRepository.findById(id, accountId)

    open fun listJobs(
        financialAccountId: UUID?,
        accountId: UUID,
    ): List<ImportJob> =
        if (financialAccountId != null) {
            importJobRepository.findByFinancialAccount(financialAccountId, accountId)
        } else {
            importJobRepository.findByAccount(accountId)
        }

    open fun listPendingReviews(
        accountId: UUID,
        financialAccountId: UUID?,
    ): List<ImportJob> = importJobRepository.findPendingReviews(accountId, financialAccountId)

    /**
     * Cancels a [ImportJobStatus.REVIEW_REQUIRED] job, discarding unresolved candidates.
     * Sets status to [ImportJobStatus.REVIEW_CANCELLED] and clears [ImportJob.reviewPayload].
     */
    @Transactional
    open fun cancelImportReview(
        jobId: UUID,
        accountId: UUID,
    ) {
        val job =
            importJobRepository.findById(jobId, accountId)
                ?: throw NoSuchElementException("ImportJob not found: $jobId")
        require(job.status == ImportJobStatus.REVIEW_REQUIRED) {
            "Import job must be in REVIEW_REQUIRED status to cancel, got ${job.status}"
        }
        importJobRepository.update(
            job.copy(
                status = ImportJobStatus.REVIEW_CANCELLED,
                reviewPayload = null,
                updatedAt = Instant.now(),
            ),
        )
    }

    /**
     * Resolves one duplicate candidate. Job must be [ImportJobStatus.REVIEW_REQUIRED].
     * When all candidates are resolved, job status is set to COMPLETED.
     */
    @Transactional
    open fun resolveImportDuplicate(
        jobId: UUID,
        incomingId: Int,
        resolution: DuplicateResolution,
        accountId: UUID,
    ): ImportJob {
        val job =
            importJobRepository.findById(jobId, accountId)
                ?: throw NoSuchElementException("ImportJob not found: $jobId")
        require(job.status == ImportJobStatus.REVIEW_REQUIRED) {
            "Import job must be in REVIEW_REQUIRED status, got ${job.status}"
        }
        val payload = job.reviewPayload ?: throw IllegalStateException("Job has no review payload")
        val reviewPayload =
            ImportReviewPayloadMapper.fromJson(objectMapper, payload)
                ?: throw IllegalStateException("Job review payload is invalid")
        val candidates = reviewPayload.duplicateCandidates
        require(candidates.isNotEmpty()) { "Job review payload has no duplicate candidates" }
        val index = candidates.indexOfFirst { it.rowIndex == incomingId }
        require(index >= 0) { "Duplicate candidate not found for rowIndex: $incomingId" }
        val candidate = candidates[index]
        val isNfceReview = job.source == ImportSource.NFC_E
        var nfceCache: NfceData? = null
        fun loadNfceIfNeeded(): NfceData {
            check(isNfceReview) { "NFC-e review data requested for non-NFC-e job" }
            return nfceCache ?: loadNfceReviewData(job).also { nfceCache = it }
        }

        when (resolution) {
            DuplicateResolution.USE_INCOMING -> {
                if (isNfceReview) {
                    val existing =
                        candidate.candidates.firstOrNull()
                            ?: throw IllegalStateException("NFC-e review candidate must contain one existing transaction")
                    linkNfceToExistingTransaction(
                        job = job,
                        existing = existing,
                        nfce = loadNfceIfNeeded(),
                        qrCodeUrl = loadNfceReviewMetadata(job).qrCodeUrl,
                        now = Instant.now(),
                    )
                } else {
                    candidate.candidates.forEach { existing ->
                        requireNotNull(existing.id) { "Existing transaction must have id" }
                        val deleted = transactionRepository.softDelete(existing.id, accountId)
                        require(deleted) { "Failed to delete existing transaction ${existing.id}" }
                    }
                    val result =
                        importProcessor.process(
                            job = job,
                            rows = listOf(candidate.parsed),
                            allowCrossSourceDuplicates = true,
                        )
                    require(result.imported.size == 1) { "Expected one imported transaction" }
                }
            }
            DuplicateResolution.KEEP_BOTH -> {
                if (isNfceReview) {
                    createNfceTransaction(
                        job = job,
                        nfce = loadNfceIfNeeded(),
                        qrCodeUrl = loadNfceReviewMetadata(job).qrCodeUrl,
                        now = Instant.now(),
                    )
                } else {
                    val result =
                        importProcessor.process(
                            job = job,
                            rows = listOf(candidate.parsed),
                            allowCrossSourceDuplicates = true,
                        )
                    require(result.imported.size == 1) { "Expected one imported transaction" }
                }
            }
            DuplicateResolution.KEEP_EXISTING -> { /* no insert, no delete */ }
        }

        if (resolution != DuplicateResolution.KEEP_EXISTING) {
            val fromDate =
                when {
                    isNfceReview ->
                        (candidate.candidates.map { it.date } + LocalDate.ofInstant(loadNfceIfNeeded().dhEmi, ZoneId.systemDefault())).min()
                    resolution == DuplicateResolution.USE_INCOMING ->
                        (candidate.candidates.map { it.date } + candidate.parsed.date).min()
                    else -> candidate.parsed.date
                }
            snapshotComputationService.recomputeChain(
                financialAccountId = job.financialAccountId,
                accountId = accountId,
                fromYear = fromDate.year,
                fromMonth = fromDate.monthValue,
            )
        }

        val remaining = candidates.filter { it.rowIndex != incomingId }
        val newPayload =
            ImportReviewPayloadMapper.toJson(
                objectMapper = objectMapper,
                duplicateCandidates = remaining,
                gate = reviewPayload.gate,
            )
        val newDuplicateReviewCount = (job.duplicateReviewCount ?: 0) - 1
        val newImportedCount =
            when (resolution) {
                DuplicateResolution.KEEP_EXISTING -> job.importedCount ?: 0
                else -> (job.importedCount ?: 0) + 1
            }
        val newStatus = if (remaining.isEmpty()) ImportJobStatus.COMPLETED else ImportJobStatus.REVIEW_REQUIRED
        val now = Instant.now()
        val updated =
            job.copy(
                status = newStatus,
                importedCount = newImportedCount,
                duplicateReviewCount = newDuplicateReviewCount,
                reviewPayload = newPayload,
                completedAt = if (remaining.isEmpty()) now else job.completedAt,
                updatedAt = now,
            )
        return importJobRepository.update(updated)
    }

    private fun duplicateFileHashConflict(
        financialAccountId: UUID,
        fileHash: String,
        existing: ImportJob?,
        cause: Exception,
    ): ConflictException =
        ConflictException(
            errorCode = BankingErrorCode.IMPORT_FILE_ALREADY_EXISTS,
            field = "fileBase64",
            parameters =
                mapOf(
                    "financialAccountId" to financialAccountId.toString(),
                    "fileHash" to fileHash,
                    "existingJobId" to (existing?.id?.toString() ?: "unknown"),
                    "existingStatus" to (existing?.status?.name ?: "unknown"),
                    "existingFinancialAccountId" to (existing?.financialAccountId?.toString() ?: "unknown"),
                ),
            cause = cause,
        )

    private fun validateFinancialAccountOwnership(
        financialAccountId: UUID,
        accountId: UUID,
    ) {
        val accounts = financialAccountRepository.findByAccountId(accountId)
        require(accounts.any { it.id == financialAccountId }) {
            "Financial account $financialAccountId does not belong to account $accountId"
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun mimeType(fileName: String): String =
        when {
            fileName.endsWith(".ofx", ignoreCase = true) -> "application/x-ofx"
            fileName.endsWith(".csv", ignoreCase = true) -> "text/csv"
            fileName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
            fileName.endsWith(
                ".xlsx",
                ignoreCase = true,
            ) -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            fileName.endsWith(".xls", ignoreCase = true) -> "application/vnd.ms-excel"
            else -> DEFAULT_MIME
        }

    private fun createSkippedNfceJob(
        financialAccountId: UUID,
        accountId: UUID,
        userId: UUID,
        chNFe: String,
    ): ImportJob {
        val now = Instant.now()
        val job =
            ImportJob(
                financialAccountId = financialAccountId,
                accountId = accountId,
                createdByUserId = userId,
                source = ImportSource.NFC_E,
                fileName = null,
                fileHash = chNFe,
                status = ImportJobStatus.COMPLETED,
                totalRows = 1,
                importedCount = 0,
                skippedCount = 1,
                duplicateReviewCount = 0,
                errorCount = 0,
                completedAt = now,
                createdAt = now,
                updatedAt = now,
            )
        return importJobRepository.save(job)
    }

    private fun handleExistingImportJob(
        existing: ImportJob,
        financialAccountId: UUID,
        accountId: UUID,
        userId: UUID,
        fileHash: String,
        fileBytes: ByteArray,
        fileName: String,
        source: ImportSource,
        producer: ImportFileProducer,
    ): ImportJob {
        return when (existing.status) {
            ImportJobStatus.COMPLETED -> {
                if (!shouldRetryCompletedEmptyOutcome(existing)) {
                    existing
                } else {
                    val reset = resetForRetry(existing, userId, source, fileName)
                    enqueueImportJob(reset, financialAccountId, accountId, fileBytes, fileName, source, producer)
                }
            }
            ImportJobStatus.PROCESSING -> {
                val reset = resetForRetry(existing, userId, source, fileName)
                enqueueImportJob(reset, financialAccountId, accountId, fileBytes, fileName, source, producer)
            }
            ImportJobStatus.FAILED -> {
                val reset = resetForRetry(existing, userId, source, fileName)
                enqueueImportJob(reset, financialAccountId, accountId, fileBytes, fileName, source, producer)
            }
            else -> throw ConflictException(
                errorCode = BankingErrorCode.IMPORT_FILE_ALREADY_EXISTS,
                field = "fileBase64",
                parameters =
                    mapOf(
                        "financialAccountId" to financialAccountId.toString(),
                        "fileHash" to fileHash,
                        "existingJobId" to existing.id?.toString().orEmpty(),
                        "existingStatus" to existing.status.name,
                    ),
            )
        }
    }

    private fun shouldRetryCompletedEmptyOutcome(existing: ImportJob): Boolean {
        val hasOutcomeCounters =
            existing.totalRows != null ||
                existing.importedCount != null ||
                existing.skippedCount != null ||
                existing.duplicateReviewCount != null ||
                existing.errorCount != null
        if (!hasOutcomeCounters) return false

        val imported = existing.importedCount ?: 0
        val skipped = existing.skippedCount ?: 0
        val duplicates = existing.duplicateReviewCount ?: 0
        val errors = existing.errorCount ?: 0
        val totalRows = existing.totalRows ?: 0

        return imported == 0 &&
            skipped == 0 &&
            duplicates == 0 &&
            (errors > 0 || totalRows == 0)
    }

    private fun resetForRetry(
        existing: ImportJob,
        userId: UUID,
        source: ImportSource,
        fileName: String,
    ): ImportJob {
        val now = Instant.now()
        return importJobRepository.update(
            existing.copy(
                createdByUserId = userId,
                source = source,
                fileName = fileName,
                status = ImportJobStatus.PENDING,
                totalRows = null,
                importedCount = null,
                skippedCount = null,
                duplicateReviewCount = null,
                errorCount = null,
                errorDetails = null,
                reviewPayload = null,
                assistantRequestPublishedAt = null,
                dateFrom = null,
                dateTo = null,
                completedAt = null,
                updatedAt = now,
            ),
        )
    }

    private fun enqueueImportJob(
        job: ImportJob,
        financialAccountId: UUID,
        accountId: UUID,
        fileBytes: ByteArray,
        fileName: String,
        source: ImportSource,
        producer: ImportFileProducer,
    ): ImportJob {
        val jobId = requireNotNull(job.id) { "ImportJob must have id before enqueue" }
        return try {
            val assetId = fileStorage.store(accountId, fileBytes, fileName, mimeType(fileName))
            producer.publish(
                ImportFileRequestedEvent(
                    jobId = jobId,
                    assetId = assetId,
                    source = source,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                ),
            )
            job
        } catch (e: Exception) {
            markAsFailedAfterInitiationError(job, e)
            throw BusinessRuleException(
                errorCode = BankingErrorCode.IMPORT_JOB_START_FAILED,
                parameters =
                    mapOf(
                        "jobId" to jobId.toString(),
                        "financialAccountId" to financialAccountId.toString(),
                        "reason" to (e.message ?: "unknown"),
                    ),
                cause = e,
            )
        }
    }

    private fun markAsFailedAfterInitiationError(
        saved: ImportJob,
        e: Exception,
    ) {
        val now = Instant.now()
        val errorDetails =
            objectMapper.writeValueAsString(
                mapOf(
                    "message" to (e.message ?: "Failed to start import job"),
                    "stage" to "initiateFileImport",
                ),
            )
        runCatching {
            importJobRepository.update(
                saved.copy(
                    status = ImportJobStatus.FAILED,
                    errorCount = 1,
                    errorDetails = errorDetails,
                    completedAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    private fun isDuplicateFileHashConstraintViolation(e: Throwable): Boolean {
        var current: Throwable? = e
        while (current != null) {
            when (current) {
                is ConstraintViolationException -> {
                    if (current.constraintName == DUPLICATE_FILE_HASH_CONSTRAINT) return true
                }
                is PSQLException -> {
                    if (current.message?.contains(DUPLICATE_FILE_HASH_CONSTRAINT) == true) return true
                }
            }
            current = current.cause
        }
        return false
    }

    private fun nfceToTransaction(
        nfce: NfceData,
        financialAccountId: UUID,
        accountId: UUID,
        createdByUserId: UUID,
        importJobId: UUID,
        now: Instant,
    ): Transaction {
        val date = LocalDate.ofInstant(nfce.dhEmi, ZoneId.systemDefault())
        return Transaction(
            financialAccountId = financialAccountId,
            accountId = accountId,
            createdByUserId = createdByUserId,
            creditDebitType = CreditDebitType.DEBIT,
            type = TransactionType.OTHER_EXPENSE,
            status = TransactionStatus.POSTED,
            source = TransactionSource.MANUAL,
            amount = nfce.vNF,
            currencyCode = "BRL",
            date = date,
            transactionDatetime = nfce.dhEmi,
            description = nfce.nomeEmitente,
            descriptionRaw = null,
            counterpartyName = nfce.nomeEmitente,
            counterpartyDocument = nfce.cnpjEmitente,
            providerTransactionId = nfce.chNFe,
            importFingerprint = null,
            importJobId = importJobId,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun nfceToParsedTransaction(nfce: NfceData): ParsedTransaction =
        ParsedTransaction(
            providerTransactionId = nfce.chNFe,
            creditDebitType = CreditDebitType.DEBIT,
            amount = nfce.vNF,
            date = LocalDate.ofInstant(nfce.dhEmi, ZoneId.systemDefault()),
            transactionDatetime = nfce.dhEmi,
            description = nfce.nomeEmitente,
            descriptionRaw = nfce.nomeEmitente,
            counterpartyName = nfce.nomeEmitente,
            counterpartyDocument = nfce.cnpjEmitente.filter(Char::isDigit),
            currencyCode = "BRL",
        )

    private fun createNfceTransaction(
        job: ImportJob,
        nfce: NfceData,
        qrCodeUrl: String,
        now: Instant,
    ): Transaction {
        val jobId = requireNotNull(job.id) { "ImportJob must have id after save" }
        val transaction = nfceToTransaction(nfce, job.financialAccountId, job.accountId, job.createdByUserId, jobId, now)
        val savedTransaction = transactionRepository.save(transaction)
        val transactionId = requireNotNull(savedTransaction.id) { "Transaction must have id after save" }
        try {
            persistNfceArtifacts(
                transactionId = transactionId,
                accountId = job.accountId,
                nfce = nfce,
                qrCodeUrl = qrCodeUrl,
                now = now,
            )
        } catch (e: Exception) {
            log.error(
                "Failed to persist NFC-e artifacts: financialAccountId={}, accountId={}, transactionId={}, accessKey={}",
                job.financialAccountId,
                job.accountId,
                transactionId,
                nfce.chNFe,
                e,
            )
            throw IllegalStateException("Failed to persist NFC-e artifacts for accessKey=${nfce.chNFe}", e)
        }
        return savedTransaction
    }

    private fun linkNfceToExistingTransaction(
        job: ImportJob,
        existing: Transaction,
        nfce: NfceData,
        qrCodeUrl: String,
        now: Instant,
    ): Transaction {
        val existingId = requireNotNull(existing.id) { "Existing transaction must have id" }
        check(nfceDocumentRepository.findByTransactionId(existingId, job.accountId) == null) {
            "Transaction $existingId already has an NFC-e document linked"
        }
        val updated =
            existing.copy(
                amount = nfce.vNF,
                date = LocalDate.ofInstant(nfce.dhEmi, ZoneId.systemDefault()),
                transactionDatetime = nfce.dhEmi,
                counterpartyName = nfce.nomeEmitente,
                counterpartyDocument = nfce.cnpjEmitente.filter(Char::isDigit),
                providerTransactionId = nfce.chNFe,
                importJobId = requireNotNull(job.id),
                sourceData =
                    (existing.sourceData ?: emptyMap()) + mapOf(
                        "nfce" to mapOf(
                            "accessKey" to nfce.chNFe,
                            "qrCodeUrl" to qrCodeUrl,
                        ),
                    ),
                updatedAt = now,
            )
        val saved = transactionRepository.update(updated)
        persistNfceArtifacts(
            transactionId = existingId,
            accountId = job.accountId,
            nfce = nfce,
            qrCodeUrl = qrCodeUrl,
            now = now,
        )
        return saved
    }

    private fun persistNfceArtifacts(
        transactionId: UUID,
        accountId: UUID,
        nfce: NfceData,
        qrCodeUrl: String,
        now: Instant,
    ) {
        nfceDocumentRepository.save(
            NfceDocument(
                transactionId = transactionId,
                accountId = accountId,
                accessKey = nfce.chNFe,
                issuerCnpj = nfce.cnpjEmitente.filter(Char::isDigit),
                issuerName = nfce.nomeEmitente,
                issuerTradeName = nfce.nomeFantasiaEmitente,
                issuerStateRegistration = nfce.inscricaoEstadualEmitente,
                issuerAddress = nfce.enderecoEmitente,
                issuerPostalCode = nfce.cepEmitente,
                emissionDate = nfce.dhEmi,
                totalValue = nfce.vNF,
                totalTax = nfce.vTotTrib,
                buyerCpf = nfce.cpfDestinatario,
                municipalityCode = nfce.cMunFG,
                municipality = nfce.municipio.takeIf { it.isNotBlank() },
                state = nfce.uf,
                countryCode = "BR",
                sourceData = mapOf("url" to qrCodeUrl),
                createdAt = now,
            ),
        )
        val savedItems =
            purchaseItemRepository.saveAll(
                nfce.items.mapIndexed { index, item ->
                PurchaseItem(
                    transactionId = transactionId,
                    accountId = accountId,
                    productVariantId = resolveProductVariant(item, nfce.cnpjEmitente, accountId),
                    sequence = item.nItem ?: (index + 1),
                    rawDescription = item.xProd,
                    ncmCode = item.ncm.takeIf { it.isNotBlank() },
                    ean = item.cEAN,
                    issuerProductCode = item.cProd,
                    quantity = item.qCom,
                    unit = item.uCom,
                    unitPrice = resolveUnitPrice(item),
                    totalPrice = item.vProd,
                    discount = item.vDesc,
                    effectivePrice = item.vProd - (item.vDesc ?: java.math.BigDecimal.ZERO),
                    taxAmount = item.vTribItem,
                    createdAt = now,
                )
                },
            )

        check(nfceDocumentRepository.findByTransactionId(transactionId, accountId) != null) {
            "NFC-e document was not persisted for transactionId=$transactionId accessKey=${nfce.chNFe}"
        }
        check(savedItems.size == nfce.items.size) {
            "NFC-e purchase items were not fully persisted for transactionId=$transactionId accessKey=${nfce.chNFe}: expected=${nfce.items.size}, actual=${savedItems.size}"
        }
    }

    private fun loadNfceReviewMetadata(job: ImportJob): NfceReviewMetadata {
        val raw = job.extractionMetadata ?: throw IllegalStateException("NFC-e review job has no extraction metadata")
        return objectMapper.readValue(raw, NfceReviewMetadata::class.java)
    }

    private fun loadNfceReviewData(job: ImportJob): NfceData =
        nfceClient.fetch(loadNfceReviewMetadata(job).qrCodeUrl)

    private fun resolveProductVariant(
        item: NfceItem,
        issuerCnpj: String,
        accountId: UUID,
    ): UUID? {
        item.cEAN?.let { ean ->
            productVariantEntityRepository.findByEan(ean)?.id?.let { return it }
        }

        productAliasEntityRepository.findByAccountIdAndRawDescription(accountId, item.xProd)?.productVariantId?.let { return it }

        item.cProd?.let { issuerProductCode ->
            purchaseItemEntityRepository.findFirstResolvedByCnpjAndProduct(
                accountId = accountId,
                issuerCnpj = issuerCnpj.filter(Char::isDigit),
                issuerProductCode = issuerProductCode,
            )?.productVariantId?.let { return it }
        }

        return null
    }

    private fun resolveUnitPrice(item: NfceItem) =
        item.vUnCom
            ?: if (item.qCom.compareTo(java.math.BigDecimal.ZERO) == 0) {
                item.vProd
            } else {
                item.vProd.divide(item.qCom, 4, RoundingMode.HALF_UP)
            }
}
