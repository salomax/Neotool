package io.github.salomax.neotool.banking.domain.account

import io.github.salomax.neotool.banking.domain.issuer.Issuer
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.banking.model.dto.account.BankDataPatch
import io.github.salomax.neotool.banking.model.dto.account.CardDataPatch
import io.github.salomax.neotool.banking.model.dto.account.CreateFinancialAccountCommand
import io.github.salomax.neotool.banking.model.dto.account.UpdateField
import io.github.salomax.neotool.banking.model.dto.account.UpdateFinancialAccountCommand
import io.github.salomax.neotool.banking.model.dto.transaction.CreateTransactionCommand
import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.repo.issuer.IssuerRepository
import io.github.salomax.neotool.banking.repo.transaction.CreditCardBillEntityRepository
import io.github.salomax.neotool.banking.repo.transaction.MonthlyBalanceSnapshotEntityRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionEntityRepository
import io.github.salomax.neotool.banking.service.account.FinancialAccountService
import io.github.salomax.neotool.banking.service.transaction.TransactionService
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.NotFoundException
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
    private lateinit var transactionEntityRepository: TransactionEntityRepository
    private lateinit var creditCardBillEntityRepository: CreditCardBillEntityRepository
    private lateinit var monthlyBalanceSnapshotEntityRepository: MonthlyBalanceSnapshotEntityRepository
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
        transactionEntityRepository = mock()
        creditCardBillEntityRepository = mock()
        monthlyBalanceSnapshotEntityRepository = mock()
        service =
            FinancialAccountService(
                financialAccountRepository = financialAccountRepository,
                issuerRepository = issuerRepository,
                transactionService = transactionService,
                transactionEntityRepository = transactionEntityRepository,
                creditCardBillEntityRepository = creditCardBillEntityRepository,
                monthlyBalanceSnapshotEntityRepository = monthlyBalanceSnapshotEntityRepository,
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
        fun `fails when initial balance date is older than 10-year moving window`() {
            val tooOldDate = LocalDate.now().minusYears(10).minusDays(1)
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

        @Test
        fun `fails with validation error code when card masked number is partial on create`() {
            val command =
                CreateFinancialAccountCommand(
                    type = FinancialAccountType.CREDIT,
                    subtype = FinancialAccountSubtype.CREDIT_CARD,
                    issuerName = "Banco Teste",
                    cardData =
                        CardData(
                            maskedNumber = "12",
                        ),
                )

            val ex = assertThrows<ValidationException> { service.create(command, accountId, userId) }
            assertThat(ex.errorCode).isEqualTo(BankingErrorCode.CARD_MASKED_NUMBER_INVALID)
            assertThat(ex.field).isEqualTo("maskedNumber")
            verify(financialAccountRepository, never()).save(any())
            verify(transactionService, never()).create(any(), any(), any())
        }

        @Test
        fun `create multi-card account clears masked number and last four digits`() {
            whenever(
                financialAccountRepository.save(any()),
            ).thenAnswer { savedAccountFrom(it.arguments[0] as FinancialAccount) }

            service.create(
                CreateFinancialAccountCommand(
                    type = FinancialAccountType.CREDIT,
                    subtype = FinancialAccountSubtype.CREDIT_CARD,
                    issuerName = "Banco Teste",
                    cardData =
                        CardData(
                            maskedNumber = "1234",
                            closingDay = 7,
                            dueDay = 15,
                            isMultiCard = true,
                        ),
                ),
                accountId,
                userId,
            )

            val accountCaptor = argumentCaptor<FinancialAccount>()
            verify(financialAccountRepository).save(accountCaptor.capture())
            assertThat(accountCaptor.firstValue.cardLastFourDigits).isNull()
            assertThat(accountCaptor.firstValue.cardData!!.maskedNumber).isNull()
            assertThat(accountCaptor.firstValue.cardData!!.isMultiCard).isTrue()
        }

        @Test
        fun `create single-card account keeps normalized last four digits`() {
            whenever(
                financialAccountRepository.save(any()),
            ).thenAnswer { savedAccountFrom(it.arguments[0] as FinancialAccount) }

            service.create(
                CreateFinancialAccountCommand(
                    type = FinancialAccountType.CREDIT,
                    subtype = FinancialAccountSubtype.CREDIT_CARD,
                    issuerName = "Banco Teste",
                    cardData =
                        CardData(
                            maskedNumber = "  9876 ",
                            closingDay = 7,
                            dueDay = 15,
                            isMultiCard = false,
                        ),
                ),
                accountId,
                userId,
            )

            val accountCaptor = argumentCaptor<FinancialAccount>()
            verify(financialAccountRepository).save(accountCaptor.capture())
            assertThat(accountCaptor.firstValue.cardLastFourDigits).isEqualTo("9876")
            assertThat(accountCaptor.firstValue.cardData!!.maskedNumber).isEqualTo("9876")
            assertThat(accountCaptor.firstValue.cardData!!.isMultiCard).isFalse()
        }

        @Test
        fun `fails when name exceeds 255 characters on create`() {
            val longName = "A".repeat(256)
            val command = command().copy(name = longName)

            val ex = assertThrows<ValidationException> { service.create(command, accountId, userId) }

            assertThat(ex.errorCode).isEqualTo(BankingErrorCode.FINANCIAL_ACCOUNT_NAME_TOO_LONG)
            assertThat(ex.field).isEqualTo("name")
            verify(financialAccountRepository, never()).save(any())
            verify(transactionService, never()).create(any(), any(), any())
        }

        @Test
        fun `create voucher card defaults includeInNetWorth to false`() {
            whenever(
                financialAccountRepository.save(any()),
            ).thenAnswer { savedAccountFrom(it.arguments[0] as FinancialAccount) }

            service.create(
                CreateFinancialAccountCommand(
                    type = FinancialAccountType.PAYMENT,
                    subtype = FinancialAccountSubtype.VOUCHER_CARD,
                    name = "Vale alimentacao",
                    issuerName = "Alelo",
                ),
                accountId,
                userId,
            )

            val accountCaptor = argumentCaptor<FinancialAccount>()
            verify(financialAccountRepository).save(accountCaptor.capture())
            assertThat(accountCaptor.firstValue.includeInNetWorth).isFalse()
        }

        @Test
        fun `create voucher card allows overriding includeInNetWorth to true`() {
            whenever(
                financialAccountRepository.save(any()),
            ).thenAnswer { savedAccountFrom(it.arguments[0] as FinancialAccount) }

            service.create(
                CreateFinancialAccountCommand(
                    type = FinancialAccountType.PAYMENT,
                    subtype = FinancialAccountSubtype.VOUCHER_CARD,
                    name = "Vale alimentacao",
                    issuerName = "Alelo",
                    includeInNetWorth = true,
                ),
                accountId,
                userId,
            )

            val accountCaptor = argumentCaptor<FinancialAccount>()
            verify(financialAccountRepository).save(accountCaptor.capture())
            assertThat(accountCaptor.firstValue.includeInNetWorth).isTrue()
        }

        @Test
        fun `create credit card keeps includeInNetWorth default true`() {
            whenever(
                financialAccountRepository.save(any()),
            ).thenAnswer { savedAccountFrom(it.arguments[0] as FinancialAccount) }

            service.create(
                CreateFinancialAccountCommand(
                    type = FinancialAccountType.CREDIT,
                    subtype = FinancialAccountSubtype.CREDIT_CARD,
                    name = "Cartao",
                    issuerName = "Banco Teste",
                ),
                accountId,
                userId,
            )

            val accountCaptor = argumentCaptor<FinancialAccount>()
            verify(financialAccountRepository).save(accountCaptor.capture())
            assertThat(accountCaptor.firstValue.includeInNetWorth).isTrue()
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
        fun `fails when resolved name exceeds 255 characters on update`() {
            val existing = existingBankAccount()
            whenever(financialAccountRepository.findById(financialAccountId, accountId)).thenReturn(existing)

            val longName = "A".repeat(256)
            assertThrows<ValidationException> {
                service.update(
                    financialAccountId = financialAccountId,
                    command =
                        UpdateFinancialAccountCommand(
                            type = FinancialAccountType.BANK,
                            subtype = FinancialAccountSubtype.CONTA_CORRENTE,
                            name = UpdateField.Present(longName),
                            issuerId = issuerId,
                        ),
                    accountId = accountId,
                    userId = userId,
                )
            }.also { ex ->
                assertThat(ex.errorCode).isEqualTo(BankingErrorCode.FINANCIAL_ACCOUNT_NAME_TOO_LONG)
                assertThat(ex.field).isEqualTo("name")
            }
            verify(financialAccountRepository, never()).update(any())
        }

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
                            name = UpdateField.Present("Cartao editado"),
                            issuerId = issuerId,
                            cardData =
                                CardDataPatch(
                                    maskedNumber = UpdateField.Present("9876"),
                                    cardNetwork = UpdateField.Present(CreditCardNetwork.VISA),
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
                                BankDataPatch(
                                    branchCode = UpdateField.Present("9999"),
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
            val existing =
                existingCardAccount().copy(
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

        @Test
        fun `clears linked bank account and last four digits when masked number is removed`() {
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
                            issuerId = issuerId,
                            cardData =
                                CardDataPatch(
                                    maskedNumber = UpdateField.Present(null),
                                    linkedBankAccountId = UpdateField.Present(null),
                                ),
                        ),
                    accountId = accountId,
                    userId = userId,
                )

            assertThat(result.cardData!!.maskedNumber).isNull()
            assertThat(result.cardData!!.linkedBankAccountId).isNull()
            assertThat(result.cardLastFourDigits).isNull()
        }

        @Test
        fun `rejects billing cycle day outside supported range`() {
            val existing = existingCardAccount()
            whenever(financialAccountRepository.findById(financialAccountId, accountId)).thenReturn(existing)

            val ex =
                assertThrows<IllegalArgumentException> {
                    service.update(
                        financialAccountId = financialAccountId,
                        command =
                            UpdateFinancialAccountCommand(
                                type = FinancialAccountType.CREDIT,
                                subtype = FinancialAccountSubtype.CHARGE_CARD,
                                issuerId = issuerId,
                                cardData =
                                    CardDataPatch(
                                        closingDay = UpdateField.Present(32),
                                    ),
                            ),
                        accountId = accountId,
                        userId = userId,
                    )
                }

            assertThat(ex.message).contains("closingDay must be between 1 and 31")
            verify(financialAccountRepository, never()).update(any())
        }

        @Test
        fun `fails with validation error code when card masked number is partial on update`() {
            val existing = existingCardAccount()
            whenever(financialAccountRepository.findById(financialAccountId, accountId)).thenReturn(existing)

            val ex =
                assertThrows<ValidationException> {
                    service.update(
                        financialAccountId = financialAccountId,
                        command =
                            UpdateFinancialAccountCommand(
                                type = FinancialAccountType.CREDIT,
                                subtype = FinancialAccountSubtype.CHARGE_CARD,
                                issuerId = issuerId,
                                cardData =
                                    CardDataPatch(
                                        maskedNumber = UpdateField.Present("123"),
                                    ),
                            ),
                        accountId = accountId,
                        userId = userId,
                    )
                }

            assertThat(ex.errorCode).isEqualTo(BankingErrorCode.CARD_MASKED_NUMBER_INVALID)
            assertThat(ex.field).isEqualTo("maskedNumber")
            verify(financialAccountRepository, never()).update(any())
        }

        @Test
        fun `update enables multi-card and clears masked number and last four digits`() {
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
                            issuerId = issuerId,
                            cardData =
                                CardDataPatch(
                                    isMultiCard = UpdateField.Present(true),
                                ),
                        ),
                    accountId = accountId,
                    userId = userId,
                )

            assertThat(result.cardData!!.isMultiCard).isTrue()
            assertThat(result.cardData!!.maskedNumber).isNull()
            assertThat(result.cardLastFourDigits).isNull()
        }

        @Test
        fun `update disables multi-card and accepts a new masked number`() {
            val base = existingCardAccount()
            val existing =
                base.copy(
                    cardLastFourDigits = null,
                    cardData =
                        base.cardData!!.copy(
                            maskedNumber = null,
                            isMultiCard = true,
                        ),
                )
            whenever(financialAccountRepository.findById(financialAccountId, accountId)).thenReturn(existing)
            whenever(financialAccountRepository.update(any())).thenAnswer { it.arguments[0] as FinancialAccount }

            val result =
                service.update(
                    financialAccountId = financialAccountId,
                    command =
                        UpdateFinancialAccountCommand(
                            type = FinancialAccountType.CREDIT,
                            subtype = FinancialAccountSubtype.CHARGE_CARD,
                            issuerId = issuerId,
                            cardData =
                                CardDataPatch(
                                    isMultiCard = UpdateField.Present(false),
                                    maskedNumber = UpdateField.Present("3344"),
                                ),
                        ),
                    accountId = accountId,
                    userId = userId,
                )

            assertThat(result.cardData!!.isMultiCard).isFalse()
            assertThat(result.cardData!!.maskedNumber).isEqualTo("3344")
            assertThat(result.cardLastFourDigits).isEqualTo("3344")
        }
    }

    @Nested
    @DisplayName("delete")
    inner class DeleteTests {
        private fun existingAccount() =
            FinancialAccount(
                id = financialAccountId,
                accountId = accountId,
                createdByUserId = userId,
                type = FinancialAccountType.BANK,
                subtype = FinancialAccountSubtype.CONTA_CORRENTE,
                name = "Conta para exclusao",
                currencyCode = "BRL",
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )

        @Test
        fun `delete account with no transactions and no bills soft-deletes account and cleans up children`() {
            whenever(financialAccountRepository.findById(financialAccountId, accountId)).thenReturn(existingAccount())
            whenever(
                transactionEntityRepository.countByFinancialAccountIdAndDeletedAtIsNull(financialAccountId),
            ).thenReturn(0)
            whenever(creditCardBillEntityRepository.countOpenByFinancialAccountId(financialAccountId)).thenReturn(0)
            whenever(financialAccountRepository.softDelete(financialAccountId, accountId)).thenReturn(true)

            val result = service.delete(financialAccountId, accountId)

            assertThat(result).isTrue
            verify(monthlyBalanceSnapshotEntityRepository).deleteByFinancialAccountId(financialAccountId)
            verify(creditCardBillEntityRepository).softDeleteByFinancialAccountId(eq(financialAccountId), any())
            verify(financialAccountRepository).softDelete(financialAccountId, accountId)
        }

        @Test
        fun `delete account with active transactions throws validation exception`() {
            whenever(financialAccountRepository.findById(financialAccountId, accountId)).thenReturn(existingAccount())
            whenever(
                transactionEntityRepository.countByFinancialAccountIdAndDeletedAtIsNull(financialAccountId),
            ).thenReturn(2)

            val ex = assertThrows<ValidationException> { service.delete(financialAccountId, accountId) }

            assertThat(ex.errorCode).isEqualTo(BankingErrorCode.FINANCIAL_ACCOUNT_HAS_TRANSACTIONS)
            assertThat(ex.field).isEqualTo("id")
            verify(monthlyBalanceSnapshotEntityRepository, never()).deleteByFinancialAccountId(any())
            verify(creditCardBillEntityRepository, never()).softDeleteByFinancialAccountId(any(), any())
            verify(financialAccountRepository, never()).softDelete(any(), any())
        }

        @Test
        fun `delete account with open credit card bills throws validation exception`() {
            whenever(financialAccountRepository.findById(financialAccountId, accountId)).thenReturn(existingAccount())
            whenever(
                transactionEntityRepository.countByFinancialAccountIdAndDeletedAtIsNull(financialAccountId),
            ).thenReturn(0)
            whenever(creditCardBillEntityRepository.countOpenByFinancialAccountId(financialAccountId)).thenReturn(1)

            val ex = assertThrows<ValidationException> { service.delete(financialAccountId, accountId) }

            assertThat(ex.errorCode).isEqualTo(BankingErrorCode.FINANCIAL_ACCOUNT_HAS_OPEN_BILLS)
            assertThat(ex.field).isEqualTo("id")
            verify(monthlyBalanceSnapshotEntityRepository, never()).deleteByFinancialAccountId(any())
            verify(creditCardBillEntityRepository, never()).softDeleteByFinancialAccountId(any(), any())
            verify(financialAccountRepository, never()).softDelete(any(), any())
        }

        @Test
        fun `delete account belonging to different tenant throws not found exception`() {
            whenever(financialAccountRepository.findById(financialAccountId, accountId)).thenReturn(null)

            val ex = assertThrows<NotFoundException> { service.delete(financialAccountId, accountId) }

            assertThat(ex.errorCode).isEqualTo(CommonErrorCode.NOT_FOUND)
            assertThat(ex.field).isEqualTo("id")
            verify(financialAccountRepository, never()).softDelete(any(), any())
        }

        @Test
        fun `delete already-deleted account throws not found exception`() {
            whenever(financialAccountRepository.findById(financialAccountId, accountId)).thenReturn(null)

            val ex = assertThrows<NotFoundException> { service.delete(financialAccountId, accountId) }

            assertThat(ex.errorCode).isEqualTo(CommonErrorCode.NOT_FOUND)
            assertThat(ex.field).isEqualTo("id")
            verify(financialAccountRepository, never()).softDelete(any(), any())
        }
    }
}
