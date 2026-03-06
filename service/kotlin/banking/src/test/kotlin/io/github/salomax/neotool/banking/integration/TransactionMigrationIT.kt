package io.github.salomax.neotool.banking.integration

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.util.UUID

/**
 * Verifies banking migrations V0_4, V0_5, V0_6, V0_7, V0_8 and seed/constraint behavior.
 */
@MicronautTest(startApplication = true)
@DisplayName("TransactionMigration IT")
class TransactionMigrationIT : BankingPostgresIntegrationTest() {
    private lateinit var accountId: UUID
    private lateinit var userId: UUID
    private lateinit var financialAccountId: UUID

    @BeforeEach
    fun insertMinimalFinancialAccount() {
        val container = getPgContainer()
        accountId = UUID.randomUUID()
        userId = UUID.randomUUID()
        financialAccountId = UUID.randomUUID()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.financial_accounts (id, account_id, created_by_user_id, type, subtype, name, currency_code, created_at, updated_at)
                VALUES ('$financialAccountId', '$accountId', '$userId', 'BANK', 'CONTA_CORRENTE', 'Mig Bank', 'BRL', NOW(), NOW())
                """.trimIndent(),
            )
        }
    }

    @Nested
    @DisplayName("migrations")
    inner class MigrationsTests {
        @Test
        fun `V0_4 V0_5 V0_6 V0_7 V0_8 apply cleanly on fresh schema`() {
            val container = getPgContainer()
            val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
            DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
                conn.createStatement().executeQuery(
                    "SELECT version FROM flyway_schema_history_banking WHERE success = true ORDER BY installed_rank",
                ).use { rs ->
                    val versions = mutableListOf<String>()
                    while (rs.next()) rs.getString("version")?.let { versions.add(it) }
                    assertThat(versions).contains("0.4", "0.5", "0.6", "0.7", "0.8")
                }
                conn.createStatement().executeQuery(
                    "SELECT table_name FROM information_schema.tables " +
                        "WHERE table_schema = 'banking' AND table_name IN " +
                        "('transactions', 'transaction_categories', 'credit_card_bills', 'transaction_tags')",
                ).use { rs ->
                    val tables = mutableListOf<String>()
                    while (rs.next()) tables.add(rs.getString("table_name"))
                    assertThat(
                        tables,
                    ).containsExactlyInAnyOrder(
                        "transactions",
                        "transaction_categories",
                        "credit_card_bills",
                        "transaction_tags",
                    )
                }
            }
        }
    }

    @Nested
    @DisplayName("transaction_categories seed")
    inner class TransactionCategoriesSeedTests {
        @Test
        fun `exactly 22 root categories subcategory parent_ids resolve`() {
            val container = getPgContainer()
            val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
            DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
                conn.createStatement().executeQuery(
                    "SELECT COUNT(*) AS cnt FROM banking.transaction_categories " +
                        "WHERE parent_id IS NULL AND deleted_at IS NULL",
                ).use { rs ->
                    rs.next()
                    assertThat(rs.getInt("cnt")).isEqualTo(22)
                }
                conn.createStatement().executeQuery(
                    """
                    SELECT c.id FROM banking.transaction_categories c
                    WHERE c.parent_id IS NOT NULL AND c.deleted_at IS NULL
                    AND NOT EXISTS (SELECT 1 FROM banking.transaction_categories p WHERE p.id = c.parent_id AND p.deleted_at IS NULL)
                    """.trimIndent(),
                ).use { rs ->
                    assertThat(rs.next()).isFalse()
                }
            }
        }
    }

    @Nested
    @DisplayName("transactions partial unique index")
    inner class TransactionsUniqueIndexTests {
        @Test
        fun `two NULLs for provider_transaction_id on same account allowed no conflict`() {
            val container = getPgContainer()
            val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
            DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
                conn.createStatement().executeUpdate(
                    """
                    INSERT INTO banking.transactions (id, financial_account_id, account_id, created_by_user_id, belongs_to_user_id, credit_debit_type, type, status, source, amount, currency_code, date, description, created_at, updated_at)
                    VALUES (uuidv7(), '$financialAccountId', '$accountId', '$userId', '$userId', 'DEBIT', 'OTHER_EXPENSE', 'POSTED', 'MANUAL', 10.00, 'BRL', CURRENT_DATE, 'First', NOW(), NOW())
                    """.trimIndent(),
                )
                conn.createStatement().executeUpdate(
                    """
                    INSERT INTO banking.transactions (id, financial_account_id, account_id, created_by_user_id, belongs_to_user_id, credit_debit_type, type, status, source, amount, currency_code, date, description, created_at, updated_at)
                    VALUES (uuidv7(), '$financialAccountId', '$accountId', '$userId', '$userId', 'DEBIT', 'OTHER_EXPENSE', 'POSTED', 'MANUAL', 20.00, 'BRL', CURRENT_DATE, 'Second', NOW(), NOW())
                    """.trimIndent(),
                )
                conn.createStatement().executeQuery(
                    "SELECT COUNT(*) AS cnt FROM banking.transactions " +
                        "WHERE financial_account_id = '$financialAccountId' AND provider_transaction_id IS NULL",
                ).use { rs ->
                    rs.next()
                    assertThat(rs.getInt("cnt")).isEqualTo(2)
                }
            }
        }
    }
}
