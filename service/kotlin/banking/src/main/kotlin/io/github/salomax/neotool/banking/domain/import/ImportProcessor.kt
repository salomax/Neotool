package io.github.salomax.neotool.banking.domain.import

import io.github.salomax.neotool.banking.domain.import.dedup.CrossSourceDedupService
import io.github.salomax.neotool.banking.domain.import.dedup.ImportFingerprintService
import io.github.salomax.neotool.banking.domain.import.dedup.SameSourceDedupService
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionRepository
import io.github.salomax.neotool.banking.domain.transaction.TransactionSource
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Result of processing a batch of [ParsedTransaction] rows for an [ImportJob].
 */
data class ImportProcessingResult(
    val imported: List<Transaction>,
    val skipped: Int,
    val duplicateCandidates: List<ImportDuplicateCandidate>,
    val errors: List<ImportRowError>,
)

/**
 * A row that may be a duplicate of existing transaction(s) from another source (Level 2).
 * Not inserted; caller may present for review.
 */
data class ImportDuplicateCandidate(
    val rowIndex: Int,
    val parsed: ParsedTransaction,
    val candidates: List<Transaction>,
)

/**
 * Parse/validation failure for a single row.
 */
data class ImportRowError(
    val rowIndex: Int,
    val reason: String,
)

/**
 * Maps [ImportSource] to [TransactionSource] for stored transactions.
 */
private fun ImportSource.toTransactionSource(): TransactionSource = when (this) {
    ImportSource.OPEN_FINANCE -> TransactionSource.OPEN_FINANCE
    ImportSource.PLUGGY -> TransactionSource.PLUGGY
    else -> TransactionSource.MANUAL
}

/**
 * Processes a list of [ParsedTransaction] items for a given [ImportJob].
 * Does NOT update job status or emit events; that is the responsibility of [ImportJobService].
 */
@Singleton
open class ImportProcessor(
    private val sameSourceDedupService: SameSourceDedupService,
    private val importFingerprintService: ImportFingerprintService,
    private val crossSourceDedupService: CrossSourceDedupService,
    private val transactionRepository: TransactionRepository,
) {

    /**
     * Processes each row: Level 1 (provider id) skip, Level 1b (fingerprint) skip,
     * Level 2 (cross-source) → duplicateCandidates without insert, else save.
     * Per-row exceptions are collected in [ImportProcessingResult.errors] and do not abort processing.
     */
    fun process(
        job: ImportJob,
        rows: List<ParsedTransaction>,
        allowCrossSourceDuplicates: Boolean = false,
    ): ImportProcessingResult {
        requireNotNull(job.id) { "ImportJob must be persisted (id not null)" }
        val financialAccountId = job.financialAccountId
        val accountId = job.accountId
        val createdByUserId = job.createdByUserId
        val importSource = job.source
        val transactionSource = importSource.toTransactionSource()
        val importJobId = job.id
        val now = Instant.now()

        // Cache initial balance date once per job (null when allowCrossSourceDuplicates = resolveImportDuplicate path)
        val initialBalanceDate: LocalDate? =
            if (allowCrossSourceDuplicates) null
            else transactionRepository.findInitialBalanceDate(financialAccountId)

        val imported = mutableListOf<Transaction>()
        var skipped = 0
        val duplicateCandidates = mutableListOf<ImportDuplicateCandidate>()
        val errors = mutableListOf<ImportRowError>()
        val sameSourceDuplicateCache = mutableMapOf<String, Boolean>()
        val fingerprintDuplicateCache = mutableMapOf<String, Boolean>()

        rows.forEachIndexed { index, parsed ->
            try {
                // 1. Level 1: same-source duplicate by providerTransactionId
                parsed.providerTransactionId?.let { providerId ->
                    val isDuplicate =
                        sameSourceDuplicateCache.getOrPut(providerId) {
                            sameSourceDedupService.isDuplicate(financialAccountId, providerId, transactionSource)
                        }
                    if (isDuplicate) {
                        skipped++
                        return@forEachIndexed
                    }
                }

                // 2. Fingerprint and Level 1b: fingerprint duplicate
                val amountCents = parsed.amount.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()
                val fingerprint = importFingerprintService.compute(
                    financialAccountId = financialAccountId,
                    date = parsed.date,
                    amountCents = amountCents,
                    normalizedDescription = parsed.description,
                )
                val fingerprintDuplicate =
                    fingerprintDuplicateCache.getOrPut(fingerprint) {
                        importFingerprintService.isDuplicate(financialAccountId, fingerprint)
                    }
                if (fingerprintDuplicate) {
                    skipped++
                    return@forEachIndexed
                }

                // 3. Initial balance date guard
                if (initialBalanceDate != null && parsed.date.isBefore(initialBalanceDate)) {
                    errors.add(
                        ImportRowError(
                            rowIndex = index,
                            reason = "Transaction date ${parsed.date} is before the account's initial balance date $initialBalanceDate (TRANSACTION_BEFORE_INITIAL_BALANCE)",
                        ),
                    )
                    return@forEachIndexed
                }

                // 5. Level 2: cross-source candidates → do NOT insert
                if (!allowCrossSourceDuplicates) {
                    val candidates = crossSourceDedupService.findCandidates(
                        financialAccountId = financialAccountId,
                        parsed = parsed,
                        incomingSource = transactionSource,
                    )
                    if (candidates.isNotEmpty()) {
                        duplicateCandidates.add(
                            ImportDuplicateCandidate(rowIndex = index, parsed = parsed, candidates = candidates),
                        )
                        return@forEachIndexed
                    }
                }

                // 6. No conflict: create and save transaction
                val type = when (parsed.creditDebitType) {
                    CreditDebitType.DEBIT -> TransactionType.OTHER_EXPENSE
                    CreditDebitType.CREDIT -> TransactionType.OTHER_INCOME
                }
                val transaction = Transaction(
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = createdByUserId,
                    creditDebitType = parsed.creditDebitType,
                    type = type,
                    status = TransactionStatus.POSTED,
                    source = transactionSource,
                    amount = parsed.amount,
                    currencyCode = parsed.currencyCode,
                    date = parsed.date,
                    transactionDatetime = parsed.transactionDatetime,
                    description = parsed.description,
                    descriptionRaw = parsed.descriptionRaw,
                    counterpartyName = parsed.counterpartyName,
                    counterpartyDocument = parsed.counterpartyDocument,
                    providerTransactionId = parsed.providerTransactionId,
                    totalInstallments = parsed.totalInstallments,
                    importFingerprint = fingerprint,
                    importJobId = importJobId,
                    createdAt = now,
                    updatedAt = now,
                )
                val saved = transactionRepository.save(transaction)
                imported.add(saved)
            } catch (e: Exception) {
                errors.add(ImportRowError(rowIndex = index, reason = e.message ?: e.toString()))
            }
        }

        return ImportProcessingResult(
            imported = imported,
            skipped = skipped,
            duplicateCandidates = duplicateCandidates,
            errors = errors,
        )
    }
}
