package io.github.salomax.neotool.mcp.graphql

import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.common.security.exception.AuthorizationDeniedException
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.github.salomax.neotool.mcp.audit.ToolCallAuditLogRepository
import io.github.salomax.neotool.mcp.security.ConnectionRevocationRepository
import jakarta.inject.Singleton
import java.time.Instant
import java.util.UUID

@Singleton
class McpConnectionResolver(
    private val connectionRevocationRepository: ConnectionRevocationRepository,
    private val toolCallAuditLogRepository: ToolCallAuditLogRepository,
) {
    fun revokeConnection(
        principal: RequestPrincipal,
        userId: String,
        clientId: String,
        accountId: String,
    ): McpConnectionRevocationDTO {
        val parsedUserId = parseUuid(userId, "userId")
        val normalizedClientId = normalizeClientId(clientId)
        val parsedAccountId = parseUuid(accountId, "accountId")
        requireInternalCaller(
            principal = principal,
            userId = parsedUserId,
            accountId = parsedAccountId,
        )

        val revokedAt = Instant.now()
        connectionRevocationRepository.upsertRevocation(
            userId = parsedUserId,
            clientId = normalizedClientId,
            accountId = parsedAccountId,
            revokedAt = revokedAt,
            revokedUntil = null,
            reason = null,
        )

        return McpConnectionRevocationDTO(
            userId = parsedUserId,
            clientId = normalizedClientId,
            accountId = parsedAccountId,
            revokedAt = revokedAt,
            revokedUntil = null,
            reason = null,
        )
    }

    fun recentActivity(
        principal: RequestPrincipal,
        userId: String,
        clientId: String,
        first: Int,
    ): List<McpRecentActivityEntryDTO> {
        val parsedUserId = parseUuid(userId, "userId")
        val normalizedClientId = normalizeClientId(clientId)
        val boundedFirst = boundFirst(first)
        requireInternalCaller(principal = principal, userId = parsedUserId)

        return toolCallAuditLogRepository
            .findRecentActivity(
                userId = parsedUserId,
                clientId = normalizedClientId,
                limit = boundedFirst,
            ).map { entry ->
                McpRecentActivityEntryDTO(
                    id = entry.id,
                    occurredAt = entry.occurredAt,
                    userId = entry.userId ?: parsedUserId,
                    clientId = entry.clientId ?: normalizedClientId,
                    tokenId = entry.tokenId,
                    toolName = entry.toolName,
                    outcome = entry.outcome,
                    errorCode = entry.errorCode,
                    errorMessage = entry.errorMessage,
                    latencyMs = entry.latencyMs,
                    createdAt = entry.createdAt,
                )
            }
    }

    private fun requireInternalCaller(
        principal: RequestPrincipal,
        userId: UUID,
        accountId: UUID? = null,
    ) {
        if (principal.principalType != PrincipalType.SERVICE) {
            throw AuthorizationDeniedException("MCP GraphQL primitives require a service token")
        }
        if (principal.userId == null || principal.userId != userId) {
            throw AuthorizationDeniedException("Delegated service token user context must match the target user")
        }
        if (accountId != null && principal.currentAccountId != accountId) {
            throw AuthorizationDeniedException("Delegated service token account context must match the target account")
        }
    }

    private fun normalizeClientId(clientId: String): String =
        clientId.trim().takeIf { it.isNotBlank() }
            ?: throw ValidationException(
                errorCode = CommonErrorCode.INVALID_INPUT,
                field = "clientId",
                parameters = mapOf("reason" to "blank"),
            )

    private fun parseUuid(
        raw: String,
        field: String,
    ): UUID =
        try {
            UUID.fromString(raw)
        } catch (_: IllegalArgumentException) {
            throw ValidationException(
                errorCode = CommonErrorCode.INVALID_INPUT,
                field = field,
                parameters = mapOf("reason" to "invalid_uuid"),
            )
        }

    private fun boundFirst(first: Int): Int {
        if (first <= 0) {
            throw ValidationException(
                errorCode = CommonErrorCode.INVALID_INPUT,
                field = "first",
                parameters = mapOf("reason" to "must_be_positive"),
            )
        }
        return first.coerceAtMost(100)
    }
}
