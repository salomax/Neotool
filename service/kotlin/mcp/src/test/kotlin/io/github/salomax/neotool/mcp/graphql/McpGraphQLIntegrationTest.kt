package io.github.salomax.neotool.mcp.graphql

import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.mcp.audit.ToolCallAuditLogEntity
import io.github.salomax.neotool.mcp.audit.ToolCallAuditLogRepository
import io.github.salomax.neotool.mcp.audit.ToolCallAuditOutcome
import io.github.salomax.neotool.mcp.security.ConnectionRevocationRepository
import io.github.salomax.neotool.mcp.test.McpJwtTestFixture
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
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
@DisplayName("MCP GraphQL integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("mcp")
open class McpGraphQLIntegrationTest :
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
    fun `revokeConnection mutation persists a revocation row`() {
        val userId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val token = McpJwtTestFixture.serviceToken(userId = userId, currentAccountId = accountId)

        val response =
            graphQL(
                query =
                    """
                    mutation RevokeConnection(${'$'}userId: ID!, ${'$'}clientId: String!, ${'$'}accountId: ID!) {
                      revokeConnection(userId: ${'$'}userId, clientId: ${'$'}clientId, accountId: ${'$'}accountId) {
                        userId
                        clientId
                        accountId
                      }
                    }
                    """.trimIndent(),
                variables =
                    mapOf(
                        "userId" to userId.toString(),
                        "clientId" to "agent-a",
                        "accountId" to accountId.toString(),
                    ),
                token = token,
            )

        assertThat(response["errors"]).isNull()
        assertThat(connectionRevocationRepository.existsRevocation(userId, "agent-a", accountId)).isTrue()
        assertThat(connectionRevocationRepository.findAll()).hasSize(1)
    }

    @Test
    fun `recentActivity query returns only matching audit entries`() {
        val userId = UUID.randomUUID()
        val token = McpJwtTestFixture.serviceToken(userId = userId, currentAccountId = UUID.randomUUID())
        val newest = Instant.parse("2026-07-30T17:00:00Z")
        val older = newest.minusSeconds(30)

        toolCallAuditLogRepository.saveAll(
            listOf(
                auditRow(UUID.randomUUID(), newest, userId, "agent-a", "tool-new"),
                auditRow(UUID.randomUUID(), older, userId, "agent-a", "tool-old"),
                auditRow(UUID.randomUUID(), newest.plusSeconds(1), userId, "agent-b", "tool-other-client"),
                auditRow(UUID.randomUUID(), newest.plusSeconds(2), UUID.randomUUID(), "agent-a", "tool-other-user"),
            ),
        )

        val response =
            graphQL(
                query =
                    """
                    query RecentActivity(${'$'}userId: ID!, ${'$'}clientId: String!, ${'$'}first: Int!) {
                      recentActivity(userId: ${'$'}userId, clientId: ${'$'}clientId, first: ${'$'}first) {
                        toolName
                        clientId
                        userId
                        occurredAt
                      }
                    }
                    """.trimIndent(),
                variables =
                    mapOf(
                        "userId" to userId.toString(),
                        "clientId" to "agent-a",
                        "first" to 10,
                    ),
                token = token,
            )

        assertThat(response["errors"]).isNull()
        val data = response["data"] as Map<*, *>
        val entries = data["recentActivity"] as List<Map<String, Any?>>
        assertThat(entries).hasSize(2)
        assertThat(entries.map { it["toolName"] }).containsExactly("tool-new", "tool-old")
        assertThat(entries.map { it["clientId"] }).containsOnly("agent-a")
        assertThat(entries.map { it["userId"] }).containsOnly(userId.toString())
    }

    @Test
    fun `graphql rejects unauthenticated recentActivity calls`() {
        val response =
            graphQL(
                query =
                    """
                    query RecentActivity(${'$'}userId: ID!, ${'$'}clientId: String!) {
                      recentActivity(userId: ${'$'}userId, clientId: ${'$'}clientId) {
                        id
                      }
                    }
                    """.trimIndent(),
                variables =
                    mapOf(
                        "userId" to UUID.randomUUID().toString(),
                        "clientId" to "agent-a",
                    ),
                token = null,
            )

        val errors = response["errors"] as List<Map<String, Any?>>
        val extensions = errors.single()["extensions"] as Map<*, *>
        assertThat(extensions["code"]).isEqualTo("AUTH_AUTHENTICATION_REQUIRED")
    }

    private fun graphQL(
        query: String,
        variables: Map<String, Any?>,
        token: String?,
    ): Map<String, Any?> {
        val request =
            HttpRequest
                .POST(
                    "/graphql",
                    mapOf(
                        "query" to query,
                        "variables" to variables,
                    ),
                ).let { base ->
                    if (token == null) {
                        base
                    } else {
                        base.header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    }
                }

        val response = httpClient.toBlocking().retrieve(request)
        return json.readValue(response, Map::class.java) as Map<String, Any?>
    }

    private fun auditRow(
        id: UUID,
        occurredAt: Instant,
        userId: UUID,
        clientId: String,
        toolName: String,
    ) = ToolCallAuditLogEntity(
        id = id,
        occurredAt = occurredAt,
        userId = userId,
        clientId = clientId,
        tokenId = "token-$clientId",
        toolName = toolName,
        arguments = emptyMap(),
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
