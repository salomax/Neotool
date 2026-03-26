package io.github.salomax.neotool.banking.service.import

import io.github.salomax.neotool.banking.domain.import.*
import io.github.salomax.neotool.banking.shared.DateWindowPolicy
import io.github.salomax.neotool.banking.service.import.dedup.CrossSourceDedupService
import io.github.salomax.neotool.banking.service.import.dedup.ImportFingerprintService
import io.github.salomax.neotool.banking.service.import.dedup.SameSourceDedupService
import io.github.salomax.neotool.banking.service.transaction.TransactionRuleEngine
import io.github.salomax.neotool.banking.domain.transaction.CategorySource
import io.github.salomax.neotool.banking.domain.preferences.RuleApplicationMode
import io.github.salomax.neotool.banking.service.preferences.AccountPreferencesService
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.github.salomax.neotool.banking.domain.transaction.TransactionSource
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate

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
private fun ImportSource.toTransactionSource(): TransactionSource =
    when (this) {
        ImportSource.OPEN_FINANCE -> TransactionSource.OPEN_FINANCE
        ImportSource.PLUGGY -> TransactionSource.OPEN_FINANCE
        ImportSource.OFX -> TransactionSource.OFX
        ImportSource.CSV_AUTO,
        ImportSource.CSV_NUBANK,
        ImportSource.CSV_ITAU,
        ImportSource.CSV_BRADESCO,
        ImportSource.CSV_SANTANDER -> TransactionSource.CSV
        ImportSource.NFC_E -> TransactionSource.NFC_E
        ImportSource.RECEIPT_OCR -> TransactionSource.RECEIPT_OCR
        else -> TransactionSource.MANUAL // AUTO: format not yet determined
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
    private val categorizationService: TransactionRuleEngine,
    private val accountPreferencesService: AccountPreferencesService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
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
        val minAllowedDate = DateWindowPolicy.minAllowedDate()

        // Cache initial balance date once per job (null when allowCrossSourceDuplicates = resolveImportDuplicate path)
        val initialBalanceDate: LocalDate? =
            if (allowCrossSourceDuplicates) {
                null
            } else {
                transactionRepository.findInitialBalanceDate(financialAccountId)
            }

        val imported = mutableListOf<Transaction>()
        var skipped = 0
        val duplicateCandidates = mutableListOf<ImportDuplicateCandidate>()
        val errors = mutableListOf<ImportRowError>()
        val sameSourceDuplicateCache = mutableMapOf<String, Boolean>()
        val fingerprintDuplicateCache = mutableMapOf<String, Boolean>()

        val mode = accountPreferencesService.get(accountId).ruleApplicationMode

        val categorizationRules =
            if (mode == RuleApplicationMode.NEVER) {
                emptyList()
            } else {
                try {
                    categorizationService.preloadRules(accountId)
                } catch (e: Exception) {
                    logger.warn(
                        "Failed to preload category rules; import will continue without auto-categorization (accountId={})",
                        accountId,
                        e,
                    )
                    emptyList()
                }
            }

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
                val amountCents =
                    parsed.amount.multiply(
                        BigDecimal(100),
                    ).setScale(0, RoundingMode.HALF_UP).longValueExact()
                val fingerprint =
                    importFingerprintService.compute(
                        financialAccountId = financialAccountId,
                        date = parsed.date,
                        amountCents = amountCents,
                        normalizedDescription = parsed.description,
                        creditDebitType = parsed.creditDebitType,
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
                            reason =
                                "Transaction date ${parsed.date} is before the account's initial balance date " +
                                    "$initialBalanceDate (TRANSACTION_BEFORE_INITIAL_BALANCE)",
                        ),
                    )
                    return@forEachIndexed
                }

                // 4. Moving date window guard (maximum 5 years in the past)
                if (parsed.date.isBefore(minAllowedDate)) {
                    errors.add(
                        ImportRowError(
                            rowIndex = index,
                            reason =
                                "Transaction date ${parsed.date} is outside the allowed window; " +
                                    "minimum allowed date is $minAllowedDate (DATE_OUTSIDE_ALLOWED_WINDOW)",
                        ),
                    )
                    return@forEachIndexed
                }

                // 5. Level 2: cross-source candidates → do NOT insert
                if (!allowCrossSourceDuplicates) {
                    val candidates =
                        crossSourceDedupService.findCandidates(
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
                val type =
                    when (parsed.creditDebitType) {
                        CreditDebitType.DEBIT -> TransactionType.OTHER_EXPENSE
                        CreditDebitType.CREDIT -> TransactionType.OTHER_INCOME
                    }
                val ruleLookupDescription = parsed.descriptionRaw?.takeIf { it.isNotBlank() } ?: parsed.description
                val categorySuggestion =
                    if (mode == RuleApplicationMode.NEVER) {
                        null
                    } else {
                        try {
                            categorizationService.suggestFromRulesRecordingMatch(
                                categorizationRules,
                                ruleLookupDescription,
                                parsed.creditDebitType,
                            )
                        } catch (e: Exception) {
                            logger.warn(
                                "Auto-categorization failed for import row; continuing without category (accountId={}, rowIndex={})",
                                accountId,
                                index,
                                e,
                            )
                            null
                        }
                    }
                val transaction =
                    Transaction(
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
                        description = categorySuggestion?.descriptionOverride ?: parsed.description,
                        descriptionRaw = parsed.descriptionRaw,
                        tags = categorySuggestion?.tagsOverride ?: emptyList(),
                        counterpartyName = parsed.counterpartyName,
                        counterpartyDocument = parsed.counterpartyDocument,
                        providerTransactionId = parsed.providerTransactionId,
                        totalInstallments = parsed.totalInstallments,
                        importFingerprint = fingerprint,
                        importJobId = importJobId,
                        sourceData = buildSourceData(parsed),
                        categoryId = categorySuggestion?.categoryId,
                        categorySource = categorySuggestion?.let { CategorySource.RULE },
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

    private fun buildSourceData(parsed: ParsedTransaction): Map<String, Any?> =
        buildMap {
            put("description", parsed.description)
            parsed.descriptionRaw?.let { put("descriptionRaw", it) }
            put("amount", parsed.amount)
            put("currencyCode", parsed.currencyCode)
            parsed.counterpartyName?.let { put("counterpartyName", it) }
            parsed.counterpartyDocument?.let { put("counterpartyDocument", it) }
            parsed.providerTransactionId?.let { put("providerTransactionId", it) }
            parsed.totalInstallments?.let { put("totalInstallments", it) }
        }
}
