package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.banking.domain.account.CreateFinancialAccountCommand
import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.domain.transaction.CreateTransactionCommand
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.service.account.FinancialAccountService
import io.github.salomax.neotool.banking.service.transaction.TransactionService
import io.github.salomax.neotool.common.error.ValidationException
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
@DisplayName("FinancialAccount delete integration")
class FinancialAccountDeleteIntegrationTest : BankingPostgresIntegrationTest() {
    @Inject
    lateinit var financialAccountService: FinancialAccountService

    @Inject
    lateinit var financialAccountRepository: FinancialAccountRepository

    @Inject
    lateinit var transactionService: TransactionService

    private lateinit var accountId: UUID
    private lateinit var userId: UUID

    @BeforeEach
    fun initContextIds() {
        accountId = UUID.randomUUID()
        userId = UUID.randomUUID()
    }

    @Test
    fun `create account and delete should remove account from listByAccount`() {
        val account = createBankAccount(name = "Conta sem transacoes")

        val deleted = financialAccountService.delete(requireNotNull(account.id), accountId)

        assertThat(deleted).isTrue
        val activeAccounts = financialAccountRepository.findByAccountId(accountId)
        assertThat(activeAccounts.mapNotNull { it.id }).doesNotContain(account.id)
    }

    @Test
    fun `create account with manual transaction and delete should be blocked`() {
        val account = createBankAccount(name = "Conta com transacao manual")
        val financialAccountId = requireNotNull(account.id)

        transactionService.create(
            command =
                CreateTransactionCommand(
                    financialAccountId = financialAccountId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("12.34"),
                    date = LocalDate.now(),
                    description = "Compra para bloquear exclusao",
                ),
            accountId = accountId,
            userId = userId,
        )

        val ex = assertThrows<ValidationException> { financialAccountService.delete(financialAccountId, accountId) }
        assertThat(ex.errorCode).isEqualTo(BankingErrorCode.FINANCIAL_ACCOUNT_HAS_TRANSACTIONS)

        val activeAccounts = financialAccountRepository.findByAccountId(accountId)
        assertThat(activeAccounts.mapNotNull { it.id }).contains(financialAccountId)
    }

    @Test
    fun `create account with initial balance and delete should be blocked`() {
        val account =
            createBankAccount(
                name = "Conta com saldo inicial",
                initialBalance = BigDecimal("100.00"),
                initialBalanceDate = LocalDate.now(),
            )
        val financialAccountId = requireNotNull(account.id)

        val ex = assertThrows<ValidationException> { financialAccountService.delete(financialAccountId, accountId) }
        assertThat(ex.errorCode).isEqualTo(BankingErrorCode.FINANCIAL_ACCOUNT_HAS_TRANSACTIONS)

        val activeAccounts = financialAccountRepository.findByAccountId(accountId)
        assertThat(activeAccounts.mapNotNull { it.id }).contains(financialAccountId)
    }

    private fun createBankAccount(
        name: String,
        initialBalance: BigDecimal? = null,
        initialBalanceDate: LocalDate? = null,
    ): FinancialAccount =
        financialAccountService.create(
            command =
                CreateFinancialAccountCommand(
                    type = FinancialAccountType.BANK,
                    subtype = FinancialAccountSubtype.CONTA_CORRENTE,
                    name = name,
                    issuerName = "Banco Teste",
                    initialBalance = initialBalance,
                    initialBalanceDate = initialBalanceDate,
                ),
            accountId = accountId,
            userId = userId,
        )
}
