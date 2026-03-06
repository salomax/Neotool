package io.github.salomax.neotool.banking.domain.import.dedup

import io.github.salomax.neotool.banking.repo.TransactionEntityRepository
import jakarta.inject.Singleton
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID

/**
 * Cross-source import deduplication via fingerprint (Level 2).
 * Fingerprint = SHA-256(account|date|amountCents|normalizedDescription).
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
    ): String {
        val normalized = normalizedDescription.trim().lowercase()
        val payload = "$financialAccountId|$date|$amountCents|$normalized"
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
}
