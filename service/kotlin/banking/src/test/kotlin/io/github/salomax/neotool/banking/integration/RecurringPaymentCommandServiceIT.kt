package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.RecurringFrequency
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.repo.transaction.RecurringPaymentRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.github.salomax.neotool.banking.service.transaction.CreateRecurringPaymentCommand
import io.github.salomax.neotool.banking.service.transaction.CreateRecurringPaymentFromTransactionCommand
import io.github.salomax.neotool.banking.service.transaction.RecurringPaymentCommandService
import io.github.salomax.neotool.banking.service.transaction.UpdateRecurringPaymentCommand
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.NotFoundException
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
    fun `delete soft-deletes template — findById returns null`() {
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

        val deleted =
            recurringPaymentCommandService.deleteRecurringPayment(accountId, requireNotNull(created.id))
        assertThat(deleted).isTrue()

        val found = recurringPaymentRepository.findById(requireNotNull(created.id), accountId)
        assertThat(found).isNull()
    }

    @Test
    fun `delete clears recurring_payment_id on linked transactions`() {
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
                        financialAccountIdOverrideDefined = false,
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

        val linkedBefore = transactionRepository.findById(requireNotNull(tx.id), accountId)
        assertThat(linkedBefore?.recurringPaymentId).isEqualTo(created.id)

        recurringPaymentCommandService.deleteRecurringPayment(accountId, requireNotNull(created.id))

        val linkedAfter = transactionRepository.findById(requireNotNull(tx.id), accountId)
        assertThat(linkedAfter?.recurringPaymentId).isNull()
    }

    @Test
    fun `delete then resume throws not found`() {
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

        recurringPaymentCommandService.deleteRecurringPayment(accountId, requireNotNull(created.id))

        assertThatThrownBy {
            recurringPaymentCommandService.resumeRecurringPayment(accountId, requireNotNull(created.id))
        }
            .isInstanceOf(NotFoundException::class.java)
            .extracting("errorCode")
            .isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND)
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

    @Test
    fun `create from transaction defaults firstDueDate to transaction date when omitted`() {
        val txDate = LocalDate.now().minusDays(2)
        val tx =
            transactionRepository.save(
                Transaction(
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("42.00"),
                    date = txDate,
                    description = "Assinatura",
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
                        financialAccountIdOverrideDefined = false,
                        financialAccountIdOverride = null,
                        name = null,
                        type = null,
                        expectedAmount = null,
                        currencyCode = null,
                        categoryIdDefined = false,
                        categoryId = null,
                        merchantIdDefined = false,
                        merchantId = null,
                        frequency = null,
                        dayOfMonth = null,
                        intervalDays = null,
                        remindDaysBefore = null,
                        firstDueDate = null,
                        notes = null,
                    ),
            )

        assertThat(created.nextDueDate).isEqualTo(txDate)
    }

    @Test
    fun `update recurring payment persists financial account and credit debit changes`() {
        val created =
            recurringPaymentCommandService.createRecurringPayment(
                accountId = accountId,
                input =
                    CreateRecurringPaymentCommand(
                        financialAccountId = financialAccountId,
                        name = "Internet",
                        creditDebitType = CreditDebitType.DEBIT,
                        type = TransactionType.OTHER_EXPENSE,
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

        val updated =
            recurringPaymentCommandService.updateRecurringPayment(
                accountId = accountId,
                recurringPaymentId = requireNotNull(created.id),
                input =
                    UpdateRecurringPaymentCommand(
                        financialAccountIdDefined = true,
                        financialAccountId = null,
                        name = null,
                        creditDebitType = CreditDebitType.CREDIT,
                        expectedAmountDefined = false,
                        expectedAmount = null,
                        categoryIdDefined = false,
                        categoryId = null,
                        merchantIdDefined = false,
                        merchantId = null,
                        frequency = null,
                        dayOfMonthDefined = false,
                        dayOfMonth = null,
                        intervalDaysDefined = false,
                        intervalDays = null,
                        remindDaysBefore = null,
                        nextDueDate = null,
                        notesDefined = false,
                        notes = null,
                    ),
            )

        assertThat(updated.financialAccountId).isNull()
        assertThat(updated.creditDebitType).isEqualTo(CreditDebitType.CREDIT)
    }

    @Test
    fun `link transaction to paused recurring throws invalid state`() {
        val tx =
            transactionRepository.save(
                Transaction(
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("110.00"),
                    date = LocalDate.now(),
                    description = "Internet",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                ),
            )
        val recurring =
            recurringPaymentCommandService.createRecurringPayment(
                accountId = accountId,
                input =
                    CreateRecurringPaymentCommand(
                        financialAccountId = financialAccountId,
                        name = "Internet",
                        creditDebitType = CreditDebitType.DEBIT,
                        type = TransactionType.OTHER_EXPENSE,
                        expectedAmount = BigDecimal("110.00"),
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
        recurringPaymentCommandService.pauseRecurringPayment(accountId, requireNotNull(recurring.id))

        assertThatThrownBy {
            recurringPaymentCommandService.linkTransactionToRecurring(
                accountId = accountId,
                transactionId = requireNotNull(tx.id),
                recurringPaymentId = requireNotNull(recurring.id),
            )
        }
            .isInstanceOf(ValidationException::class.java)
            .extracting("errorCode")
            .isEqualTo(CommonErrorCode.INVALID_STATE)
    }

    @Test
    fun `link pending transaction to recurring throws invalid state`() {
        val tx =
            transactionRepository.save(
                Transaction(
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    status = TransactionStatus.PENDING,
                    amount = BigDecimal("110.00"),
                    date = LocalDate.now(),
                    description = "Internet",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                ),
            )
        val recurring =
            recurringPaymentCommandService.createRecurringPayment(
                accountId = accountId,
                input =
                    CreateRecurringPaymentCommand(
                        financialAccountId = financialAccountId,
                        name = "Internet",
                        creditDebitType = CreditDebitType.DEBIT,
                        type = TransactionType.OTHER_EXPENSE,
                        expectedAmount = BigDecimal("110.00"),
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

        assertThatThrownBy {
            recurringPaymentCommandService.linkTransactionToRecurring(
                accountId = accountId,
                transactionId = requireNotNull(tx.id),
                recurringPaymentId = requireNotNull(recurring.id),
            )
        }
            .isInstanceOf(ValidationException::class.java)
            .extracting("errorCode")
            .isEqualTo(CommonErrorCode.INVALID_STATE)
    }
}
