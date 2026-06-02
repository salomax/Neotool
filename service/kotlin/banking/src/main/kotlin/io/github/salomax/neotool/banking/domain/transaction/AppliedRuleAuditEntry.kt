package io.github.salomax.neotool.banking.domain.transaction

import java.time.Instant
import java.util.UUID

/**
 * Audit trail entry for one rule that was effectively applied to a transaction.
 */
data class AppliedRuleAuditEntry(
    val ruleId: UUID,
    val appliedAt: Instant,
)
