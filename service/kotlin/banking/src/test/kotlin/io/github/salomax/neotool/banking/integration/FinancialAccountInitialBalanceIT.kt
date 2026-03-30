package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.banking.domain.account.CreateFinancialAccountCommand
import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.service.account.FinancialAccountService
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.repo.transaction.MonthlyBalanceSnapshotRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("FinancialAccount initial balance IT")
class FinancialAccountInitialBalanceIT : BankingPostgresIntegrationTest() {
    @Inject
    lateinit var financialAccountService: FinancialAccountService

    @Inject
    lateinit var financialAccountRepository: FinancialAccountRepository

    @Inject
    lateinit var transactionRepository: TransactionRepository

    @Inject
    lateinit var snapshotRepository: MonthlyBalanceSnapshotRepository

    private lateinit var accountId: UUID
    private lateinit var userId: UUID

    @BeforeEach
    fun initContextIds() {
        accountId = UUID.randomUUID()
        userId = UUID.randomUUID()
    }

    @Test
    fun `create account with initial balance persists BALANCE_ADJUSTMENT and snapshot`() {
        val initialBalanceDate = LocalDate.now()
        val command =
            CreateFinancialAccountCommand(
                type = FinancialAccountType.BANK,
                subtype = FinancialAccountSubtype.CONTA_CORRENTE,
                name = "Conta com saldo inicial",
                issuerName = "Banco Teste",
                initialBalance = BigDecimal("1200.00"),
                initialBalanceDate = initialBalanceDate,
            )

        val created = financialAccountService.create(command, accountId, userId)

        val financialAccountId = created.id
        assertThat(financialAccountId).isNotNull

        val transactions = transactionRepository.findByFinancialAccount(financialAccountId!!, accountId)
        assertThat(transactions).hasSize(1)
        val balanceAdjustment = transactions.first()
        assertThat(balanceAdjustment.type).isEqualTo(TransactionType.BALANCE_ADJUSTMENT)
        assertThat(balanceAdjustment.creditDebitType).isEqualTo(CreditDebitType.CREDIT)
        assertThat(balanceAdjustment.amount).isEqualByComparingTo("1200.00")
        assertThat(balanceAdjustment.date).isEqualTo(initialBalanceDate)

        val snapshot =
            snapshotRepository.findByYearMonth(
                financialAccountId = financialAccountId,
                year = initialBalanceDate.year,
                month = initialBalanceDate.monthValue,
            )
        assertThat(snapshot).isNotNull
        assertThat(snapshot!!.openingBalance).isEqualByComparingTo("0.00")
        assertThat(snapshot.closingBalance).isEqualByComparingTo("1200.00")
        assertThat(snapshot.totalIncome).isEqualByComparingTo("0.00")
        assertThat(snapshot.totalExpenses).isEqualByComparingTo("0.00")
        assertThat(snapshot.isDirty).isFalse()
    }

    @Test
    fun `create account with initial balance but no date fails and does not persist account`() {
        val command =
            CreateFinancialAccountCommand(
                type = FinancialAccountType.BANK,
                subtype = FinancialAccountSubtype.CONTA_CORRENTE,
                name = "Conta inválida",
                issuerName = "Banco Teste",
                initialBalance = BigDecimal("350.00"),
                initialBalanceDate = null,
            )

        val ex = assertThrows<IllegalArgumentException> { financialAccountService.create(command, accountId, userId) }

        assertThat(ex.message).contains("initialBalanceDate is required when initialBalance is provided")
        assertThat(financialAccountRepository.findByAccountId(accountId)).isEmpty()
    }
}
