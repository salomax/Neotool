package io.github.salomax.neotool.banking.domain.transaction

import java.util.UUID

data class CreateTransactionOutcome(
    val transaction: Transaction,
    /** Rules that were applied while creating the transaction. */
    val appliedRuleIds: List<UUID> = emptyList(),
)
