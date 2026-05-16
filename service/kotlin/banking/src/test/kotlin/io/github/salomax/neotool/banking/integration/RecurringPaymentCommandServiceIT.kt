package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.RecurringFrequency
import io.github.salomax.neotool.banking.domain.transaction.RecurringPaymentStatus
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.repo.transaction.RecurringPaymentRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.github.salomax.neotool.banking.service.transaction.CreateRecurringPaymentCommand
import io.github.salomax.neotool.banking.service.transaction.CreateRecurringPaymentFromTransactionCommand
import io.github.salomax.neotool.banking.service.transaction.RecurringPaymentCommandService
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ValidationException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.sql.DriverManager
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("RecurringPaymentCommandService IT")
class RecurringPaymentCommandServiceIT : BankingPostgresIntegrationTest() {
    @Inject
    lateinit var recurringPaymentCommandService: RecurringPaymentCommandService

    @Inject
    lateinit var recurringPaymentRepository: RecurringPaymentRepository

    @Inject
    lateinit var transactionRepository: TransactionRepository

    private lateinit var accountId: UUID
    private lateinit var userId: UUID
    private lateinit var financialAccountId: UUID

    @BeforeEach
    fun seedData() {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            accountId = UUID.randomUUID()
            userId = UUID.randomUUID()
            financialAccountId = UUID.randomUUID()
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.financial_accounts (id, account_id, created_by_user_id, type, subtype, name, currency_code, created_at, updated_at)
                VALUES ('$financialAccountId', '$accountId', '$userId', 'BANK', 'CONTA_CORRENTE', 'IT Recurring', 'BRL', NOW(), NOW())
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `cancel keeps template visible with CANCELLED status`() {
        val created =
            recurringPaymentCommandService.createRecurringPayment(
                accountId = accountId,
                input =
                    CreateRecurringPaymentCommand(
                        financialAccountId = financialAccountId,
                        name = "Comgas",
                        creditDebitType = CreditDebitType.DEBIT,
                        type = TransactionType.BOLETO,
                        expectedAmount = BigDecimal("120.00"),
                        currencyCode = "BRL",
                        categoryId = null,
                        merchantId = null,
                        frequency = RecurringFrequency.MONTHLY,
                        dayOfMonth = 10,
                        intervalDays = null,
                        remindDaysBefore = 3,
                        firstDueDate = LocalDate.now().plusDays(3),
                        notes = null,
                    ),
            )

        recurringPaymentCommandService.cancelRecurringPayment(accountId, requireNotNull(created.id))

        val found = recurringPaymentRepository.findById(requireNotNull(created.id), accountId)
        assertThat(found).isNotNull
        assertThat(found?.status).isEqualTo(RecurringPaymentStatus.CANCELLED)
    }

    @Test
    fun `resume throws INVALID_STATE for cancelled recurring`() {
        val created =
            recurringPaymentCommandService.createRecurringPayment(
                accountId = accountId,
                input =
                    CreateRecurringPaymentCommand(
                        financialAccountId = financialAccountId,
                        name = "Netflix",
                        creditDebitType = CreditDebitType.DEBIT,
                        type = TransactionType.CARD,
                        expectedAmount = BigDecimal("59.90"),
                        currencyCode = "BRL",
                        categoryId = null,
                        merchantId = null,
                        frequency = RecurringFrequency.MONTHLY,
                        dayOfMonth = 5,
                        intervalDays = null,
                        remindDaysBefore = 3,
                        firstDueDate = LocalDate.now().plusDays(5),
                        notes = null,
                    ),
            )

        recurringPaymentCommandService.cancelRecurringPayment(accountId, requireNotNull(created.id))

        assertThatThrownBy {
            recurringPaymentCommandService.resumeRecurringPayment(accountId, requireNotNull(created.id))
        }
            .isInstanceOf(ValidationException::class.java)
            .extracting("errorCode")
            .isEqualTo(CommonErrorCode.INVALID_STATE)
    }

    @Test
    fun `create from transaction honors explicit null financial account override`() {
        val tx =
            transactionRepository.save(
                Transaction(
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("88.00"),
                    date = LocalDate.now(),
                    description = "Internet",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                ),
            )

        val created =
            recurringPaymentCommandService.createRecurringPaymentFromTransaction(
                accountId = accountId,
                transactionId = requireNotNull(tx.id),
                input =
                    CreateRecurringPaymentFromTransactionCommand(
                        financialAccountIdOverrideDefined = true,
                        financialAccountIdOverride = null,
                        name = "Internet",
                        type = TransactionType.OTHER_EXPENSE,
                        expectedAmount = BigDecimal("88.00"),
                        currencyCode = "BRL",
                        categoryIdDefined = false,
                        categoryId = null,
                        merchantIdDefined = false,
                        merchantId = null,
                        frequency = RecurringFrequency.MONTHLY,
                        dayOfMonth = 10,
                        intervalDays = null,
                        remindDaysBefore = 3,
                        firstDueDate = LocalDate.now().plusDays(10),
                        notes = null,
                    ),
            )

        assertThat(created.financialAccountId).isNull()
    }
}
