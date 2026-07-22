package io.github.salomax.neotool.mcp.security

import jakarta.inject.Singleton
import java.time.Instant
import java.util.UUID

interface ConnectionRevocationStore {
    fun isRevoked(
        userId: UUID,
        clientId: String,
        accountId: UUID,
    ): Boolean

    fun deleteExpired(now: Instant): Long
}

@Singleton
class DefaultConnectionRevocationStore(
    private val repository: ConnectionRevocationRepository,
) : ConnectionRevocationStore {
    override fun isRevoked(
        userId: UUID,
        clientId: String,
        accountId: UUID,
    ): Boolean = repository.existsRevocation(userId, clientId, accountId)

    override fun deleteExpired(now: Instant): Long = repository.deleteExpired(now)
}
