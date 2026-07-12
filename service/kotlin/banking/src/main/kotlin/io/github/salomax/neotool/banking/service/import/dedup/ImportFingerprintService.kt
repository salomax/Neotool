package io.github.salomax.neotool.banking.service.import.dedup

import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.repo.transaction.TransactionEntityRepository
import jakarta.inject.Singleton
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID

/**
 * Cross-source import deduplication via fingerprint (Level 2).
 * Fingerprint = SHA-256(account|date|amountCents|normalizedDescription|creditDebitType).
 * Same inputs always produce the same 64-char hex digest.
 */
@Singleton
open class ImportFingerprintService(
    private val transactionEntityRepository: TransactionEntityRepository,
) {
    /**
     * Computes a deterministic 64-char hex fingerprint from account, date, amount in cents, and description.
     * Description is normalized: trim + lowercase before hashing.
     */
    fun compute(
        financialAccountId: UUID,
        date: LocalDate,
        amountCents: Long,
        normalizedDescription: String,
        creditDebitType: CreditDebitType,
    ): String {
        val normalized = normalizedDescription.trim().lowercase()
        val payload = "$financialAccountId|$date|$amountCents|$normalized|${creditDebitType.name}"
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Returns true if a non-deleted transaction exists with the given
     * financialAccountId and importFingerprint.
     */
    fun isDuplicate(
        financialAccountId: UUID,
        fingerprint: String,
    ): Boolean =
        transactionEntityRepository.existsByFinancialAccountIdAndImportFingerprintAndDeletedAtIsNull(
            financialAccountId = financialAccountId,
            importFingerprint = fingerprint,
        )

    /**
     * Bill-import charge dedup keyed by business reference month instead of bill id.
     * Fingerprint = SHA-256(account|referenceMonth|amountCents|normalizedDescription|creditDebitType).
     */
    fun computeForBill(
        financialAccountId: UUID,
        referenceMonth: LocalDate,
        amountCents: Long,
        normalizedDescription: String,
        creditDebitType: CreditDebitType,
    ): String {
        val normalized = normalizedDescription.trim().lowercase()
        val payload = "$financialAccountId|$referenceMonth|$amountCents|$normalized|${creditDebitType.name}"
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun isDuplicateForBill(
        financialAccountId: UUID,
        fingerprint: String,
    ): Boolean = isDuplicate(financialAccountId, fingerprint)
}
