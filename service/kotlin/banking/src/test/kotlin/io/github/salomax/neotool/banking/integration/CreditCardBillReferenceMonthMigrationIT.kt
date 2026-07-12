package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.banking.repo.transaction.CreditCardBillRepository
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.time.LocalDate
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("CreditCardBill reference month migration IT")
class CreditCardBillReferenceMonthMigrationIT : BankingPostgresIntegrationTest() {
    @Inject
    lateinit var creditCardBillRepository: CreditCardBillRepository

    @Test
    fun `reference_month is backfilled to first day of due month`() {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        val accountId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        val billId = UUID.randomUUID()

        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeUpdate(
                    """
                    INSERT INTO banking.financial_accounts (
                        id, account_id, created_by_user_id, type, subtype, name, currency_code,
                        include_in_net_worth, include_in_dashboard, created_at, updated_at
                    )
                    VALUES (
                        '$financialAccountId', '$accountId', '$userId',
                        'CREDIT', 'CREDIT_CARD',
                        'Migration Card', 'BRL',
                        TRUE, TRUE, NOW(), NOW()
                    )
                    """.trimIndent(),
                )
                stmt.executeUpdate(
                    """
                    INSERT INTO banking.credit_card_bills (
                        id, financial_account_id, account_id,
                        closing_date, due_date, reference_month,
                        status, created_at, updated_at
                    )
                    VALUES (
                        '$billId', '$financialAccountId', '$accountId',
                        '2025-07-29', '2025-08-15', '2025-08-01',
                        'OPEN', NOW(), NOW()
                    )
                    """.trimIndent(),
                )

                stmt.executeQuery(
                    """
                    SELECT reference_month, due_date
                      FROM banking.credit_card_bills
                     WHERE id = '$billId'
                    """.trimIndent(),
                ).use { rs ->
                    assertThat(rs.next()).isTrue()
                    assertThat(rs.getDate("reference_month").toLocalDate())
                        .isEqualTo(LocalDate.of(2025, 8, 1))
                    assertThat(rs.getDate("due_date").toLocalDate())
                        .isEqualTo(LocalDate.of(2025, 8, 15))
                }
            }
        }
    }

    @Test
    fun `findOrCreate keeps a single active bill per reference month under sequential inserts`() {
        val accountId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()

        insertCardAccount(accountId, userId, financialAccountId)

        val first =
            creditCardBillRepository.findOrCreate(
                financialAccountId = financialAccountId,
                accountId = accountId,
                closingDate = LocalDate.of(2025, 12, 7),
                dueDate = LocalDate.of(2025, 12, 25),
                referenceMonth = LocalDate.of(2025, 12, 1),
            )
        val second =
            creditCardBillRepository.findOrCreate(
                financialAccountId = financialAccountId,
                accountId = accountId,
                closingDate = LocalDate.of(2025, 12, 10),
                dueDate = LocalDate.of(2025, 12, 28),
                referenceMonth = LocalDate.of(2025, 12, 1),
            )

        assertThat(second.id).isEqualTo(first.id)
        assertThat(second.closingDate).isEqualTo(LocalDate.of(2025, 12, 10))
        assertThat(second.dueDate).isEqualTo(LocalDate.of(2025, 12, 28))

        val listed = creditCardBillRepository.listByAccount(financialAccountId, accountId)
        assertThat(listed).hasSize(1)
        assertThat(listed.single().referenceMonth).isEqualTo(LocalDate.of(2025, 12, 1))
    }

    private fun insertCardAccount(
        accountId: UUID,
        userId: UUID,
        financialAccountId: UUID,
    ) {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeUpdate(
                    """
                    INSERT INTO banking.financial_accounts (
                        id, account_id, created_by_user_id, type, subtype, name, currency_code,
                        include_in_net_worth, include_in_dashboard, created_at, updated_at
                    )
                    VALUES (
                        '$financialAccountId', '$accountId', '$userId',
                        'CREDIT', 'CREDIT_CARD',
                        'Migration Card', 'BRL',
                        TRUE, TRUE, NOW(), NOW()
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
