package io.github.salomax.neotool.banking.repo.transaction

import java.time.Instant
import java.util.UUID

data class McpWriteIdempotencyRecord(
    val id: UUID?,
    val accountId: UUID,
    val idempotencyKey: String,
    val operation: String,
    val resultJson: String,
    val createdAt: Instant,
)

class McpWriteIdempotencyAlreadyClaimedException(
    val accountId: UUID,
    val idempotencyKey: String,
    val operation: String,
    cause: Throwable? = null,
) : RuntimeException(
        "Idempotency key '$idempotencyKey' was already claimed for account $accountId",
        cause,
    )

interface McpWriteIdempotencyRepository {
    @Throws(McpWriteIdempotencyAlreadyClaimedException::class)
    fun claim(
        accountId: UUID,
        idempotencyKey: String,
        operation: String,
    ): McpWriteIdempotencyRecord

    fun findByAccountIdAndIdempotencyKey(
        accountId: UUID,
        idempotencyKey: String,
    ): McpWriteIdempotencyRecord?

    fun storeResult(
        id: UUID,
        resultJson: String,
    ): McpWriteIdempotencyRecord

    fun deleteCreatedBefore(cutoff: Instant): Int
}
