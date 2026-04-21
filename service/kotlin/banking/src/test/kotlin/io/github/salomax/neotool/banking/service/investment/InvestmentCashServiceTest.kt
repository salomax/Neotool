package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionRepository
import io.github.salomax.neotool.banking.domain.preferences.AccountPreferences
import io.github.salomax.neotool.banking.model.dto.transaction.CreateTransactionCommand
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionCategoryEntityRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.github.salomax.neotool.banking.service.preferences.AccountPreferencesService
import io.github.salomax.neotool.banking.service.transaction.TransactionService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@DisplayName("InvestmentCashService")
class InvestmentCashServiceTest {

    private lateinit var financialAccountRepository: FinancialAccountRepository
    private lateinit var transactionCategoryEntityRepository: TransactionCategoryEntityRepository
    private lateinit var transactionService: TransactionService
    private lateinit var accountPreferencesService: AccountPreferencesService
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var pendingActionRepository: AccountPendingActionRepository
    private lateinit var portfolioFlowEventService: PortfolioFlowEventService
    private lateinit var service: InvestmentCashService

    private val accountId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val financialAccountId = UUID.randomUUID()
    private val now = Instant.now()
    private val today = LocalDate.now()

    private val custodian =
        FinancialAccount(
            id = financialAccountId,
            accountId = accountId,
            createdByUserId = userId,
            type = FinancialAccountType.INVESTMENT,
            subtype = FinancialAccountSubtype.BROKERAGE,
            name = "XP Investimentos",
            createdAt = now,
            updatedAt = now,
        )

    @BeforeEach
    fun setUp() {
        financialAccountRepository = mock()
        transactionCategoryEntityRepository = mock()
        transactionService = mock()
        accountPreferencesService = mock()
        transactionRepository = mock()
        pendingActionRepository = mock()
        portfolioFlowEventService = mock()
        service = InvestmentCashService(
            financialAccountRepository = financialAccountRepository,
            transactionCategoryEntityRepository = transactionCategoryEntityRepository,
            transactionService = transactionService,
            accountPreferencesService = accountPreferencesService,
            transactionRepository = transactionRepository,
            pendingActionRepository = pendingActionRepository,
            portfolioFlowEventService = portfolioFlowEventService,
        )

        whenever(financialAccountRepository.findById(financialAccountId, accountId)).thenReturn(custodian)
        whenever(accountPreferencesService.get(accountId)).thenReturn(AccountPreferences.defaultsFor(accountId, now))
        whenever(transactionCategoryEntityRepository.findSystemSubcategoryId(any(), any())).thenReturn(UUID.randomUUID())
        whenever(transactionService.create(any(), eq(accountId), eq(userId))).thenAnswer { invocation ->
            val cmd = invocation.getArgument<CreateTransactionCommand>(0)
            Transaction(
                id = UUID.randomUUID(),
                financialAccountId = cmd.financialAccountId,
                accountId = accountId,
                createdByUserId = userId,
                creditDebitType = cmd.creditDebitType,
                type = cmd.type,
                amount = cmd.amount,
                date = cmd.date,
                description = cmd.description,
                status = cmd.status,
                source = cmd.transactionSource,
                investmentTransactionId = cmd.investmentTransactionId,
                categoryId = cmd.categoryId,
                transferPeerTransactionId = cmd.transferPeerTransactionId,
                createdAt = now,
                updatedAt = now,
            )
        }
    }

    @Test
    fun `deposit without description uses default aporte description`() {
        val saved =
            service.createManualCashTransaction(
                financialAccountId = financialAccountId,
                type = InvestmentCashTransactionType.DEPOSIT,
                amount = BigDecimal("100.00"),
                date = today,
                description = null,
                accountId = accountId,
                userId = userId,
            )

        val cmdCaptor = argumentCaptor<CreateTransactionCommand>()
        verify(transactionService).create(cmdCaptor.capture(), eq(accountId), eq(userId))
        assertThat(cmdCaptor.firstValue.description).isEqualTo("Aporte")
        assertThat(saved.description).isEqualTo("Aporte")
    }

    @Test
    fun `withdrawal without description uses default saque description`() {
        val saved =
            service.createManualCashTransaction(
                financialAccountId = financialAccountId,
                type = InvestmentCashTransactionType.WITHDRAWAL,
                amount = BigDecimal("100.00"),
                date = today,
                description = null,
                accountId = accountId,
                userId = userId,
            )

        val cmdCaptor = argumentCaptor<CreateTransactionCommand>()
        verify(transactionService).create(cmdCaptor.capture(), eq(accountId), eq(userId))
        assertThat(cmdCaptor.firstValue.description).isEqualTo("Saque")
        assertThat(saved.description).isEqualTo("Saque")
    }
}
