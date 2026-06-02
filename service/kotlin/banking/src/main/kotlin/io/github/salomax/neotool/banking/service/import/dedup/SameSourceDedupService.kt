package io.github.salomax.neotool.banking.service.import.dedup

import io.github.salomax.neotool.banking.domain.transaction.TransactionSource
import io.github.salomax.neotool.banking.repo.transaction.TransactionEntityRepository
import jakarta.inject.Singleton
import java.util.UUID

/**
 * Same-source deduplication: checks if a transaction with the same
 * financial account, provider transaction ID, and source already exists.
 * Scoped to financial_account_id; deleted transactions are not considered.
 */
@Singleton
open class SameSourceDedupService(
    private val transactionEntityRepository: TransactionEntityRepository,
) {
    /**
     * Returns true if a non-deleted transaction exists with the given
     * financialAccountId, providerTransactionId, and source.
     */
    fun isDuplicate(
        financialAccountId: UUID,
        providerTransactionId: String,
        source: TransactionSource,
    ): Boolean =
        transactionEntityRepository.existsByFinancialAccountIdAndProviderTransactionIdAndSourceAndDeletedAtIsNull(
            financialAccountId = financialAccountId,
            providerTransactionId = providerTransactionId,
            source = source,
        )
}
