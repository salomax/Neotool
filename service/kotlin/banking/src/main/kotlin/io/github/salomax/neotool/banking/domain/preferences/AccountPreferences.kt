package io.github.salomax.neotool.banking.domain.preferences

import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import java.time.Instant
import java.util.UUID

data class AccountPreferences(
    val accountId: UUID,
    val defaultTransactionStatus: TransactionStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun defaultsFor(
            accountId: UUID,
            now: Instant = Instant.now(),
        ): AccountPreferences =
            AccountPreferences(
                accountId = accountId,
                defaultTransactionStatus = TransactionStatus.POSTED,
                createdAt = now,
                updatedAt = now,
            )
    }
}
