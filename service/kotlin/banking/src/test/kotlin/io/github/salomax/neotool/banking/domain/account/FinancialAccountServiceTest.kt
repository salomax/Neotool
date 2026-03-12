package io.github.salomax.neotool.banking.domain.account

import io.github.salomax.neotool.banking.domain.issuer.IssuerRepository
import io.github.salomax.neotool.banking.domain.transaction.CreateTransactionCommand
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionService
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.common.error.ValidationException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@DisplayName("FinancialAccountService")
class FinancialAccountServiceTest {
    private lateinit var financialAccountRepository: FinancialAccountRepository
    private lateinit var issuerRepository: IssuerRepository
    private lateinit var transactionService: TransactionService
    private lateinit var service: FinancialAccountService

    private val accountId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val financialAccountId = UUID.randomUUID()
    private val today = LocalDate.now()

    @BeforeEach
    fun setUp() {
        financialAccountRepository = mock()
        issuerRepository = mock()
        transactionService = mock()
        service =
            FinancialAccountService(
                financialAccountRepository = financialAccountRepository,
                issuerRepository = issuerRepository,
                transactionService = transactionService,
            )
    }

    @Nested
    @DisplayName("create")
    inner class CreateTests {
        private fun command(
            initialBalance: BigDecimal? = null,
            initialBalanceDate: LocalDate? = null,
        ) = CreateFinancialAccountCommand(
            type = FinancialAccountType.BANK,
            subtype = FinancialAccountSubtype.CONTA_CORRENTE,
            name = "Conta principal",
            issuerName = "Banco Teste",
            initialBalance = initialBalance,
            initialBalanceDate = initialBalanceDate,
        )

        private fun savedAccountFrom(input: FinancialAccount) = input.copy(id = financialAccountId)

        private fun transactionResult() =
            Transaction(
                id = UUID.randomUUID(),
                financialAccountId = financialAccountId,
                accountId = accountId,
                createdByUserId = userId,
                creditDebitType = CreditDebitType.CREDIT,
                type = TransactionType.BALANCE_ADJUSTMENT,
                amount = BigDecimal("1.00"),
                date = today,
                description = "Saldo inicial",
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )

        @Test
        fun `creates account without initial balance and does not create balance adjustment transaction`() {
            whenever(
                financialAccountRepository.save(any()),
            ).thenAnswer { savedAccountFrom(it.arguments[0] as FinancialAccount) }

            val result = service.create(command(), accountId, userId)

            assertThat(result.id).isEqualTo(financialAccountId)
            verify(financialAccountRepository).save(any())
            verify(transactionService, never()).create(any(), any(), any())
        }

        @Test
        fun `creates BALANCE_ADJUSTMENT transaction when initial balance and date are provided`() {
            whenever(
                financialAccountRepository.save(any()),
            ).thenAnswer { savedAccountFrom(it.arguments[0] as FinancialAccount) }
            whenever(transactionService.create(any(), eq(accountId), eq(userId))).thenReturn(transactionResult())
            val command = command(initialBalance = BigDecimal("1500.25"), initialBalanceDate = today)

            val result = service.create(command, accountId, userId)

            assertThat(result.id).isEqualTo(financialAccountId)
            val transactionCommandCaptor = argumentCaptor<CreateTransactionCommand>()
            verify(transactionService).create(transactionCommandCaptor.capture(), eq(accountId), eq(userId))
            val txCommand = transactionCommandCaptor.firstValue
            assertThat(txCommand.financialAccountId).isEqualTo(financialAccountId)
            assertThat(txCommand.type).isEqualTo(TransactionType.BALANCE_ADJUSTMENT)
            assertThat(txCommand.creditDebitType).isEqualTo(CreditDebitType.CREDIT)
            assertThat(txCommand.amount).isEqualByComparingTo("1500.25")
            assertThat(txCommand.date).isEqualTo(today)
        }

        @Test
        fun `fails when initial balance is provided without date and does not persist account`() {
            val command = command(initialBalance = BigDecimal("10.00"), initialBalanceDate = null)

            val ex = assertThrows<IllegalArgumentException> { service.create(command, accountId, userId) }

            assertThat(ex.message).contains("initialBalanceDate is required when initialBalance is provided")
            verify(financialAccountRepository, never()).save(any())
            verify(transactionService, never()).create(any(), any(), any())
        }

        @Test
        fun `fails when initial balance is zero and does not persist account`() {
            val command = command(initialBalance = BigDecimal.ZERO, initialBalanceDate = today)

            val ex = assertThrows<IllegalArgumentException> { service.create(command, accountId, userId) }

            assertThat(ex.message).contains("initialBalance must be non-zero when provided")
            verify(financialAccountRepository, never()).save(any())
            verify(transactionService, never()).create(any(), any(), any())
        }

        @Test
        fun `ignores initial balance date when initial balance is not provided`() {
            whenever(
                financialAccountRepository.save(any()),
            ).thenAnswer { savedAccountFrom(it.arguments[0] as FinancialAccount) }
            val command = command(initialBalance = null, initialBalanceDate = today)

            val result = service.create(command, accountId, userId)

            assertThat(result.id).isEqualTo(financialAccountId)
            verify(financialAccountRepository).save(any())
            verify(transactionService, never()).create(any(), any(), any())
        }

        @Test
        fun `fails when initial balance date is older than 5-year moving window`() {
            val tooOldDate = LocalDate.now().minusYears(5).minusDays(1)
            val command = command(initialBalance = BigDecimal("10.00"), initialBalanceDate = tooOldDate)

            assertThrows<ValidationException> {
                service.create(command, accountId, userId)
            }.also { ex ->
                assertThat(ex.errorCode).isEqualTo(BankingErrorCode.TRANSACTION_DATE_OUTSIDE_ALLOWED_WINDOW)
                assertThat(ex.field).isEqualTo("initialBalanceDate")
            }
            verify(financialAccountRepository, never()).save(any())
            verify(transactionService, never()).create(any(), any(), any())
        }
    }
}
