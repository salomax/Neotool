package io.github.salomax.neotool.banking.domain.import.dedup

import io.github.salomax.neotool.banking.domain.import.ParsedTransaction
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionSource
import io.github.salomax.neotool.banking.model.transaction.TransactionMapper
import io.github.salomax.neotool.banking.repo.TransactionEntityRepository
import jakarta.inject.Singleton
import java.util.UUID

/**
 * Cross-source deduplication (Level 2): finds existing transactions that match
 * (date, amount, financial_account_id) but have a different source.
 * Same-source duplicates are Level 1 (SameSourceDedupService).
 * Soft-deleted transactions are excluded.
 */
@Singleton
open class CrossSourceDedupService(
    private val transactionEntityRepository: TransactionEntityRepository,
) {
    /**
     * Returns existing transactions that match the parsed row on date and amount
     * for the same financial account but with a different source.
     * Empty list when no cross-source conflict.
     */
    fun findCandidates(
        financialAccountId: UUID,
        parsed: ParsedTransaction,
        incomingSource: TransactionSource,
    ): List<Transaction> =
        transactionEntityRepository.findCrossSourceCandidates(
            financialAccountId = financialAccountId,
            date = parsed.date,
            amount = parsed.amount,
            incomingSource = incomingSource.name,
        ).map(TransactionMapper::toDomain)
}
