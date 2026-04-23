package io.github.salomax.neotool.banking.domain.transaction

import io.github.salomax.neotool.banking.service.transaction.*
import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.service.account.FinancialAccountGuard
import io.github.salomax.neotool.banking.repo.transaction.TransactionCategoryRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionListQuery
import io.github.salomax.neotool.banking.repo.transaction.TransactionPageResult
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.domain.preferences.AccountPreferences
import io.github.salomax.neotool.banking.domain.preferences.RuleApplicationMode
import io.github.salomax.neotool.banking.client.transaction.security.AccountMembershipValidationPort
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.banking.service.preferences.AccountPreferencesService
import io.github.salomax.neotool.banking.service.preferences.UserRuleApplicationModeService
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
import org.mockito.kotlin.anyOrNull
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

@DisplayName("TransactionService")
class TransactionServiceTest {
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var categoryRepository: TransactionCategoryRepository
    private lateinit var transactionRuleEngine: TransactionRuleEngine
    private lateinit var merchantService: MerchantService
    private lateinit var financialAccountRepository: FinancialAccountRepository
    private lateinit var financialAccountGuard: FinancialAccountGuard
    private lateinit var accountMembershipValidationPort: AccountMembershipValidationPort
    private lateinit var snapshotComputationService: SnapshotComputationService
    private lateinit var creditCardBillService: CreditCardBillService
    private lateinit var accountPreferencesService: AccountPreferencesService
    private lateinit var userRuleApplicationModeService: UserRuleApplicationModeService
    private lateinit var service: TransactionService

    private val accountId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val financialAccountId = UUID.randomUUID()
    private val now = Instant.now()
    private val today = LocalDate.now()

    private val ownedAccount =
        FinancialAccount(
            id = financialAccountId,
            accountId = accountId,
            createdByUserId = userId,
            type = FinancialAccountType.BANK,
            subtype = FinancialAccountSubtype.CONTA_CORRENTE,
            name = "Checking",
            currencyCode = "BRL",
            createdAt = now,
            updatedAt = now,
        )

    @BeforeEach
    fun setUp() {
        transactionRepository = mock()
        categoryRepository = mock()
        transactionRuleEngine = mock()
        merchantService = mock()
        financialAccountRepository = mock()
        financialAccountGuard = mock()
        accountMembershipValidationPort = mock()
        snapshotComputationService = mock()
        creditCardBillService = mock()
        accountPreferencesService = mock()
        userRuleApplicationModeService = mock()
        whenever(accountPreferencesService.get(accountId)).thenReturn(AccountPreferences.defaultsFor(accountId, now))
        whenever(userRuleApplicationModeService.get(userId)).thenReturn(RuleApplicationMode.ALWAYS_PROMPT)
        service =
            TransactionService(
                transactionRepository = transactionRepository,
                categoryRepository = categoryRepository,
                transactionRuleEngine = transactionRuleEngine,
                merchantService = merchantService,
                financialAccountRepository = financialAccountRepository,
                financialAccountGuard = financialAccountGuard,
                accountMembershipValidationPort = accountMembershipValidationPort,
                snapshotComputationService = snapshotComputationService,
                creditCardBillService = creditCardBillService,
                accountPreferencesService = accountPreferencesService,
                userRuleApplicationModeService = userRuleApplicationModeService,
            )
    }

    @Nested
    @DisplayName("create")
    inner class CreateTests {
        @Test
        fun `creates POSTED transaction with all required fields and returns persisted domain object`() {
            whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(listOf(ownedAccount))
            val command =
                CreateTransactionCommand(
                    financialAccountId = financialAccountId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("50.00"),
                    date = today,
                    description = "Coffee",
                )
            val savedWithId =
                Transaction(
                    id = UUID.randomUUID(),
                    financialAccountId = command.financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = command.creditDebitType,
                    type = command.type,
                    status = TransactionStatus.POSTED,
                    source = TransactionSource.MANUAL,
                    amount = command.amount,
                    currencyCode = "BRL",
                    date = command.date,
                    description = command.description,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(transactionRepository.save(any())).thenReturn(savedWithId)

            val result = service.create(command, accountId, userId)

            assertThat(result.id).isEqualTo(savedWithId.id)
            assertThat(result.financialAccountId).isEqualTo(financialAccountId)
            assertThat(result.status).isEqualTo(TransactionStatus.POSTED)
            assertThat(result.source).isEqualTo(TransactionSource.MANUAL)
        }

        @Test
        fun `creates FUTURE transaction with status FUTURE`() {
            whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(listOf(ownedAccount))
            val command =
                CreateTransactionCommand(
                    financialAccountId = financialAccountId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("25.00"),
                    date = today.plusDays(7),
                    description = "Scheduled",
                    status = TransactionStatus.FUTURE,
                )
            whenever(
                transactionRepository.save(any()),
            ).thenAnswer { (it.arguments[0] as Transaction).copy(id = UUID.randomUUID()) }

            val result = service.create(command, accountId, userId)

            assertThat(result.status).isEqualTo(TransactionStatus.FUTURE)
        }

        @Test
        fun `manual create applies rule engine suggestion`() {
            whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(listOf(ownedAccount))
            whenever(userRuleApplicationModeService.get(userId)).thenReturn(RuleApplicationMode.ALWAYS)
            val suggestedRuleId = UUID.randomUUID()
            val suggestedCategoryId = UUID.randomUUID()
            whenever(transactionRuleEngine.suggest(accountId, "Coffee", CreditDebitType.DEBIT)).thenReturn(
                RuleSuggestion(
                    ruleId = suggestedRuleId,
                    categoryId = suggestedCategoryId,
                    merchantId = null,
                    appliedRuleIds = listOf(suggestedRuleId),
                ),
            )
            val saveCaptor = argumentCaptor<Transaction>()
            whenever(transactionRepository.save(saveCaptor.capture())).thenAnswer {
                saveCaptor.firstValue.copy(id = UUID.randomUUID())
            }

            service.create(
                CreateTransactionCommand(
                    financialAccountId = financialAccountId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("50.00"),
                    date = today,
                    description = "Coffee",
                ),
                accountId,
                userId,
            )

            assertThat(saveCaptor.firstValue.categoryId).isEqualTo(suggestedCategoryId)
            assertThat(saveCaptor.firstValue.categorySource).isEqualTo(CategorySource.RULE)
            assertThat(saveCaptor.firstValue.appliedRuleIds).containsExactly(suggestedRuleId)
            assertThat(saveCaptor.firstValue.appliedRuleHistory).hasSize(1)
            assertThat(saveCaptor.firstValue.appliedRuleHistory.map { it.ruleId }).containsExactly(suggestedRuleId)
            verify(transactionRuleEngine).suggest(accountId, "Coffee", CreditDebitType.DEBIT)
        }

        @Test
        fun `creates BALANCE_ADJUSTMENT with CREDIT direction and succeeds`() {
            whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(listOf(ownedAccount))
            val command =
                CreateTransactionCommand(
                    financialAccountId = financialAccountId,
                    creditDebitType = CreditDebitType.CREDIT,
                    type = TransactionType.BALANCE_ADJUSTMENT,
                    amount = BigDecimal("1000.00"),
                    date = today,
                    description = "Opening balance",
                )
            whenever(
                transactionRepository.save(any()),
            ).thenAnswer { (it.arguments[0] as Transaction).copy(id = UUID.randomUUID()) }

            val result = service.create(command, accountId, userId)

            assertThat(result.type).isEqualTo(TransactionType.BALANCE_ADJUSTMENT)
            assertThat(result.creditDebitType).isEqualTo(CreditDebitType.CREDIT)
        }

        @Test
        fun `fails when financialAccountId does not belong to caller accountId`() {
            whenever(financialAccountGuard.requireOwnership(financialAccountId, accountId))
                .thenThrow(IllegalArgumentException("Financial account $financialAccountId does not belong to account $accountId"))
            val command =
                CreateTransactionCommand(
                    financialAccountId = financialAccountId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("10.00"),
                    date = today,
                    description = "X",
                )

            val ex =
                assertThrows<IllegalArgumentException> {
                    service.create(command, accountId, userId)
                }
            assertThat(ex.message).contains(financialAccountId.toString()).contains(accountId.toString())
        }

        @Test
        fun `fails when amount is zero or negative`() {
            whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(listOf(ownedAccount))
            val command =
                CreateTransactionCommand(
                    financialAccountId = financialAccountId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal.ZERO,
                    date = today,
                    description = "X",
                )

            assertThrows<IllegalArgumentException> { service.create(command, accountId, userId) }
        }

        @Test
        fun `fails when create date is older than 10-year moving window`() {
            whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(listOf(ownedAccount))
            val tooOldDate = LocalDate.now().minusYears(10).minusDays(1)
            val command =
                CreateTransactionCommand(
                    financialAccountId = financialAccountId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("10.00"),
                    date = tooOldDate,
                    description = "Old tx",
                )

            assertThrows<ValidationException> {
                service.create(command, accountId, userId)
            }.also { ex ->
                assertThat(ex.errorCode).isEqualTo(BankingErrorCode.TRANSACTION_DATE_OUTSIDE_ALLOWED_WINDOW)
                assertThat(ex.field).isEqualTo("date")
            }
            verify(transactionRepository, never()).save(any())
        }

        @Test
        fun `fails when only installmentNumber is set without totalInstallments`() {
            whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(listOf(ownedAccount))
            val command =
                CreateTransactionCommand(
                    financialAccountId = financialAccountId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.CARD,
                    amount = BigDecimal("100.00"),
                    date = today,
                    description = "Card",
                    installmentNumber = 1,
                    totalInstallments = null,
                )

            val ex = assertThrows<IllegalArgumentException> { service.create(command, accountId, userId) }
            assertThat(ex.message).contains("totalInstallments")
        }

        @Test
        fun `fails when only totalInstallments is set without installmentNumber`() {
            whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(listOf(ownedAccount))
            val command =
                CreateTransactionCommand(
                    financialAccountId = financialAccountId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.CARD,
                    amount = BigDecimal("100.00"),
                    date = today,
                    description = "Card",
                    installmentNumber = null,
                    totalInstallments = 3,
                )

            val ex = assertThrows<IllegalArgumentException> { service.create(command, accountId, userId) }
            assertThat(ex.message).contains("installmentNumber")
        }

        @Test
        fun `fails when installmentNumber greater than totalInstallments`() {
            whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(listOf(ownedAccount))
            val command =
                CreateTransactionCommand(
                    financialAccountId = financialAccountId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.CARD,
                    amount = BigDecimal("100.00"),
                    date = today,
                    description = "Card",
                    installmentNumber = 4,
                    totalInstallments = 3,
                )

            assertThrows<IllegalArgumentException> { service.create(command, accountId, userId) }
        }

        @Test
        fun `correctly propagates source MANUAL regardless of input`() {
            whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(listOf(ownedAccount))
            val command =
                CreateTransactionCommand(
                    financialAccountId = financialAccountId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("1.00"),
                    date = today,
                    description = "X",
                )
            val saveCaptor = argumentCaptor<Transaction>()
            whenever(transactionRepository.save(saveCaptor.capture())).thenAnswer {
                saveCaptor.firstValue.copy(id = UUID.randomUUID())
            }

            service.create(command, accountId, userId)

            assertThat(saveCaptor.firstValue.source).isEqualTo(TransactionSource.MANUAL)
        }

        @Test
        fun `defaults belongsToUserId to authenticated user when attribution is not provided`() {
            whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(listOf(ownedAccount))
            val saveCaptor = argumentCaptor<Transaction>()
            whenever(transactionRepository.save(saveCaptor.capture())).thenAnswer {
                saveCaptor.firstValue.copy(id = UUID.randomUUID())
            }

            service.create(
                CreateTransactionCommand(
                    financialAccountId = financialAccountId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("30.00"),
                    date = today,
                    description = "Dinner",
                ),
                accountId,
                userId,
            )

            assertThat(saveCaptor.firstValue.belongsToUserId).isEqualTo(userId)
            assertThat(saveCaptor.firstValue.belongsToName).isNull()
        }

        @Test
        fun `allows belongsToName attribution on create`() {
            whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(listOf(ownedAccount))
            val saveCaptor = argumentCaptor<Transaction>()
            whenever(transactionRepository.save(saveCaptor.capture())).thenAnswer {
                saveCaptor.firstValue.copy(id = UUID.randomUUID())
            }

            service.create(
                CreateTransactionCommand(
                    financialAccountId = financialAccountId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("42.00"),
                    date = today,
                    description = "Groceries",
                    belongsToName = "Family",
                ),
                accountId,
                userId,
            )

            assertThat(saveCaptor.firstValue.belongsToUserId).isNull()
            assertThat(saveCaptor.firstValue.belongsToName).isEqualTo("Family")
        }

        @Test
        fun `fails when both belongsToUserId and belongsToName are provided on create`() {
            whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(listOf(ownedAccount))

            val ex =
                assertThrows<IllegalArgumentException> {
                    service.create(
                        CreateTransactionCommand(
                            financialAccountId = financialAccountId,
                            creditDebitType = CreditDebitType.DEBIT,
                            type = TransactionType.OTHER_EXPENSE,
                            amount = BigDecimal("10.00"),
                            date = today,
                            description = "X",
                            belongsToUserId = UUID.randomUUID(),
                            belongsToName = "Someone",
                        ),
                        accountId,
                        userId,
                    )
                }
            assertThat(ex.message).contains("Only one of belongsToUserId or belongsToName")
        }

        @Test
        fun `accepts belongsToUserId when user is an active member of account`() {
            whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(listOf(ownedAccount))
            val belongsToUserId = UUID.randomUUID()
            whenever(accountMembershipValidationPort.isUserActiveMemberOfAccount(belongsToUserId, accountId))
                .thenReturn(true)
            val saveCaptor = argumentCaptor<Transaction>()
            whenever(transactionRepository.save(saveCaptor.capture())).thenAnswer {
                saveCaptor.firstValue.copy(id = UUID.randomUUID())
            }

            service.create(
                CreateTransactionCommand(
                    financialAccountId = financialAccountId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("10.00"),
                    date = today,
                    description = "Movie",
                    belongsToUserId = belongsToUserId,
                ),
                accountId,
                userId,
            )

            verify(accountMembershipValidationPort).isUserActiveMemberOfAccount(belongsToUserId, accountId)
            assertThat(saveCaptor.firstValue.belongsToUserId).isEqualTo(belongsToUserId)
            assertThat(saveCaptor.firstValue.belongsToName).isNull()
        }

        @Test
        fun `rejects belongsToUserId when user is not an active member of account`() {
            whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(listOf(ownedAccount))
            val belongsToUserId = UUID.randomUUID()
            whenever(accountMembershipValidationPort.isUserActiveMemberOfAccount(belongsToUserId, accountId))
                .thenReturn(false)

            assertThrows<ValidationException> {
                service.create(
                    CreateTransactionCommand(
                        financialAccountId = financialAccountId,
                        creditDebitType = CreditDebitType.DEBIT,
                        type = TransactionType.OTHER_EXPENSE,
                        amount = BigDecimal("10.00"),
                        date = today,
                        description = "Movie",
                        belongsToUserId = belongsToUserId,
                    ),
                    accountId,
                    userId,
                )
            }.also { ex ->
                assertThat(ex.errorCode).isEqualTo(CommonErrorCode.INVALID_INPUT)
                assertThat(ex.field).isEqualTo("belongsToUserId")
            }
            verify(accountMembershipValidationPort).isUserActiveMemberOfAccount(belongsToUserId, accountId)
            verify(transactionRepository, never()).save(any())
        }

        @Test
        fun `normalizes duplicate tags on create`() {
            whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(listOf(ownedAccount))
            val saveCaptor = argumentCaptor<Transaction>()
            whenever(transactionRepository.save(saveCaptor.capture())).thenAnswer {
                saveCaptor.firstValue.copy(id = UUID.randomUUID())
            }

            service.create(
                CreateTransactionCommand(
                    financialAccountId = financialAccountId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("10.00"),
                    date = today,
                    description = "X",
                    tags = listOf(" Food ", "Food", "Weekend"),
                ),
                accountId,
                userId,
            )

            assertThat(saveCaptor.firstValue.tags).containsExactly("Food", "Weekend")
        }
    }

    @Nested
    @DisplayName("update")
    inner class UpdateTests {
        @Test
        fun `updates allowed fields and returns updated domain`() {
            val txId = UUID.randomUUID()
            val existing =
                Transaction(
                    id = txId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    status = TransactionStatus.POSTED,
                    amount = BigDecimal("10.00"),
                    date = today,
                    description = "Old",
                    notes = "Old notes",
                    categoryId = null,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(transactionRepository.findById(txId, accountId)).thenReturn(existing)
            val updated =
                existing.copy(
                    description = "New desc",
                    amount = BigDecimal("20.00"),
                    date = today.plusDays(1),
                    notes = "New notes",
                    categoryId = UUID.randomUUID(),
                    updatedAt = now.plusSeconds(60),
                )
            whenever(transactionRepository.update(any())).thenReturn(updated)

            val command =
                UpdateTransactionCommand(
                    description = "New desc",
                    amount = BigDecimal("20.00"),
                    date = today.plusDays(1),
                    notes = "New notes",
                    categoryId = updated.categoryId,
                )
            val result = service.update(txId, command, accountId, userId)

            assertThat(result.description).isEqualTo("New desc")
            assertThat(result.amount).isEqualByComparingTo("20.00")
            assertThat(result.date).isEqualTo(today.plusDays(1))
            assertThat(result.notes).isEqualTo("New notes")
            assertThat(result.categoryId).isEqualTo(updated.categoryId)
        }

        @Test
        fun `partial update only changes fields present in command, others remain unchanged`() {
            val txId = UUID.randomUUID()
            val categoryId = UUID.randomUUID()
            val existing =
                Transaction(
                    id = txId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    status = TransactionStatus.POSTED,
                    amount = BigDecimal("15.00"),
                    date = today,
                    description = "Original",
                    notes = "Original notes",
                    categoryId = categoryId,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(transactionRepository.findById(txId, accountId)).thenReturn(existing)
            val updateCaptor = argumentCaptor<Transaction>()
            whenever(transactionRepository.update(updateCaptor.capture())).thenAnswer { updateCaptor.firstValue }

            service.update(txId, UpdateTransactionCommand(description = "Only description"), accountId, userId)

            val passed = updateCaptor.firstValue
            assertThat(passed.description).isEqualTo("Only description")
            assertThat(passed.amount).isEqualByComparingTo("15.00")
            assertThat(passed.date).isEqualTo(today)
            assertThat(passed.notes).isEqualTo("Original notes")
            assertThat(passed.categoryId).isEqualTo(categoryId)
        }

        @Test
        fun `fails when transaction not found for accountId`() {
            val txId = UUID.randomUUID()
            whenever(transactionRepository.findById(txId, accountId)).thenReturn(null)

            val ex =
                assertThrows<NotFoundException> {
                    service.update(txId, UpdateTransactionCommand(description = "X"), accountId, userId)
                }
            assertThat(ex.errorCode).isEqualTo(BankingErrorCode.TRANSACTION_NOT_FOUND)
        }

        @Test
        fun `fails when updated amount is zero or negative`() {
            val txId = UUID.randomUUID()
            val existing =
                Transaction(
                    id = txId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("10.00"),
                    date = today,
                    description = "X",
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(transactionRepository.findById(txId, accountId)).thenReturn(existing)

            assertThrows<IllegalArgumentException> {
                service.update(txId, UpdateTransactionCommand(amount = BigDecimal.ZERO), accountId, userId)
            }
        }

        @Test
        fun `fails when update date is older than 10-year moving window`() {
            val txId = UUID.randomUUID()
            val existing =
                Transaction(
                    id = txId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("10.00"),
                    date = today,
                    description = "X",
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(transactionRepository.findById(txId, accountId)).thenReturn(existing)
            val tooOldDate = LocalDate.now().minusYears(10).minusDays(1)

            assertThrows<ValidationException> {
                service.update(txId, UpdateTransactionCommand(date = tooOldDate), accountId, userId)
            }.also { ex ->
                assertThat(ex.errorCode).isEqualTo(BankingErrorCode.TRANSACTION_DATE_OUTSIDE_ALLOWED_WINDOW)
                assertThat(ex.field).isEqualTo("date")
            }
            verify(transactionRepository, never()).update(any())
        }

        @Test
        fun `updates attribution when belongsTo fields are provided`() {
            val txId = UUID.randomUUID()
            val existing =
                Transaction(
                    id = txId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    belongsToUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("10.00"),
                    date = today,
                    description = "X",
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(transactionRepository.findById(txId, accountId)).thenReturn(existing)
            val updateCaptor = argumentCaptor<Transaction>()
            whenever(transactionRepository.update(updateCaptor.capture())).thenAnswer { updateCaptor.firstValue }

            service.update(
                txId,
                UpdateTransactionCommand(
                    belongsToName = "Partner",
                    belongsToProvided = true,
                ),
                accountId,
                userId,
            )

            assertThat(updateCaptor.firstValue.belongsToUserId).isNull()
            assertThat(updateCaptor.firstValue.belongsToName).isEqualTo("Partner")
        }

        @Test
        fun `replaces tags when tags are provided on update`() {
            val txId = UUID.randomUUID()
            val existing =
                Transaction(
                    id = txId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("10.00"),
                    date = today,
                    description = "X",
                    tags = listOf("Old"),
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(transactionRepository.findById(txId, accountId)).thenReturn(existing)
            val updateCaptor = argumentCaptor<Transaction>()
            whenever(transactionRepository.update(updateCaptor.capture())).thenAnswer { updateCaptor.firstValue }

            service.update(
                txId,
                UpdateTransactionCommand(tags = listOf("New", "Weekend"), tagsProvided = true),
                accountId,
                userId,
            )

            assertThat(updateCaptor.firstValue.tags).containsExactly("New", "Weekend")
        }

        @Test
        fun `clears category when categoryId is explicitly provided as null`() {
            val txId = UUID.randomUUID()
            val categoryId = UUID.randomUUID()
            val existing =
                Transaction(
                    id = txId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("10.00"),
                    date = today,
                    description = "X",
                    categoryId = categoryId,
                    categorySource = CategorySource.MANUAL,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(transactionRepository.findById(txId, accountId)).thenReturn(existing)
            val updateCaptor = argumentCaptor<Transaction>()
            whenever(transactionRepository.update(updateCaptor.capture())).thenAnswer { updateCaptor.firstValue }

            service.update(
                txId,
                UpdateTransactionCommand(categoryId = null, categoryIdProvided = true),
                accountId,
                userId,
            )

            assertThat(updateCaptor.firstValue.categoryId).isNull()
            assertThat(updateCaptor.firstValue.categorySource).isNull()
        }

        @Test
        fun `does not learn implicit rule when updating non-manual transaction`() {
            val txId = UUID.randomUUID()
            val categoryId = UUID.randomUUID()
            val existing =
                Transaction(
                    id = txId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    source = TransactionSource.OPEN_FINANCE,
                    amount = BigDecimal("10.00"),
                    date = today,
                    description = "Imported",
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(transactionRepository.findById(txId, accountId)).thenReturn(existing)
            whenever(transactionRepository.update(any())).thenAnswer { it.getArgument(0) }

            service.update(
                txId,
                UpdateTransactionCommand(categoryId = categoryId),
                accountId,
                userId,
            )

            verify(transactionRuleEngine, never()).learnFromManualAssignment(
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any(),
            )
        }

        @Test
        fun `manual update applies suggestion to untouched fields`() {
            whenever(userRuleApplicationModeService.get(userId)).thenReturn(RuleApplicationMode.ALWAYS)
            val txId = UUID.randomUUID()
            val existing =
                Transaction(
                    id = txId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("10.00"),
                    date = today,
                    description = "Coffee",
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(transactionRepository.findById(txId, accountId)).thenReturn(existing)
            val suggestedCategoryId = UUID.randomUUID()
            val suggestionRuleId = UUID.randomUUID()
            whenever(transactionRuleEngine.suggest(accountId, "Coffee", CreditDebitType.DEBIT)).thenReturn(
                RuleSuggestion(
                    ruleId = suggestionRuleId,
                    categoryId = suggestedCategoryId,
                    merchantId = null,
                    appliedRuleIds = listOf(suggestionRuleId),
                ),
            )
            val updateCaptor = argumentCaptor<Transaction>()
            whenever(transactionRepository.update(updateCaptor.capture())).thenAnswer { updateCaptor.firstValue }

            service.update(
                txId,
                UpdateTransactionCommand(notes = "updated"),
                accountId,
                userId,
            )

            assertThat(updateCaptor.firstValue.categoryId).isEqualTo(suggestedCategoryId)
            assertThat(updateCaptor.firstValue.categorySource).isEqualTo(CategorySource.RULE)
            assertThat(updateCaptor.firstValue.appliedRuleIds).hasSize(1)
            assertThat(updateCaptor.firstValue.appliedRuleHistory).hasSize(1)
            verify(transactionRuleEngine).suggest(accountId, "Coffee", CreditDebitType.DEBIT)
        }

        @Test
        fun `manual update appends learned implicit rule to applied rule history`() {
            whenever(userRuleApplicationModeService.get(userId)).thenReturn(RuleApplicationMode.ALWAYS)
            val txId = UUID.randomUUID()
            val oldCategoryId = UUID.randomUUID()
            val newCategoryId = UUID.randomUUID()
            val learnedRuleId = UUID.randomUUID()
            val existing =
                Transaction(
                    id = txId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("10.00"),
                    date = today,
                    description = "Coffee",
                    categoryId = oldCategoryId,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(transactionRepository.findById(txId, accountId)).thenReturn(existing)
            whenever(
                transactionRuleEngine.learnFromManualAssignment(
                    accountId,
                    "Coffee",
                    newCategoryId,
                    null,
                    null,
                    null,
                    false,
                ),
            ).thenReturn(learnedRuleId)
            whenever(transactionRuleEngine.suggest(accountId, "Coffee", CreditDebitType.DEBIT)).thenReturn(null)
            val updateCaptor = argumentCaptor<Transaction>()
            whenever(transactionRepository.update(updateCaptor.capture())).thenAnswer { updateCaptor.firstValue }

            service.update(
                txId,
                UpdateTransactionCommand(categoryId = newCategoryId, categoryIdProvided = true),
                accountId,
                userId,
            )

            assertThat(updateCaptor.firstValue.appliedRuleIds).containsExactly(learnedRuleId)
            assertThat(updateCaptor.firstValue.appliedRuleHistory).hasSize(1)
            assertThat(updateCaptor.firstValue.appliedRuleHistory.first().ruleId).isEqualTo(learnedRuleId)
        }

        @Test
        fun `allows amount-only update for BALANCE_ADJUSTMENT`() {
            val txId = UUID.randomUUID()
            val existing =
                Transaction(
                    id = txId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.CREDIT,
                    type = TransactionType.BALANCE_ADJUSTMENT,
                    amount = BigDecimal("1000.00"),
                    date = today,
                    description = "Saldo inicial",
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(transactionRepository.findById(txId, accountId)).thenReturn(existing)
            val updateCaptor = argumentCaptor<Transaction>()
            whenever(transactionRepository.update(updateCaptor.capture())).thenAnswer { updateCaptor.firstValue }

            service.update(
                txId,
                UpdateTransactionCommand(amount = BigDecimal("1500.00")),
                accountId,
                userId,
            )

            assertThat(updateCaptor.firstValue.amount).isEqualByComparingTo("1500.00")
            assertThat(updateCaptor.firstValue.type).isEqualTo(TransactionType.BALANCE_ADJUSTMENT)
        }

        @Test
        fun `rejects category update on BALANCE_ADJUSTMENT`() {
            val txId = UUID.randomUUID()
            val existing =
                Transaction(
                    id = txId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.CREDIT,
                    type = TransactionType.BALANCE_ADJUSTMENT,
                    amount = BigDecimal("1000.00"),
                    date = today,
                    description = "Saldo inicial",
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(transactionRepository.findById(txId, accountId)).thenReturn(existing)

            val ex =
                assertThrows<ValidationException> {
                    service.update(
                        txId,
                        UpdateTransactionCommand(
                            categoryId = UUID.randomUUID(),
                            categoryIdProvided = true,
                        ),
                        accountId,
                        userId,
                    )
                }
            assertThat(ex.errorCode).isEqualTo(BankingErrorCode.BALANCE_ADJUSTMENT_ONLY_AMOUNT_EDITABLE)
            verify(transactionRepository, never()).update(any())
        }

        @Test
        fun `rejects description update on BALANCE_ADJUSTMENT`() {
            val txId = UUID.randomUUID()
            val existing =
                Transaction(
                    id = txId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.CREDIT,
                    type = TransactionType.BALANCE_ADJUSTMENT,
                    amount = BigDecimal("1000.00"),
                    date = today,
                    description = "Saldo inicial",
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(transactionRepository.findById(txId, accountId)).thenReturn(existing)

            assertThrows<ValidationException> {
                service.update(
                    txId,
                    UpdateTransactionCommand(description = "Alterado"),
                    accountId,
                    userId,
                )
            }
            verify(transactionRepository, never()).update(any())
        }
    }

    @Nested
    @DisplayName("similar transaction edit propagation")
    inner class SimilarTransactionEditPropagationTests {
        @Test
        fun `preview returns count for similar non-manual transactions`() {
            val txId = UUID.randomUUID()
            val existing =
                Transaction(
                    id = txId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    source = TransactionSource.OPEN_FINANCE,
                    amount = BigDecimal("15.00"),
                    date = today,
                    description = "Renamed",
                    descriptionRaw = "IFOOD*123",
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(transactionRepository.findById(txId, accountId)).thenReturn(existing)
            whenever(
                transactionRepository.countSimilarNonManualTransactions(
                    accountId,
                    txId,
                    "IFOOD*123",
                    CreditDebitType.DEBIT,
                ),
            ).thenReturn(2)

            val result = service.previewSimilarTransactionEdit(txId, accountId)

            assertThat(result.similarCount).isEqualTo(2)
            assertThat(result.matchDescription).isEqualTo("IFOOD*123")
            assertThat(result.supportsFuturePropagation).isTrue()
        }

        @Test
        fun `applyEditToSimilarTransactions rejects BALANCE_ADJUSTMENT`() {
            val txId = UUID.randomUUID()
            val current =
                Transaction(
                    id = txId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.CREDIT,
                    type = TransactionType.BALANCE_ADJUSTMENT,
                    source = TransactionSource.MANUAL,
                    amount = BigDecimal("1000.00"),
                    date = today,
                    description = "Saldo inicial",
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(transactionRepository.findById(txId, accountId)).thenReturn(current)

            val ex =
                assertThrows<ValidationException> {
                    service.applyEditToSimilarTransactions(
                        id = txId,
                        matchDescription = "x",
                        command =
                            UpdateTransactionCommand(
                                categoryId = UUID.randomUUID(),
                                categoryIdProvided = true,
                            ),
                        applyToFuture = false,
                        applyToExisting = true,
                        accountId = accountId,
                    )
                }
            assertThat(ex.errorCode).isEqualTo(BankingErrorCode.BALANCE_ADJUSTMENT_ONLY_AMOUNT_EDITABLE)
            verify(transactionRepository, never()).findSimilarNonManualTransactions(any(), any(), any(), any())
        }

        @Test
        fun `applyEditToSimilarTransactions updates similar rows and creates future rule`() {
            val txId = UUID.randomUUID()
            val current =
                Transaction(
                    id = txId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    source = TransactionSource.OPEN_FINANCE,
                    amount = BigDecimal("18.00"),
                    date = today,
                    description = "iFood",
                    descriptionRaw = "IFOOD*123",
                    tags = listOf("Delivery"),
                    categoryId = UUID.randomUUID(),
                    categorySource = CategorySource.MANUAL,
                    createdAt = now,
                    updatedAt = now,
                )
            val similar =
                Transaction(
                    id = UUID.randomUUID(),
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    source = TransactionSource.OPEN_FINANCE,
                    amount = BigDecimal("12.00"),
                    date = today.minusDays(1),
                    description = "IFOOD*123",
                    tags = emptyList(),
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(transactionRepository.findById(txId, accountId)).thenReturn(current)
            whenever(
                transactionRepository.findSimilarNonManualTransactions(
                    accountId,
                    txId,
                    "IFOOD*123",
                    CreditDebitType.DEBIT,
                ),
            ).thenReturn(listOf(similar))
            whenever(transactionRepository.update(any())).thenAnswer { it.getArgument(0) }

            val result =
                service.applyEditToSimilarTransactions(
                    id = txId,
                    matchDescription = "IFOOD*123",
                    command =
                        UpdateTransactionCommand(
                            description = "iFood",
                            categoryId = current.categoryId,
                            categoryIdProvided = true,
                            tags = listOf("Delivery"),
                            tagsProvided = true,
                        ),
                    applyToFuture = true,
                    accountId = accountId,
                )

            val updateCaptor = argumentCaptor<Transaction>()
            verify(transactionRepository).update(updateCaptor.capture())
            assertThat(updateCaptor.firstValue.description).isEqualTo("iFood")
            assertThat(updateCaptor.firstValue.tags).containsExactly("Delivery")
            assertThat(updateCaptor.firstValue.categoryId).isEqualTo(current.categoryId)
            assertThat(updateCaptor.firstValue.categorySource).isEqualTo(CategorySource.MANUAL)
            verify(transactionRuleEngine).learnFromManualAssignment(
                eq(accountId),
                eq("IFOOD*123"),
                eq(current.categoryId),
                eq(null),
                eq("iFood"),
                eq(listOf("Delivery")),
                eq(true),
            )
            assertThat(result.affectedCount).isEqualTo(1)
            assertThat(result.futureRuleCreated).isTrue()
        }

        @Test
        fun `applyEditToSimilarTransactions can clear category without creating future rule`() {
            val txId = UUID.randomUUID()
            val current =
                Transaction(
                    id = txId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    source = TransactionSource.OPEN_FINANCE,
                    amount = BigDecimal("18.00"),
                    date = today,
                    description = "iFood",
                    descriptionRaw = "IFOOD*123",
                    categoryId = null,
                    categorySource = null,
                    createdAt = now,
                    updatedAt = now,
                )
            val similar =
                Transaction(
                    id = UUID.randomUUID(),
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    source = TransactionSource.OPEN_FINANCE,
                    amount = BigDecimal("12.00"),
                    date = today.minusDays(1),
                    description = "IFOOD*123",
                    categoryId = UUID.randomUUID(),
                    categorySource = CategorySource.MANUAL,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(transactionRepository.findById(txId, accountId)).thenReturn(current)
            whenever(
                transactionRepository.findSimilarNonManualTransactions(
                    accountId,
                    txId,
                    "IFOOD*123",
                    CreditDebitType.DEBIT,
                ),
            ).thenReturn(listOf(similar))
            whenever(transactionRepository.update(any())).thenAnswer { it.getArgument(0) }

            val result =
                service.applyEditToSimilarTransactions(
                    id = txId,
                    matchDescription = "IFOOD*123",
                    command = UpdateTransactionCommand(categoryId = null, categoryIdProvided = true),
                    applyToFuture = true,
                    accountId = accountId,
                )

            val updateCaptor = argumentCaptor<Transaction>()
            verify(transactionRepository).update(updateCaptor.capture())
            assertThat(updateCaptor.firstValue.categoryId).isNull()
            assertThat(updateCaptor.firstValue.categorySource).isNull()
            verify(transactionRuleEngine, never()).learnFromManualAssignment(
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any(),
            )
            assertThat(result.affectedCount).isEqualTo(1)
            assertThat(result.futureRuleCreated).isFalse()
        }
    }

    @Nested
    @DisplayName("delete")
    inner class DeleteTests {
        @Test
        fun `soft-deletes MANUAL transaction and returns true`() {
            val txId = UUID.randomUUID()
            val existing =
                Transaction(
                    id = txId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    source = TransactionSource.MANUAL,
                    amount = BigDecimal("10.00"),
                    date = today,
                    description = "X",
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(transactionRepository.findById(txId, accountId)).thenReturn(existing)
            whenever(transactionRepository.softDelete(txId, accountId)).thenReturn(true)

            val result = service.delete(txId, accountId)

            assertThat(result).isTrue
            verify(transactionRepository).softDelete(txId, accountId)
        }

        @Test
        fun `throws when transaction not found for accountId`() {
            val txId = UUID.randomUUID()
            whenever(transactionRepository.findById(txId, accountId)).thenReturn(null)

            val ex = assertThrows<NotFoundException> { service.delete(txId, accountId) }
            assertThat(ex.errorCode).isEqualTo(BankingErrorCode.TRANSACTION_NOT_FOUND)
        }

        @Test
        fun `throws when transaction source is not MANUAL`() {
            val txId = UUID.randomUUID()
            val existing =
                Transaction(
                    id = txId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    source = TransactionSource.OPEN_FINANCE,
                    amount = BigDecimal("10.00"),
                    date = today,
                    description = "OFB",
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(transactionRepository.findById(txId, accountId)).thenReturn(existing)

            val ex = assertThrows<IllegalStateException> { service.delete(txId, accountId) }
            assertThat(ex.message).contains("MANUAL").contains("OFB")
        }
    }

    @Nested
    @DisplayName("listByFinancialAccount")
    inner class ListByFinancialAccountTests {
        @Test
        fun `listByFinancialAccount throws when financialAccountId does not belong to accountId`() {
            whenever(financialAccountGuard.requireOwnership(financialAccountId, accountId))
                .thenThrow(IllegalArgumentException("Financial account $financialAccountId does not belong to account $accountId"))

            val ex =
                assertThrows<IllegalArgumentException> {
                    service.listByFinancialAccount(financialAccountId, accountId)
                }
            assertThat(ex.message).contains(financialAccountId.toString())
        }

        @Test
        fun `delegates to repository with correct arguments`() {
            whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(listOf(ownedAccount))
            val list =
                listOf(
                    Transaction(
                        id = UUID.randomUUID(),
                        financialAccountId = financialAccountId,
                        accountId = accountId,
                        createdByUserId = userId,
                        creditDebitType = CreditDebitType.DEBIT,
                        type = TransactionType.OTHER_EXPENSE,
                        amount = BigDecimal("1.00"),
                        date = today,
                        description = "X",
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            whenever(transactionRepository.findByFinancialAccount(financialAccountId, accountId)).thenReturn(list)

            val result = service.listByFinancialAccount(financialAccountId, accountId)

            assertThat(result).hasSize(1)
            verify(transactionRepository).findByFinancialAccount(eq(financialAccountId), eq(accountId))
        }
    }

    @Nested
    @DisplayName("listCategories")
    inner class ListCategoriesTests {
        @Test
        fun `returns combined system and user-defined categories for account`() {
            val cat1 =
                TransactionCategory(
                    id = UUID.randomUUID(),
                    accountId = null,
                    parentId = null,
                    name = "System",
                    applicableTo = CategoryApplicableTo.EXPENSE,
                    icon = "x",
                    color = "#000",
                    isSystem = true,
                    sortOrder = 0,
                    createdAt = now,
                    updatedAt = now,
                )
            val cat2 =
                TransactionCategory(
                    id = UUID.randomUUID(),
                    accountId = accountId,
                    parentId = null,
                    name = "User",
                    applicableTo = CategoryApplicableTo.ANY,
                    icon = null,
                    color = null,
                    isSystem = false,
                    sortOrder = 1,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(categoryRepository.findAll(accountId)).thenReturn(listOf(cat1, cat2))

            val result = service.listCategories(accountId)

            assertThat(result).hasSize(2)
            assertThat(result.map { it.name }).containsExactlyInAnyOrder("System", "User")
            verify(categoryRepository).findAll(accountId)
        }
    }

    @Nested
    @DisplayName("findAccessibleCategory")
    inner class FindAccessibleCategoryTests {
        @Test
        fun `returns category when system-owned`() {
            val categoryId = UUID.randomUUID()
            val category =
                TransactionCategory(
                    id = categoryId,
                    accountId = null,
                    parentId = null,
                    name = "System",
                    applicableTo = CategoryApplicableTo.EXPENSE,
                    icon = null,
                    color = null,
                    isSystem = true,
                    sortOrder = 0,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(categoryRepository.findById(categoryId)).thenReturn(category)

            val result = service.findAccessibleCategory(categoryId, accountId)

            assertThat(result).isEqualTo(category)
        }

        @Test
        fun `returns category when owned by current account`() {
            val categoryId = UUID.randomUUID()
            val category =
                TransactionCategory(
                    id = categoryId,
                    accountId = accountId,
                    parentId = null,
                    name = "User",
                    applicableTo = CategoryApplicableTo.EXPENSE,
                    icon = null,
                    color = null,
                    isSystem = false,
                    sortOrder = 10,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(categoryRepository.findById(categoryId)).thenReturn(category)

            val result = service.findAccessibleCategory(categoryId, accountId)

            assertThat(result).isEqualTo(category)
        }

        @Test
        fun `returns null when category belongs to another account`() {
            val categoryId = UUID.randomUUID()
            val category =
                TransactionCategory(
                    id = categoryId,
                    accountId = UUID.randomUUID(),
                    parentId = null,
                    name = "Foreign",
                    applicableTo = CategoryApplicableTo.EXPENSE,
                    icon = null,
                    color = null,
                    isSystem = false,
                    sortOrder = 10,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(categoryRepository.findById(categoryId)).thenReturn(category)

            val result = service.findAccessibleCategory(categoryId, accountId)

            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("findAccessibleCategoriesByIds")
    inner class FindAccessibleCategoriesByIdsTests {
        @Test
        fun `returns only requested and accessible categories`() {
            val systemId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val ignoredId = UUID.randomUUID()
            val foreignId = UUID.randomUUID()
            val systemCategory =
                TransactionCategory(
                    id = systemId,
                    accountId = null,
                    parentId = null,
                    name = "System",
                    applicableTo = CategoryApplicableTo.EXPENSE,
                    icon = null,
                    color = null,
                    isSystem = true,
                    sortOrder = 0,
                    createdAt = now,
                    updatedAt = now,
                )
            val userCategory =
                TransactionCategory(
                    id = userId,
                    accountId = accountId,
                    parentId = null,
                    name = "User",
                    applicableTo = CategoryApplicableTo.ANY,
                    icon = null,
                    color = null,
                    isSystem = false,
                    sortOrder = 10,
                    createdAt = now,
                    updatedAt = now,
                )
            val nonRequestedCategory =
                TransactionCategory(
                    id = ignoredId,
                    accountId = accountId,
                    parentId = null,
                    name = "Not requested",
                    applicableTo = CategoryApplicableTo.ANY,
                    icon = null,
                    color = null,
                    isSystem = false,
                    sortOrder = 20,
                    createdAt = now,
                    updatedAt = now,
                )
            val foreignCategory =
                TransactionCategory(
                    id = foreignId,
                    accountId = UUID.randomUUID(),
                    parentId = null,
                    name = "Foreign",
                    applicableTo = CategoryApplicableTo.EXPENSE,
                    icon = null,
                    color = null,
                    isSystem = false,
                    sortOrder = 30,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(categoryRepository.findAccessibleByIds(setOf(systemId, userId, foreignId), accountId))
                .thenReturn(listOf(systemCategory, userCategory))

            val result =
                service.findAccessibleCategoriesByIds(
                    categoryIds = setOf(systemId, userId, foreignId),
                    accountId = accountId,
                )

            assertThat(result.keys).containsExactlyInAnyOrder(systemId, userId)
            assertThat(result[systemId]).isEqualTo(systemCategory)
            assertThat(result[userId]).isEqualTo(userCategory)
            assertThat(result[foreignId]).isNull()
            verify(categoryRepository).findAccessibleByIds(setOf(systemId, userId, foreignId), accountId)
        }
    }

    @Nested
    @DisplayName("listTags")
    inner class ListTagsTests {
        @Test
        fun `returns distinct tags for account`() {
            whenever(transactionRepository.listTags(accountId)).thenReturn(listOf("Food", "Bills"))

            val result = service.listTags(accountId)

            assertThat(result).containsExactly("Food", "Bills")
            verify(transactionRepository).listTags(accountId)
        }
    }

    @Nested
    @DisplayName("snapshot recomputation triggers")
    inner class SnapshotRecomputationTests {
        private fun buildTransaction(
            id: UUID = UUID.randomUUID(),
            date: LocalDate = today,
            source: TransactionSource = TransactionSource.MANUAL,
        ) = Transaction(
            id = id,
            financialAccountId = financialAccountId,
            accountId = accountId,
            createdByUserId = userId,
            creditDebitType = CreditDebitType.DEBIT,
            type = TransactionType.OTHER_EXPENSE,
            status = TransactionStatus.POSTED,
            source = source,
            amount = BigDecimal("10.00"),
            date = date,
            description = "X",
            createdAt = now,
            updatedAt = now,
        )

        @Test
        fun `create triggers recomputeChain for the transaction month`() {
            whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(listOf(ownedAccount))
            val txDate = LocalDate.of(2024, 6, 15)
            val command =
                CreateTransactionCommand(
                    financialAccountId = financialAccountId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("10.00"),
                    date = txDate,
                    description = "Test",
                )
            val saved = buildTransaction(date = txDate)
            whenever(transactionRepository.save(any())).thenReturn(saved)

            service.create(command, accountId, userId)

            verify(snapshotComputationService).recomputeChain(
                financialAccountId = financialAccountId,
                accountId = accountId,
                fromYear = 2024,
                fromMonth = 6,
            )
        }

        @Test
        fun `create BALANCE_ADJUSTMENT also triggers recomputeChain for the transaction month`() {
            whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(listOf(ownedAccount))
            val txDate = LocalDate.of(2025, 1, 20)
            val command =
                CreateTransactionCommand(
                    financialAccountId = financialAccountId,
                    creditDebitType = CreditDebitType.CREDIT,
                    type = TransactionType.BALANCE_ADJUSTMENT,
                    amount = BigDecimal("500.00"),
                    date = txDate,
                    description = "Saldo inicial",
                )
            val saved =
                buildTransaction(
                    date = txDate,
                ).copy(
                    creditDebitType = CreditDebitType.CREDIT,
                    type = TransactionType.BALANCE_ADJUSTMENT,
                )
            whenever(transactionRepository.save(any())).thenReturn(saved)

            service.create(command, accountId, userId)

            verify(snapshotComputationService).recomputeChain(
                financialAccountId = financialAccountId,
                accountId = accountId,
                fromYear = 2025,
                fromMonth = 1,
            )
        }

        @Test
        fun `update triggers recomputeChain from the earliest of old and new date`() {
            val txId = UUID.randomUUID()
            val oldDate = LocalDate.of(2024, 3, 10)
            val newDate = LocalDate.of(2024, 5, 20)
            val existing = buildTransaction(id = txId, date = oldDate)
            val updated = existing.copy(date = newDate)
            whenever(transactionRepository.findById(txId, accountId)).thenReturn(existing)
            whenever(transactionRepository.update(any())).thenReturn(updated)

            service.update(txId, UpdateTransactionCommand(date = newDate), accountId, userId)

            // Should recompute from the earlier month (March, not May)
            verify(snapshotComputationService).recomputeChain(
                financialAccountId = financialAccountId,
                accountId = accountId,
                fromYear = 2024,
                fromMonth = 3,
            )
        }

        @Test
        fun `update triggers recomputeChain from the same month when date is unchanged`() {
            val txId = UUID.randomUUID()
            val txDate = LocalDate.of(2024, 8, 1)
            val existing = buildTransaction(id = txId, date = txDate)
            val updated = existing.copy(description = "New desc")
            whenever(transactionRepository.findById(txId, accountId)).thenReturn(existing)
            whenever(transactionRepository.update(any())).thenReturn(updated)

            service.update(txId, UpdateTransactionCommand(description = "New desc"), accountId, userId)

            verify(snapshotComputationService).recomputeChain(
                financialAccountId = financialAccountId,
                accountId = accountId,
                fromYear = 2024,
                fromMonth = 8,
            )
        }

        @Test
        fun `delete triggers recomputeChain for the deleted transaction month`() {
            val txId = UUID.randomUUID()
            val txDate = LocalDate.of(2024, 11, 5)
            val existing = buildTransaction(id = txId, date = txDate)
            whenever(transactionRepository.findById(txId, accountId)).thenReturn(existing)
            whenever(transactionRepository.softDelete(txId, accountId)).thenReturn(true)

            service.delete(txId, accountId)

            verify(snapshotComputationService).recomputeChain(
                financialAccountId = financialAccountId,
                accountId = accountId,
                fromYear = 2024,
                fromMonth = 11,
            )
        }

        @Test
        fun `delete does not trigger recomputeChain when softDelete returns false`() {
            val txId = UUID.randomUUID()
            val existing = buildTransaction(id = txId)
            whenever(transactionRepository.findById(txId, accountId)).thenReturn(existing)
            whenever(transactionRepository.softDelete(txId, accountId)).thenReturn(false)

            service.delete(txId, accountId)

            verify(snapshotComputationService, never()).recomputeChain(any(), any(), any(), any())
        }
    }

    @Nested
    @DisplayName("listTransactions")
    inner class ListTransactionsTests {
        @Test
        fun `filters by belongsToName when provided`() {
            val tx1 =
                Transaction(
                    id = UUID.randomUUID(),
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    belongsToName = "Partner",
                    belongsToUserId = null,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("11.00"),
                    date = today,
                    description = "A",
                    createdAt = now,
                    updatedAt = now,
                )
            val tx2 =
                Transaction(
                    id = UUID.randomUUID(),
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    belongsToUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("12.00"),
                    date = today,
                    description = "B",
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(
                transactionRepository.findPage(
                    TransactionListQuery(
                        accountId = accountId,
                        financialAccountId = null,
                        belongsToUserId = null,
                        belongsToName = "Partner",
                        search = null,
                        tags = null,
                        billId = null,
                        categoryIds = null,
                        uncategorizedOnly = false,
                        creditDebitType = null,
                        types = null,
                        startDate = LocalDate.of(today.year, today.monthValue, 1),
                        endDateExclusive = LocalDate.of(today.year, today.monthValue, 1).plusMonths(1),
                        limit = 20,
                        offset = 0,
                    ),
                ),
            ).thenReturn(TransactionPageResult(items = listOf(tx1), totalCount = 1))

            val result =
                service.listTransactions(
                    accountId = accountId,
                    financialAccountId = null,
                    belongsToUserId = null,
                    belongsToName = "Partner",
                    search = null,
                    tags = null,
                    billId = null,
                    categoryId = null,
                    uncategorized = null,
                    creditDebitType = null,
                    types = null,
                    month = today.monthValue,
                    year = today.year,
                    first = 20,
                    after = null,
                )

            assertThat(result.totalCount).isEqualTo(1)
            assertThat(result.edges).hasSize(1)
            assertThat(result.edges.first().node.id).isEqualTo(tx1.id)
        }

        @Test
        fun `filters by any matching tag when tags filter has multiple values`() {
            val tx1 =
                Transaction(
                    id = UUID.randomUUID(),
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("20.00"),
                    date = today,
                    description = "A",
                    tags = listOf("Food", "Weekend"),
                    createdAt = now,
                    updatedAt = now,
                )
            val tx2 =
                Transaction(
                    id = UUID.randomUUID(),
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("15.00"),
                    date = today,
                    description = "B",
                    tags = listOf("Bills"),
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(
                transactionRepository.findPage(
                    TransactionListQuery(
                        accountId = accountId,
                        financialAccountId = null,
                        belongsToUserId = null,
                        belongsToName = null,
                        search = null,
                        tags = setOf("Weekend", "Transport"),
                        billId = null,
                        categoryIds = null,
                        uncategorizedOnly = false,
                        creditDebitType = null,
                        types = null,
                        startDate = LocalDate.of(today.year, today.monthValue, 1),
                        endDateExclusive = LocalDate.of(today.year, today.monthValue, 1).plusMonths(1),
                        limit = 20,
                        offset = 0,
                    ),
                ),
            ).thenReturn(TransactionPageResult(items = listOf(tx1), totalCount = 1))

            val result =
                service.listTransactions(
                    accountId = accountId,
                    financialAccountId = null,
                    belongsToUserId = null,
                    belongsToName = null,
                    search = null,
                    tags = listOf("Weekend", "Transport"),
                    billId = null,
                    categoryId = null,
                    uncategorized = null,
                    creditDebitType = null,
                    types = null,
                    month = today.monthValue,
                    year = today.year,
                    first = 20,
                    after = null,
                )

            assertThat(result.totalCount).isEqualTo(1)
            assertThat(result.edges).hasSize(1)
            assertThat(result.edges.first().node.id).isEqualTo(tx1.id)
        }
    }

    @Nested
    @DisplayName("breakdowns")
    inner class BreakdownTests {
        @Test
        fun `getCategoryBreakdown delegates to filtered repository query`() {
            whenever(
                transactionRepository.getFilteredCategoryBreakdown(
                    any(),
                    eq(CategoryApplicableTo.EXPENSE),
                ),
            ).thenReturn(emptyList())

            val result =
                service.getCategoryBreakdown(
                    accountId = accountId,
                    financialAccountId = null,
                    belongsToUserId = null,
                    belongsToName = null,
                    search = null,
                    tags = listOf("Weekend"),
                    billId = null,
                    categoryId = null,
                    uncategorized = null,
                    month = today.monthValue,
                    year = today.year,
                    applicableTo = CategoryApplicableTo.EXPENSE,
                )

            assertThat(result).isEmpty()
            val queryCaptor = argumentCaptor<TransactionListQuery>()
            verify(transactionRepository).getFilteredCategoryBreakdown(queryCaptor.capture(), eq(CategoryApplicableTo.EXPENSE))
            assertThat(queryCaptor.firstValue.tags).containsExactly("Weekend")
            assertThat(queryCaptor.firstValue.creditDebitType).isEqualTo(CreditDebitType.DEBIT)
        }

        @Test
        fun `getTagBreakdown delegates to filtered repository query`() {
            whenever(
                transactionRepository.getFilteredTagBreakdown(
                    any(),
                    eq(CategoryApplicableTo.INCOME),
                ),
            ).thenReturn(
                listOf(
                    TagBreakdownRow(
                        tag = "Salary",
                        totalAmount = BigDecimal("100.00"),
                        transactionCount = 1,
                    ),
                ),
            )

            val result =
                service.getTagBreakdown(
                    accountId = accountId,
                    financialAccountId = null,
                    belongsToUserId = null,
                    belongsToName = null,
                    search = null,
                    tags = null,
                    billId = null,
                    categoryId = null,
                    uncategorized = null,
                    month = today.monthValue,
                    year = today.year,
                    applicableTo = CategoryApplicableTo.INCOME,
                )

            assertThat(result).hasSize(1)
            assertThat(result.first().tag).isEqualTo("Salary")
            assertThat(result.first().percentage).isEqualTo(100.0)
            val queryCaptor = argumentCaptor<TransactionListQuery>()
            verify(transactionRepository).getFilteredTagBreakdown(queryCaptor.capture(), eq(CategoryApplicableTo.INCOME))
            assertThat(queryCaptor.firstValue.creditDebitType).isEqualTo(CreditDebitType.CREDIT)
        }
    }
}
