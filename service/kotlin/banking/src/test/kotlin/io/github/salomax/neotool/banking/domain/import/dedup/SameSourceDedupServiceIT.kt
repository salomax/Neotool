package io.github.salomax.neotool.banking.service.import.dedup

import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionSource
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.integration.BankingPostgresIntegrationTest
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.sql.DriverManager
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("SameSourceDedupService IT")
class SameSourceDedupServiceIT : BankingPostgresIntegrationTest() {
    @Inject
    lateinit var sameSourceDedupService: SameSourceDedupService

    @Inject
    lateinit var transactionRepository: TransactionRepository

    private lateinit var accountId: UUID
    private lateinit var userId: UUID
    private lateinit var financialAccountId: UUID
    private val today = LocalDate.now()
    private val providerTxId = "same-source-dedup-it-${UUID.randomUUID()}"

    @BeforeEach
    fun insertTestData() {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            userId = UUID.randomUUID()
            accountId = UUID.randomUUID()
            financialAccountId = UUID.randomUUID()
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.financial_accounts (id, account_id, created_by_user_id, type, subtype, name, currency_code, created_at, updated_at)
                VALUES ('$financialAccountId', '$accountId', '$userId', 'BANK', 'CONTA_CORRENTE', 'IT Same', 'BRL', NOW(), NOW())
                """.trimIndent(),
            )
        }
    }

    private fun transactionWithProvider(
        description: String,
        source: TransactionSource,
    ) = Transaction(
        financialAccountId = financialAccountId,
        accountId = accountId,
        createdByUserId = userId,
        creditDebitType = CreditDebitType.DEBIT,
        type = TransactionType.OTHER_EXPENSE,
        status = TransactionStatus.POSTED,
        source = source,
        amount = BigDecimal("10.00"),
        date = today,
        description = description,
        providerTransactionId = providerTxId,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    @Nested
    @DisplayName("isDuplicate — SQL correctness")
    inner class IsDuplicateSqlTests {
        @Test
        fun `returns true when exact match exists same financial_account_id provider_transaction_id and source`() {
            transactionRepository.save(transactionWithProvider("Existing", TransactionSource.MANUAL))

            val result =
                sameSourceDedupService.isDuplicate(
                    financialAccountId = financialAccountId,
                    providerTransactionId = providerTxId,
                    source = TransactionSource.MANUAL,
                )

            assertThat(result).isTrue()
        }

        @Test
        fun `returns false when no row matches`() {
            val result =
                sameSourceDedupService.isDuplicate(
                    financialAccountId = financialAccountId,
                    providerTransactionId = "nonexistent-id",
                    source = TransactionSource.MANUAL,
                )

            assertThat(result).isFalse()
        }

        @Test
        fun `returns false when provider_transaction_id differs`() {
            transactionRepository.save(transactionWithProvider("Existing", TransactionSource.MANUAL))

            val result =
                sameSourceDedupService.isDuplicate(
                    financialAccountId = financialAccountId,
                    providerTransactionId = "other-provider-id",
                    source = TransactionSource.MANUAL,
                )

            assertThat(result).isFalse()
        }

        @Test
        fun `returns false when source differs same provider id`() {
            transactionRepository.save(transactionWithProvider("MANUAL row", TransactionSource.MANUAL))

            val result =
                sameSourceDedupService.isDuplicate(
                    financialAccountId = financialAccountId,
                    providerTransactionId = providerTxId,
                    source = TransactionSource.OPEN_FINANCE,
                )

            assertThat(result).isFalse()
        }

        @Test
        fun `returns false when transaction is soft-deleted`() {
            val saved = transactionRepository.save(transactionWithProvider("To delete", TransactionSource.MANUAL))
            transactionRepository.softDelete(saved.id!!, accountId)

            val result =
                sameSourceDedupService.isDuplicate(
                    financialAccountId = financialAccountId,
                    providerTransactionId = providerTxId,
                    source = TransactionSource.MANUAL,
                )

            assertThat(result).isFalse()
        }

        @Test
        fun `returns false when financial_account_id differs no cross-account leak`() {
            transactionRepository.save(transactionWithProvider("On account A", TransactionSource.MANUAL))
            val otherFinancialAccountId = UUID.randomUUID()
            val otherAccountId = UUID.randomUUID()
            val container = getPgContainer()
            val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
            DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
                conn.createStatement().executeUpdate(
                    """
                    INSERT INTO banking.financial_accounts (id, account_id, created_by_user_id, type, subtype, name, currency_code, created_at, updated_at)
                    VALUES ('$otherFinancialAccountId', '$otherAccountId', '$userId', 'BANK', 'CONTA_CORRENTE', 'Other', 'BRL', NOW(), NOW())
                    """.trimIndent(),
                )
            }

            val result =
                sameSourceDedupService.isDuplicate(
                    financialAccountId = otherFinancialAccountId,
                    providerTransactionId = providerTxId,
                    source = TransactionSource.MANUAL,
                )

            assertThat(result).isFalse()
        }
    }
}
