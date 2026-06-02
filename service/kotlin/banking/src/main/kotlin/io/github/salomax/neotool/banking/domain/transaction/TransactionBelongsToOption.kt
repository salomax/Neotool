package io.github.salomax.neotool.banking.domain.transaction

import java.util.UUID

/**
 * Distinct "belongs to" option available for transactions in an account.
 * Exactly one field is expected to be non-null.
 */
data class TransactionBelongsToOption(
    val userId: UUID? = null,
    val name: String? = null,
)
