package io.github.salomax.neotool.mcp.graphql

import io.github.salomax.neotool.common.security.exception.AuthorizationDeniedException
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.github.salomax.neotool.mcp.audit.ToolCallAuditLogEntity
import io.github.salomax.neotool.mcp.audit.ToolCallAuditLogRepository
import io.github.salomax.neotool.mcp.audit.ToolCallAuditOutcome
import io.github.salomax.neotool.mcp.security.ConnectionRevocationRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class McpConnectionResolverTest {
    private val connectionRevocationRepository = mock<ConnectionRevocationRepository>()
    private val toolCallAuditLogRepository = mock<ToolCallAuditLogRepository>()
    private val resolver = McpConnectionResolver(connectionRevocationRepository, toolCallAuditLogRepository)

    @Test
    fun `revokeConnection rejects invalid userId`() {
        assertThatThrownBy {
            resolver.revokeConnection(
                principal = delegatedServicePrincipal(),
                userId = "not-a-uuid",
                clientId = "agent-a",
                accountId = UUID.randomUUID().toString(),
            )
        }.hasMessageContaining("Invalid input provided")

        verify(connectionRevocationRepository, never()).upsertRevocation(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `revokeConnection rejects blank clientId`() {
        assertThatThrownBy {
            resolver.revokeConnection(
                principal = delegatedServicePrincipal(),
                userId = UUID.randomUUID().toString(),
                clientId = "   ",
                accountId = UUID.randomUUID().toString(),
            )
        }.hasMessageContaining("Invalid input provided")

        verify(connectionRevocationRepository, never()).upsertRevocation(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `revokeConnection rejects user principals`() {
        val userId = UUID.randomUUID()
        val accountId = UUID.randomUUID()

        assertThatThrownBy {
            resolver.revokeConnection(
                principal =
                    RequestPrincipal(
                        principalType = PrincipalType.USER,
                        userId = userId,
                        serviceId = null,
                        token = "token",
                        tokenId = "token-id",
                        permissionsFromToken = emptyList(),
                        currentAccountId = accountId,
                    ),
                userId = userId.toString(),
                clientId = "agent-a",
                accountId = accountId.toString(),
            )
        }.isInstanceOf(AuthorizationDeniedException::class.java)

        verify(connectionRevocationRepository, never()).upsertRevocation(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `revokeConnection rejects delegated account mismatches`() {
        val userId = UUID.randomUUID()

        assertThatThrownBy {
            resolver.revokeConnection(
                principal = delegatedServicePrincipal(userId = userId, accountId = UUID.randomUUID()),
                userId = userId.toString(),
                clientId = "agent-a",
                accountId = UUID.randomUUID().toString(),
            )
        }.isInstanceOf(AuthorizationDeniedException::class.java)

        verify(connectionRevocationRepository, never()).upsertRevocation(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `recentActivity rejects non-positive bounds`() {
        assertThatThrownBy {
            resolver.recentActivity(
                principal = delegatedServicePrincipal(),
                userId = UUID.randomUUID().toString(),
                clientId = "agent-a",
                first = 0,
            )
        }.hasMessageContaining("Invalid input provided")

        verify(toolCallAuditLogRepository, never()).findRecentActivity(any(), any(), any())
    }

    @Test
    fun `recentActivity coerces oversized bounds and maps rows`() {
        val userId = UUID.randomUUID()
        val now = Instant.parse("2026-07-30T12:00:00Z")
        whenever(toolCallAuditLogRepository.findRecentActivity(eq(userId), eq("agent-a"), eq(100))).thenReturn(
            listOf(
                ToolCallAuditLogEntity(
                    id = UUID.randomUUID(),
                    occurredAt = now,
                    userId = userId,
                    clientId = "agent-a",
                    tokenId = "token-1",
                    toolName = "list_transactions",
                    arguments = emptyMap(),
                    outcome = ToolCallAuditOutcome.SUCCESS,
                    latencyMs = 15,
                    createdAt = now,
                ),
            ),
        )

        val result =
            resolver.recentActivity(
                principal = delegatedServicePrincipal(userId = userId),
                userId = userId.toString(),
                clientId = "agent-a",
                first = 500,
            )

        val entry = result.single()
        assertThat(entry.userId).isEqualTo(userId)
        assertThat(entry.clientId).isEqualTo("agent-a")
        assertThat(entry.outcome).isEqualTo(ToolCallAuditOutcome.SUCCESS)
    }

    private fun delegatedServicePrincipal(
        userId: UUID = UUID.randomUUID(),
        accountId: UUID = UUID.randomUUID(),
    ) = RequestPrincipal(
        principalType = PrincipalType.SERVICE,
        userId = userId,
        serviceId = UUID.randomUUID(),
        token = "service-token",
        tokenId = "token-id",
        permissionsFromToken = emptyList(),
        userPermissions = emptyList(),
        currentAccountId = accountId,
    )
}
