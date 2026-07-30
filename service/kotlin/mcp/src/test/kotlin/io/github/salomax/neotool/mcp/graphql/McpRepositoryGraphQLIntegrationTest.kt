package io.github.salomax.neotool.mcp.graphql

import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.mcp.audit.ToolCallAuditLogEntity
import io.github.salomax.neotool.mcp.audit.ToolCallAuditLogRepository
import io.github.salomax.neotool.mcp.audit.ToolCallAuditOutcome
import io.github.salomax.neotool.mcp.security.ConnectionRevocationRepository
import io.github.salomax.neotool.mcp.test.McpJwtTestFixture
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID

@MicronautTest(startApplication = true, transactional = false)
@DisplayName("MCP GraphQL repository integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("mcp")
open class McpRepositoryGraphQLIntegrationTest :
    BaseIntegrationTest(),
    PostgresIntegrationTest,
    TestPropertyProvider {
    @Inject
    lateinit var connectionRevocationRepository: ConnectionRevocationRepository

    @Inject
    lateinit var toolCallAuditLogRepository: ToolCallAuditLogRepository

    override fun getProperties(): MutableMap<String, String> {
        val props = super.getProperties()
        props["jwt.issuer"] = McpJwtTestFixture.ISSUER
        props["jwt.public-key"] = McpJwtTestFixture.publicKeyPem
        props["mcp.resource.audience"] = McpJwtTestFixture.AUDIENCE
        props["mcp.resource.metadata-url"] = "https://mcp.example.test/.well-known/oauth-protected-resource"
        return props
    }

    @BeforeEach
    override fun setUp() {
        super.setUp()
        connectionRevocationRepository.deleteAll()
        truncateAuditLog()
    }

    @AfterEach
    fun cleanUp() {
        connectionRevocationRepository.deleteAll()
        truncateAuditLog()
    }

    @Test
    fun `upsertRevocation keeps one durable revoked row`() {
        val userId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val revokedAt = Instant.parse("2026-07-30T13:00:00Z")
        val refreshedAt = revokedAt.plusSeconds(60)

        connectionRevocationRepository.upsertRevocation(
            userId = userId,
            clientId = "agent-a",
            accountId = accountId,
            revokedAt = revokedAt,
            revokedUntil = null,
            reason = "first",
        )
        connectionRevocationRepository.upsertRevocation(
            userId = userId,
            clientId = "agent-a",
            accountId = accountId,
            revokedAt = refreshedAt,
            revokedUntil = null,
            reason = "second",
        )

        val row = connectionRevocationRepository.findAll().single()
        assertThat(row.id.userId).isEqualTo(userId)
        assertThat(row.id.clientId).isEqualTo("agent-a")
        assertThat(row.id.accountId).isEqualTo(accountId)
        assertThat(row.revokedAt).isEqualTo(refreshedAt)
        assertThat(row.reason).isEqualTo("second")
    }

    @Test
    fun `findRecentActivity filters by userId and clientId and orders newest first`() {
        val targetUserId = UUID.randomUUID()
        val otherUserId = UUID.randomUUID()
        val newest = Instant.parse("2026-07-30T15:00:00Z")
        val older = newest.minusSeconds(120)

        toolCallAuditLogRepository.saveAll(
            listOf(
                auditRow(
                    id = UUID.randomUUID(),
                    occurredAt = older,
                    userId = targetUserId,
                    clientId = "agent-a",
                ),
                auditRow(
                    id = UUID.randomUUID(),
                    occurredAt = newest,
                    userId = targetUserId,
                    clientId = "agent-a",
                ),
                auditRow(
                    id = UUID.randomUUID(),
                    occurredAt = newest.plusSeconds(1),
                    userId = targetUserId,
                    clientId = "agent-b",
                ),
                auditRow(
                    id = UUID.randomUUID(),
                    occurredAt = newest.plusSeconds(2),
                    userId = otherUserId,
                    clientId = "agent-a",
                ),
            ),
        )

        val result = toolCallAuditLogRepository.findRecentActivity(targetUserId, "agent-a", 10)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.clientId }).containsOnly("agent-a")
        assertThat(result.map { it.userId }).containsOnly(targetUserId)
        assertThat(result.map { it.occurredAt }).containsExactly(newest, older)
    }

    private fun auditRow(
        id: UUID,
        occurredAt: Instant,
        userId: UUID,
        clientId: String,
    ) = ToolCallAuditLogEntity(
        id = id,
        occurredAt = occurredAt,
        userId = userId,
        clientId = clientId,
        tokenId = "token-$clientId",
        toolName = "list_transactions",
        arguments = mapOf("first" to 5),
        outcome = ToolCallAuditOutcome.SUCCESS,
        latencyMs = 10,
        createdAt = occurredAt,
    )

    private fun truncateAuditLog() {
        val container = getPgContainer()
        DriverManager.getConnection(container.jdbcUrl, container.username, container.password).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("TRUNCATE TABLE mcp.tool_call_audit_log")
            }
        }
    }
}
