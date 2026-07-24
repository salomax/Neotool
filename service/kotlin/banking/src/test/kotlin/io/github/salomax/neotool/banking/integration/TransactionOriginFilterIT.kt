package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.banking.graphql.transaction.TransactionResolver
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.sql.DriverManager
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@MicronautTest(startApplication = true)
class TransactionOriginFilterIT : BankingPostgresIntegrationTest() {
    @Inject
    lateinit var transactionResolver: TransactionResolver

    private lateinit var accountId: UUID
    private lateinit var otherAccountId: UUID
    private lateinit var userId: UUID
    private lateinit var financialAccountId: UUID
    private lateinit var otherFinancialAccountId: UUID
    private val today = LocalDate.now()

    @BeforeEach
    fun seedAccounts() {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            userId = UUID.randomUUID()
            accountId = UUID.randomUUID()
            otherAccountId = UUID.randomUUID()
            financialAccountId = UUID.randomUUID()
            otherFinancialAccountId = UUID.randomUUID()
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.financial_accounts (id, account_id, created_by_user_id, type, subtype, name, currency_code, created_at, updated_at)
                VALUES
                    ('$financialAccountId', '$accountId', '$userId', 'BANK', 'CONTA_CORRENTE', 'Primary', 'BRL', NOW(), NOW()),
                    ('$otherFinancialAccountId', '$otherAccountId', '$userId', 'BANK', 'CONTA_CORRENTE', 'Other', 'BRL', NOW(), NOW())
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `transactions filtered by originClientId stay scoped to the caller account`() {
        val matchingTxId = UUID.randomUUID()
        val nonMatchingTxId = UUID.randomUUID()
        val otherAccountMatchingTxId = UUID.randomUUID()
        val toolCallId = UUID.randomUUID()
        insertTransaction(
            id = matchingTxId,
            accountId = accountId,
            financialAccountId = financialAccountId,
            description = "MCP matching",
            originClientId = "mcp-client-a",
            originToolCallId = toolCallId,
        )
        insertTransaction(
            id = nonMatchingTxId,
            accountId = accountId,
            financialAccountId = financialAccountId,
            description = "MCP other client",
            originClientId = "mcp-client-b",
            originToolCallId = UUID.randomUUID(),
        )
        insertTransaction(
            id = otherAccountMatchingTxId,
            accountId = otherAccountId,
            financialAccountId = otherFinancialAccountId,
            description = "Other account same client",
            originClientId = "mcp-client-a",
            originToolCallId = UUID.randomUUID(),
        )

        val result =
            transactionResolver.transactions(
                financialAccountId = null,
                belongsToUserId = null,
                belongsToName = null,
                search = null,
                tags = null,
                billId = null,
                categoryId = null,
                uncategorized = null,
                creditDebitType = null,
                types = null,
                originClientId = "mcp-client-a",
                originToolCallId = null,
                month = today.monthValue,
                year = today.year,
                first = 20,
                after = null,
                principal = principal(accountId),
            )

        assertThat(result.edges).hasSize(1)
        assertThat(result.edges.single().node.id).isEqualTo(matchingTxId)
        assertThat(result.totalCount).isEqualTo(1)
    }

    private fun insertTransaction(
        id: UUID,
        accountId: UUID,
        financialAccountId: UUID,
        description: String,
        originClientId: String,
        originToolCallId: UUID,
    ) {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO banking.transactions (
                    id, financial_account_id, account_id, created_by_user_id, belongs_to_user_id,
                    credit_debit_type, type, status, source, amount, currency_code, date, description,
                    origin_channel, origin_client_id, origin_tool_call_id, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'DEBIT', 'OTHER_EXPENSE', 'POSTED', 'MANUAL', ?, 'BRL', ?, ?, 'MCP', ?, ?, ?, ?)
                """.trimIndent(),
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, financialAccountId)
                stmt.setObject(3, accountId)
                stmt.setObject(4, userId)
                stmt.setObject(5, userId)
                stmt.setBigDecimal(6, BigDecimal("10.00"))
                stmt.setObject(7, today)
                stmt.setString(8, description)
                stmt.setString(9, originClientId)
                stmt.setObject(10, originToolCallId)
                stmt.setTimestamp(11, Timestamp.from(Instant.now()))
                stmt.setTimestamp(12, Timestamp.from(Instant.now()))
                stmt.executeUpdate()
            }
        }
    }

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
}
