package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.banking.config.BankingTestTokenPrincipalDecoder
import io.github.salomax.neotool.banking.graphql.transaction.TransactionResolver
import io.github.salomax.neotool.banking.service.transaction.TransactionService
import io.github.salomax.neotool.common.provenance.OriginChannel
import io.github.salomax.neotool.common.security.agent.AgentCallContext
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.github.salomax.neotool.common.test.transaction.runTransaction
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("MCP change log GraphQL IT")
class McpChangeLogGraphQLIT : BankingPostgresIntegrationTest() {
    @Inject
    lateinit var transactionResolver: TransactionResolver

    @Inject
    lateinit var transactionService: TransactionService

    @Inject
    lateinit var entityManager: EntityManager

    private val accountId = BankingTestTokenPrincipalDecoder.TEST_ACCOUNT_ID
    private val userId = BankingTestTokenPrincipalDecoder.TEST_USER_ID
    private lateinit var financialAccountId: UUID
    private lateinit var otherAccountId: UUID
    private lateinit var otherFinancialAccountId: UUID

    @BeforeEach
    fun seedAccounts() {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            financialAccountId = UUID.randomUUID()
            otherAccountId = UUID.randomUUID()
            otherFinancialAccountId = UUID.randomUUID()
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.financial_accounts (
                    id, account_id, created_by_user_id, type, subtype, name, currency_code, created_at, updated_at
                )
                VALUES
                    ('$financialAccountId', '$accountId', '$userId', 'BANK', 'CONTA_CORRENTE', 'Primary', 'BRL', NOW(), NOW()),
                    ('$otherFinancialAccountId', '$otherAccountId', '$userId', 'BANK', 'CONTA_CORRENTE', 'Other', 'BRL', NOW(), NOW())
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `bulkCategorize writes UPDATE audit rows with before and after snapshots`() {
        val transactionId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        val toolCallId = UUID.randomUUID()
        insertCategory(categoryId, accountId, "Groceries")
        insertTransaction(
            id = transactionId,
            accountId = accountId,
            financialAccountId = financialAccountId,
            description = "Bulk categorize me",
        )

        val result =
            entityManager.runTransaction {
                transactionResolver.bulkCategorize(
                    transactionIds = listOf(transactionId.toString()),
                    categoryId = categoryId.toString(),
                    principal = principal(accountId),
                    agentCallContext = agentCallContext(toolCallId),
                )
            }

        assertThat(result.affectedCount).isEqualTo(1)
        assertThat(result.errors).isEmpty()

        val auditRows =
            transactionResolver.mcpChangeLog(
                clientId = null,
                tokenId = null,
                toolCallId = toolCallId.toString(),
                limit = 50,
                offset = 0,
                principal = principal(accountId),
            )
        assertThat(auditRows).hasSize(1)
        val auditRow = auditRows.single()
        assertThat(auditRow.action).isEqualTo("UPDATE")
        assertThat(auditRow.before["categoryId"]).isNull()
        assertThat(auditRow.after["categoryId"]).isEqualTo(categoryId.toString())
        assertThat(auditRow.after["originChannel"]).isEqualTo("MCP")
        assertThat(auditRow.after["originClientId"]).isEqualTo("mcp-client")

        val storedTx = transactionService.findById(transactionId, accountId)
        assertThat(storedTx.categoryId).isEqualTo(categoryId)
        assertThat(storedTx.originChannel).isEqualTo(OriginChannel.MCP)
        assertThat(storedTx.originClientId).isEqualTo("mcp-client")
        assertThat(storedTx.originToolCallId).isEqualTo(toolCallId)
    }

    @Test
    fun `mcpChangeLog query stays scoped to the caller account`() {
        val toolCallId = UUID.randomUUID()
        val ownTransactionId = UUID.randomUUID()
        val otherTransactionId = UUID.randomUUID()
        insertTransaction(
            id = ownTransactionId,
            accountId = accountId,
            financialAccountId = financialAccountId,
            description = "Own audited tx",
            originChannel = "MCP",
            originClientId = "shared-client",
            originToolCallId = toolCallId,
        )
        insertTransaction(
            id = otherTransactionId,
            accountId = otherAccountId,
            financialAccountId = otherFinancialAccountId,
            description = "Other audited tx",
            originChannel = "MCP",
            originClientId = "shared-client",
            originToolCallId = toolCallId,
        )
        insertChangeLog(
            resourceId = ownTransactionId,
            toolCallId = toolCallId,
            clientId = "shared-client",
            tokenId = "origin-token-a",
            beforeJson = snapshotJson(originChannel = "UI"),
            afterJson =
                snapshotJson(
                    originChannel = "MCP",
                    originClientId = "shared-client",
                    originToolCallId = toolCallId,
                ),
        )
        insertChangeLog(
            resourceId = otherTransactionId,
            toolCallId = toolCallId,
            clientId = "shared-client",
            tokenId = "origin-token-b",
            beforeJson = snapshotJson(originChannel = "UI"),
            afterJson =
                snapshotJson(
                    originChannel = "MCP",
                    originClientId = "shared-client",
                    originToolCallId = toolCallId,
                ),
        )

        val result =
            transactionResolver.mcpChangeLog(
                clientId = "shared-client",
                tokenId = null,
                toolCallId = null,
                limit = 50,
                offset = 0,
                principal = principal(accountId),
            )

        assertThat(result).hasSize(1)
        val entry = result.single()
        assertThat(entry.resourceId).isEqualTo(ownTransactionId)
        assertThat(entry.clientId).isEqualTo("shared-client")
        assertThat(entry.before["originChannel"]).isEqualTo("UI")
        assertThat(entry.after["originChannel"]).isEqualTo("MCP")
    }

    @Test
    fun `mcpChangeLog keeps rows queryable after the audited transaction is soft deleted`() {
        val toolCallId = UUID.randomUUID()
        val transactionId = UUID.randomUUID()
        insertTransaction(
            id = transactionId,
            accountId = accountId,
            financialAccountId = financialAccountId,
            description = "Soft deleted audited tx",
            originChannel = "MCP",
            originClientId = "mcp-client",
            originToolCallId = toolCallId,
        )
        insertChangeLog(
            resourceId = transactionId,
            toolCallId = toolCallId,
            clientId = "mcp-client",
            tokenId = "origin-token",
            beforeJson = snapshotJson(originChannel = "UI"),
            afterJson =
                snapshotJson(
                    originChannel = "MCP",
                    originClientId = "mcp-client",
                    originToolCallId = toolCallId,
                ),
        )
        softDeleteTransaction(transactionId)

        val result =
            transactionResolver.mcpChangeLog(
                clientId = null,
                tokenId = null,
                toolCallId = toolCallId.toString(),
                limit = 50,
                offset = 0,
                principal = principal(accountId),
            )

        assertThat(result).hasSize(1)
        assertThat(result.single().resourceId).isEqualTo(transactionId)
    }

    @Test
    fun `revertToolCallChanges restores transaction state and appends REVERT audit rows`() {
        val transactionId = UUID.randomUUID()
        val toolCallId = UUID.randomUUID()
        val originalCategoryId = UUID.randomUUID()
        val currentCategoryId = UUID.randomUUID()
        val changedAt = Instant.parse("2026-07-24T12:00:00Z")
        insertCategory(originalCategoryId, accountId, "Original")
        insertCategory(currentCategoryId, accountId, "Current")
        insertTransaction(
            id = transactionId,
            accountId = accountId,
            financialAccountId = financialAccountId,
            description = "Needs revert",
            categoryId = currentCategoryId,
            categorySource = "MANUAL",
            originChannel = "MCP",
            originClientId = "mcp-client",
            originToolCallId = toolCallId,
            updatedAt = changedAt,
        )
        insertChangeLog(
            resourceId = transactionId,
            toolCallId = toolCallId,
            clientId = "mcp-client",
            tokenId = "origin-token",
            beforeJson = snapshotJson(categoryId = originalCategoryId, categorySource = "MANUAL", originChannel = "UI"),
            afterJson =
                snapshotJson(
                    categoryId = currentCategoryId,
                    categorySource = "MANUAL",
                    originChannel = "MCP",
                    originClientId = "mcp-client",
                    originToolCallId = toolCallId,
                ),
            createdAt = changedAt,
        )

        val result =
            entityManager.runTransaction {
                transactionResolver.revertToolCallChanges(toolCallId.toString(), principal(accountId))
            }

        assertThat(result.affectedCount).isEqualTo(1)
        assertThat(result.errors).isEmpty()

        val storedTx = transactionService.findById(transactionId, accountId)
        assertThat(storedTx.categoryId).isEqualTo(originalCategoryId)
        assertThat(storedTx.originChannel).isEqualTo(OriginChannel.UI)
        assertThat(storedTx.originClientId).isNull()
        assertThat(storedTx.originToolCallId).isNull()

        val auditRows = loadChangeLogs(toolCallId)
        assertThat(auditRows.map { it.action }).containsExactly("UPDATE", "REVERT")
        assertThat(auditRows.last().afterJson).contains(originalCategoryId.toString())
        assertThat(auditRows.last().afterJson).contains("\"originChannel\": \"UI\"")
    }

    @Test
    fun `revertToolCallChanges skips rows updated after the original audit timestamp`() {
        val transactionId = UUID.randomUUID()
        val toolCallId = UUID.randomUUID()
        val changedAt = Instant.parse("2026-07-24T12:00:00Z")
        insertTransaction(
            id = transactionId,
            accountId = accountId,
            financialAccountId = financialAccountId,
            description = "Conflicted revert",
            originChannel = "MCP",
            originClientId = "mcp-client",
            originToolCallId = toolCallId,
            updatedAt = changedAt.plusSeconds(30),
        )
        insertChangeLog(
            resourceId = transactionId,
            toolCallId = toolCallId,
            clientId = "mcp-client",
            tokenId = "origin-token",
            beforeJson = snapshotJson(originChannel = "UI"),
            afterJson =
                snapshotJson(
                    categorySource = "MANUAL",
                    originChannel = "MCP",
                    originClientId = "mcp-client",
                    originToolCallId = toolCallId,
                ),
            createdAt = changedAt,
        )

        val result =
            entityManager.runTransaction {
                transactionResolver.revertToolCallChanges(toolCallId.toString(), principal(accountId))
            }

        assertThat(result.affectedCount).isZero()
        assertThat(result.errors).singleElement().asString().contains(transactionId.toString())
        assertThat(
            transactionResolver.mcpChangeLog(
                clientId = null,
                tokenId = null,
                toolCallId = toolCallId.toString(),
                limit = 50,
                offset = 0,
                principal = principal(accountId),
            ),
        ).hasSize(1)
        assertThat(transactionService.findById(transactionId, accountId).originChannel)
            .isEqualTo(OriginChannel.MCP)
    }

    @Test
    fun `mcp change log rows are immutable`() {
        val transactionId = UUID.randomUUID()
        val toolCallId = UUID.randomUUID()
        insertTransaction(
            id = transactionId,
            accountId = accountId,
            financialAccountId = financialAccountId,
            description = "Immutable audit",
        )
        val logId =
            insertChangeLog(
                resourceId = transactionId,
                toolCallId = toolCallId,
                clientId = "mcp-client",
                tokenId = "origin-token",
                beforeJson = snapshotJson(originChannel = "UI"),
                afterJson =
                    snapshotJson(
                        originChannel = "MCP",
                        originClientId = "mcp-client",
                        originToolCallId = toolCallId,
                    ),
            )

        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        val ex =
            assertThrows<SQLException> {
                DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
                    conn.prepareStatement(
                        "UPDATE banking.mcp_change_log SET token_id = ? WHERE id = ?",
                    ).use { stmt ->
                        stmt.setString(1, "tampered-token")
                        stmt.setObject(2, logId)
                        stmt.executeUpdate()
                    }
                }
            }

        assertThat(ex.message).contains("banking.mcp_change_log is immutable")
    }

    private fun insertCategory(
        id: UUID,
        accountId: UUID,
        name: String,
    ) {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.transaction_categories (
                    id, account_id, name, applicable_to, is_system, sort_order, created_at, updated_at
                ) VALUES (
                    '$id', '$accountId', '$name', 'EXPENSE', false, 0, NOW(), NOW()
                )
                """.trimIndent(),
            )
        }
    }

    private fun insertTransaction(
        id: UUID,
        accountId: UUID,
        financialAccountId: UUID,
        description: String,
        categoryId: UUID? = null,
        categorySource: String? = null,
        originChannel: String = "UI",
        originClientId: String? = null,
        originToolCallId: UUID? = null,
        updatedAt: Instant = Instant.now(),
    ) {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO banking.transactions (
                    id, financial_account_id, account_id, created_by_user_id, belongs_to_user_id,
                    credit_debit_type, type, status, source, amount, currency_code, date, description,
                    category_id, category_source, origin_channel, origin_client_id, origin_tool_call_id,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'DEBIT', 'OTHER_EXPENSE', 'POSTED', 'MANUAL', ?, 'BRL', ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, financialAccountId)
                stmt.setObject(3, accountId)
                stmt.setObject(4, userId)
                stmt.setObject(5, userId)
                stmt.setBigDecimal(6, BigDecimal("10.00"))
                stmt.setObject(7, LocalDate.of(2026, 7, 24))
                stmt.setString(8, description)
                stmt.setObject(9, categoryId)
                stmt.setString(10, categorySource)
                stmt.setString(11, originChannel)
                stmt.setString(12, originClientId)
                stmt.setObject(13, originToolCallId)
                stmt.setTimestamp(14, Timestamp.from(updatedAt.minusSeconds(60)))
                stmt.setTimestamp(15, Timestamp.from(updatedAt))
                stmt.executeUpdate()
            }
        }
    }

    private fun insertChangeLog(
        resourceId: UUID,
        toolCallId: UUID,
        clientId: String,
        tokenId: String,
        beforeJson: String,
        afterJson: String,
        createdAt: Instant = Instant.now(),
    ): UUID {
        val id = UUID.randomUUID()
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO banking.mcp_change_log (
                    id, tool_call_id, client_id, token_id, resource_type, resource_id, action, before, after, created_at
                ) VALUES (?, ?, ?, ?, 'TRANSACTION', ?, 'UPDATE', CAST(? AS jsonb), CAST(? AS jsonb), ?)
                """.trimIndent(),
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, toolCallId)
                stmt.setString(3, clientId)
                stmt.setString(4, tokenId)
                stmt.setObject(5, resourceId)
                stmt.setString(6, beforeJson)
                stmt.setString(7, afterJson)
                stmt.setTimestamp(8, Timestamp.from(createdAt))
                stmt.executeUpdate()
            }
        }
        return id
    }

    private fun loadChangeLogs(toolCallId: UUID): List<StoredChangeLog> {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            conn.prepareStatement(
                """
                SELECT action, before::text, after::text
                FROM banking.mcp_change_log
                WHERE tool_call_id = ?
                ORDER BY created_at ASC, id ASC
                """.trimIndent(),
            ).use { stmt ->
                stmt.setObject(1, toolCallId)
                stmt.executeQuery().use { rs ->
                    val rows = mutableListOf<StoredChangeLog>()
                    while (rs.next()) {
                        rows +=
                            StoredChangeLog(
                                action = rs.getString(1),
                                beforeJson = rs.getString(2),
                                afterJson = rs.getString(3),
                            )
                    }
                    return rows
                }
            }
        }
    }

    private fun loadTransactionState(id: UUID): StoredTransactionState {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            conn.prepareStatement(
                """
                SELECT category_id, origin_channel, origin_client_id, origin_tool_call_id
                FROM banking.transactions
                WHERE id = ? AND deleted_at IS NULL
                """.trimIndent(),
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.executeQuery().use { rs ->
                    require(rs.next()) { "Transaction not found for id=$id" }
                    return StoredTransactionState(
                        categoryId = rs.getObject("category_id", UUID::class.java),
                        originChannel = rs.getString("origin_channel"),
                        originClientId = rs.getString("origin_client_id"),
                        originToolCallId = rs.getObject("origin_tool_call_id", UUID::class.java),
                    )
                }
            }
        }
    }

    private fun softDeleteTransaction(id: UUID) {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            conn.prepareStatement(
                """
                UPDATE banking.transactions
                   SET deleted_at = NOW(),
                       updated_at = NOW()
                 WHERE id = ?
                """.trimIndent(),
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.executeUpdate()
            }
        }
    }

    private fun snapshotJson(
        categoryId: UUID? = null,
        categorySource: String? = null,
        originChannel: String,
        originClientId: String? = null,
        originToolCallId: UUID? = null,
    ): String =
        """
        {
          "categoryId": ${jsonValue(categoryId?.toString())},
          "categorySource": ${jsonValue(categorySource)},
          "merchantId": null,
          "type": "OTHER_EXPENSE",
          "description": "Sample",
          "tags": [],
          "originChannel": ${jsonValue(originChannel)},
          "originClientId": ${jsonValue(originClientId)},
          "originToolCallId": ${jsonValue(originToolCallId?.toString())}
        }
        """.trimIndent()

    private fun jsonValue(value: String?): String = value?.let { "\"$it\"" } ?: "null"

    private fun principal(accountId: UUID) =
        RequestPrincipal(
            principalType = PrincipalType.USER,
            userId = userId,
            serviceId = null,
            token = "test-token",
            tokenId = "test-token-id",
            permissionsFromToken = emptyList(),
            currentAccountId = accountId,
        )

    private fun agentCallContext(toolCallId: UUID) =
        AgentCallContext(
            toolCallId = toolCallId,
            originClientId = "mcp-client",
            originTokenId = "origin-token",
        )

    private data class StoredChangeLog(
        val action: String,
        val beforeJson: String,
        val afterJson: String,
    )

    private data class StoredTransactionState(
        val categoryId: UUID?,
        val originChannel: String,
        val originClientId: String?,
        val originToolCallId: UUID?,
    )
}
