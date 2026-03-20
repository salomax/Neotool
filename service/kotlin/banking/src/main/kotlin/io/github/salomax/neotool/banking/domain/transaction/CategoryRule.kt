package io.github.salomax.neotool.banking.domain.transaction

import java.time.Instant
import java.util.UUID

/**
 * Domain model for banking.transaction_rules.
 * Maps a text pattern to a category and/or merchant for auto-assignment on transactions.
 * At least one of [categoryId] or [merchantId] must be non-null.
 */
data class TransactionRule(
    val id: UUID? = null,
    val accountId: UUID? = null,
    val categoryId: UUID? = null,
    val merchantId: UUID? = null,
    val pattern: String,
    val patternType: PatternType,
    val applicableTo: CategoryApplicableTo,
    val priority: Int = 100,
    val isSystem: Boolean = false,
    val isImplicit: Boolean = false,
    val matchCount: Int = 0,
    val deletedAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    init {
        require(categoryId != null || merchantId != null) {
            "TransactionRule must target at least one of categoryId or merchantId"
        }
    }
}
