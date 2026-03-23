package io.github.salomax.neotool.banking.domain.account

import io.github.salomax.neotool.banking.domain.issuer.IssuerRepository
import io.github.salomax.neotool.banking.domain.transaction.CreateTransactionCommand
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionService
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.banking.domain.issuer.Issuer
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

    @Nested
    @DisplayName("update")
    inner class UpdateTests {
        private val issuerId = UUID.randomUUID()
        private val linkedBankAccountId = UUID.randomUUID()

        private fun existingBankAccount() =
            FinancialAccount(
                id = financialAccountId,
                accountId = accountId,
                createdByUserId = userId,
                type = FinancialAccountType.BANK,
                subtype = FinancialAccountSubtype.CONTA_CORRENTE,
                name = "Conta atual",
                currencyCode = "BRL",
                issuerId = issuerId,
                issuerName = "Banco Atual",
                bankData =
                    BankData(
                        branchCode = "0001",
                        number = "12345",
                        checkDigit = "6",
                        maskedNumber = "****12345-6",
                    ),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )

        private fun existingCardAccount() =
            FinancialAccount(
                id = financialAccountId,
                accountId = accountId,
                createdByUserId = userId,
                type = FinancialAccountType.CREDIT,
                subtype = FinancialAccountSubtype.CHARGE_CARD,
                name = "Cartao atual",
                currencyCode = "BRL",
                issuerId = issuerId,
                issuerName = "Banco Atual",
                issuerCountry = "BR",
                issuerCompeCode = "001",
                issuerIspb = "12345678",
                cardLastFourDigits = "1234",
                cardData =
                    CardData(
                        maskedNumber = "1234",
                        bin = "555544",
                        cardNetwork = CreditCardNetwork.MASTERCARD,
                        cardLevel = CreditCardLevel.GOLD,
                        creditLimit = BigDecimal("3000.00"),
                        availableCreditLimit = BigDecimal("1500.00"),
                        minimumPaymentPercentage = BigDecimal("15.00"),
                        closingDay = 7,
                        dueDay = 15,
                        linkedBankAccountId = linkedBankAccountId,
                    ),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )

        @Test
        fun `preserves nested card data omitted by update input`() {
            val existing = existingCardAccount()
            whenever(financialAccountRepository.findById(financialAccountId, accountId)).thenReturn(existing)
            whenever(financialAccountRepository.update(any())).thenAnswer { it.arguments[0] as FinancialAccount }

            val result =
                service.update(
                    financialAccountId = financialAccountId,
                    command =
                        UpdateFinancialAccountCommand(
                            type = FinancialAccountType.CREDIT,
                            subtype = FinancialAccountSubtype.CHARGE_CARD,
                            name = "Cartao editado",
                            issuerId = issuerId,
                            cardData =
                                CardData(
                                    maskedNumber = "9876",
                                    cardNetwork = CreditCardNetwork.VISA,
                                ),
                        ),
                    accountId = accountId,
                    userId = userId,
                )

            assertThat(result.name).isEqualTo("Cartao editado")
            assertThat(result.cardLastFourDigits).isEqualTo("9876")
            assertThat(result.cardData!!.maskedNumber).isEqualTo("9876")
            assertThat(result.cardData!!.cardNetwork).isEqualTo(CreditCardNetwork.VISA)
            assertThat(result.cardData!!.bin).isEqualTo("555544")
            assertThat(result.cardData!!.availableCreditLimit).isEqualByComparingTo("1500.00")
            assertThat(result.cardData!!.minimumPaymentPercentage).isEqualByComparingTo("15.00")
            assertThat(result.cardData!!.closingDay).isEqualTo(7)
            assertThat(result.cardData!!.dueDay).isEqualTo(15)
            assertThat(result.cardData!!.linkedBankAccountId).isEqualTo(linkedBankAccountId)
        }

        @Test
        fun `preserves nested bank data omitted by update input`() {
            val existing = existingBankAccount()
            whenever(financialAccountRepository.findById(financialAccountId, accountId)).thenReturn(existing)
            whenever(financialAccountRepository.update(any())).thenAnswer { it.arguments[0] as FinancialAccount }

            val result =
                service.update(
                    financialAccountId = financialAccountId,
                    command =
                        UpdateFinancialAccountCommand(
                            type = FinancialAccountType.BANK,
                            subtype = FinancialAccountSubtype.CONTA_CORRENTE,
                            issuerId = issuerId,
                            bankData =
                                BankData(
                                    branchCode = "9999",
                                ),
                        ),
                    accountId = accountId,
                    userId = userId,
                )

            assertThat(result.bankData!!.branchCode).isEqualTo("9999")
            assertThat(result.bankData!!.number).isEqualTo("12345")
            assertThat(result.bankData!!.checkDigit).isEqualTo("6")
            assertThat(result.bankData!!.maskedNumber).isEqualTo("****12345-6")
        }

        @Test
        fun `switches from issuer reference to custom issuer without retaining issuer metadata`() {
            val existing = existingCardAccount()
            whenever(financialAccountRepository.findById(financialAccountId, accountId)).thenReturn(existing)
            whenever(financialAccountRepository.update(any())).thenAnswer { it.arguments[0] as FinancialAccount }

            val result =
                service.update(
                    financialAccountId = financialAccountId,
                    command =
                        UpdateFinancialAccountCommand(
                            type = FinancialAccountType.CREDIT,
                            subtype = FinancialAccountSubtype.CHARGE_CARD,
                            issuerName = "Issuer Manual",
                        ),
                    accountId = accountId,
                    userId = userId,
                )

            assertThat(result.issuerId).isNull()
            assertThat(result.issuerName).isEqualTo("Issuer Manual")
            assertThat(result.issuerCountry).isNull()
            assertThat(result.issuerCompeCode).isNull()
            assertThat(result.issuerIspb).isNull()
        }

        @Test
        fun `switches from custom issuer to referenced issuer`() {
            val existing = existingCardAccount().copy(
                issuerId = null,
                issuerName = "Issuer Manual",
                issuerCountry = null,
                issuerCompeCode = null,
                issuerIspb = null,
            )
            whenever(financialAccountRepository.findById(financialAccountId, accountId)).thenReturn(existing)
            whenever(financialAccountRepository.update(any())).thenAnswer { it.arguments[0] as FinancialAccount }
            whenever(issuerRepository.findById(issuerId)).thenReturn(
                Issuer(
                    id = issuerId,
                    name = "Issuer Catalog",
                    domain = null,
                    compeCode = "341",
                    ispb = "60701190",
                    logoKey = null,
                    countryCode = "BR",
                ),
            )

            val result =
                service.update(
                    financialAccountId = financialAccountId,
                    command =
                        UpdateFinancialAccountCommand(
                            type = FinancialAccountType.CREDIT,
                            subtype = FinancialAccountSubtype.CHARGE_CARD,
                            issuerId = issuerId,
                        ),
                    accountId = accountId,
                    userId = userId,
                )

            assertThat(result.issuerId).isEqualTo(issuerId)
            assertThat(result.issuerName).isEqualTo("Issuer Catalog")
            assertThat(result.issuerCountry).isEqualTo("BR")
            assertThat(result.issuerCompeCode).isEqualTo("341")
            assertThat(result.issuerIspb).isEqualTo("60701190")
        }
    }
}
