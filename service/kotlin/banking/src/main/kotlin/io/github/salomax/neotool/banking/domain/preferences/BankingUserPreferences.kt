package io.github.salomax.neotool.banking.domain.preferences

import java.time.Instant
import java.util.UUID

data class BankingUserPreferences(
    val userId: UUID,
    val ruleApplicationMode: RuleApplicationMode,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun defaultsFor(userId: UUID, now: Instant = Instant.now()): BankingUserPreferences =
            BankingUserPreferences(
                userId = userId,
                ruleApplicationMode = RuleApplicationMode.ALWAYS_PROMPT,
                createdAt = now,
                updatedAt = now,
            )
    }
}
