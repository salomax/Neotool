package io.github.salomax.neotool.banking.domain.transaction

import java.util.UUID

data class CreateTransactionOutcome(
    val transaction: Transaction,
    /** Only populated when user ruleApplicationMode is ALWAYS_PROMPT. */
    val appliedRuleIds: List<UUID> = emptyList(),
)
