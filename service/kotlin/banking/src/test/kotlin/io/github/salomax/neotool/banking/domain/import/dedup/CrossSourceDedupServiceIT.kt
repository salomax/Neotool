package io.github.salomax.neotool.banking.service.import.dedup

import io.github.salomax.neotool.banking.service.import.dedup.*
import io.github.salomax.neotool.banking.service.import.*
import io.github.salomax.neotool.banking.domain.import.ParsedTransaction
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.github.salomax.neotool.banking.domain.transaction.TransactionSource
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.integration.BankingPostgresIntegrationTest
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
@DisplayName("CrossSourceDedupService IT")
class CrossSourceDedupServiceIT : BankingPostgresIntegrationTest() {
    @Inject
    lateinit var crossSourceDedupService: CrossSourceDedupService

    @Inject
    lateinit var transactionRepository: TransactionRepository

    private lateinit var accountId: UUID
    private lateinit var userId: UUID
    private lateinit var financialAccountId: UUID
    private val today = LocalDate.now()
    private val amount = BigDecimal("42.10")

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
                VALUES ('$financialAccountId', '$accountId', '$userId', 'BANK', 'CONTA_CORRENTE', 'IT Cross', 'BRL', NOW(), NOW())
                """.trimIndent(),
            )
        }
    }

    private fun baseTransaction(
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
        amount = amount,
        date = today,
        description = description,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    @Nested
    @DisplayName("findCandidates — SQL correctness")
    inner class FindCandidatesSqlTests {
        @Test
        fun `returns existing transaction when date amount and financial_account_id match and source differs`() {
            transactionRepository.save(baseTransaction("MANUAL row", TransactionSource.MANUAL))

            val parsed =
                ParsedTransaction(
                    creditDebitType = CreditDebitType.DEBIT,
                    amount = amount,
                    date = today,
                    description = "Incoming OPEN_FINANCE",
                )
            val candidates =
                crossSourceDedupService.findCandidates(
                    financialAccountId = financialAccountId,
                    parsed = parsed,
                    incomingSource = TransactionSource.OPEN_FINANCE,
                )

            assertThat(candidates).hasSize(1)
            assertThat(candidates[0].source).isEqualTo(TransactionSource.MANUAL)
            assertThat(candidates[0].amount).isEqualByComparingTo(amount)
            assertThat(candidates[0].date).isEqualTo(today)
            assertThat(candidates[0].description).isEqualTo("MANUAL row")
        }

        @Test
        fun `does NOT return same-source match Level 1 is separate`() {
            transactionRepository.save(baseTransaction("MANUAL only", TransactionSource.MANUAL))

            val parsed =
                ParsedTransaction(
                    creditDebitType = CreditDebitType.DEBIT,
                    amount = amount,
                    date = today,
                    description = "Also MANUAL",
                )
            val candidates =
                crossSourceDedupService.findCandidates(
                    financialAccountId = financialAccountId,
                    parsed = parsed,
                    incomingSource = TransactionSource.MANUAL,
                )

            assertThat(candidates).isEmpty()
        }

        @Test
        fun `does NOT return soft-deleted transactions`() {
            val saved = transactionRepository.save(baseTransaction("To delete", TransactionSource.MANUAL))
            transactionRepository.softDelete(saved.id!!, accountId)

            val parsed =
                ParsedTransaction(
                    creditDebitType = CreditDebitType.DEBIT,
                    amount = amount,
                    date = today,
                    description = "Incoming",
                )
            val candidates =
                crossSourceDedupService.findCandidates(
                    financialAccountId = financialAccountId,
                    parsed = parsed,
                    incomingSource = TransactionSource.OPEN_FINANCE,
                )

            assertThat(candidates).isEmpty()
        }

        @Test
        fun `returns empty when no cross-source conflict`() {
            val parsed =
                ParsedTransaction(
                    creditDebitType = CreditDebitType.DEBIT,
                    amount = amount,
                    date = today,
                    description = "No existing",
                )
            val candidates =
                crossSourceDedupService.findCandidates(
                    financialAccountId = financialAccountId,
                    parsed = parsed,
                    incomingSource = TransactionSource.OPEN_FINANCE,
                )

            assertThat(candidates).isEmpty()
        }

        @Test
        fun `returns empty when amount differs`() {
            transactionRepository.save(baseTransaction("MANUAL", TransactionSource.MANUAL))

            val parsed =
                ParsedTransaction(
                    creditDebitType = CreditDebitType.DEBIT,
                    amount = BigDecimal("99.99"),
                    date = today,
                    description = "Different amount",
                )
            val candidates =
                crossSourceDedupService.findCandidates(
                    financialAccountId = financialAccountId,
                    parsed = parsed,
                    incomingSource = TransactionSource.OPEN_FINANCE,
                )

            assertThat(candidates).isEmpty()
        }

        @Test
        fun `returns empty when date differs`() {
            transactionRepository.save(baseTransaction("MANUAL", TransactionSource.MANUAL))

            val parsed =
                ParsedTransaction(
                    creditDebitType = CreditDebitType.DEBIT,
                    amount = amount,
                    date = today.plusDays(1),
                    description = "Different date",
                )
            val candidates =
                crossSourceDedupService.findCandidates(
                    financialAccountId = financialAccountId,
                    parsed = parsed,
                    incomingSource = TransactionSource.OPEN_FINANCE,
                )

            assertThat(candidates).isEmpty()
        }

        @Test
        fun `returns empty when querying different financial_account_id no cross-account leak`() {
            transactionRepository.save(baseTransaction("MANUAL on A", TransactionSource.MANUAL))
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

            val parsed =
                ParsedTransaction(
                    creditDebitType = CreditDebitType.DEBIT,
                    amount = amount,
                    date = today,
                    description = "Incoming",
                )
            val candidates =
                crossSourceDedupService.findCandidates(
                    financialAccountId = otherFinancialAccountId,
                    parsed = parsed,
                    incomingSource = TransactionSource.OPEN_FINANCE,
                )

            assertThat(candidates).isEmpty()
        }
    }
}
