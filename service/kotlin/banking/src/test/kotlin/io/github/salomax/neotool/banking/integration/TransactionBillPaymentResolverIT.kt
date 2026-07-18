package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.banking.domain.account.CardData
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.graphql.transaction.TransactionResolver
import io.github.salomax.neotool.banking.model.dto.account.CreateFinancialAccountCommand
import io.github.salomax.neotool.banking.model.dto.transaction.CreateTransactionCommand
import io.github.salomax.neotool.banking.service.account.FinancialAccountService
import io.github.salomax.neotool.banking.service.transaction.TransactionService
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("TransactionResolver BILL_PAYMENT on credit card")
class TransactionBillPaymentResolverIT : BankingPostgresIntegrationTest() {
    @Inject
    lateinit var transactionResolver: TransactionResolver

    @Inject
    lateinit var transactionService: TransactionService

    @Inject
    lateinit var financialAccountService: FinancialAccountService

    private lateinit var accountId: UUID
    private lateinit var userId: UUID

    @BeforeEach
    fun initContextIds() {
        accountId = UUID.randomUUID()
        userId = UUID.randomUUID()
    }

    @Test
    fun `updateTransaction marks CC CREDIT as BILL_PAYMENT and excludes it from income summary`() {
        val cardAccountId =
            requireNotNull(
                financialAccountService.create(
                    CreateFinancialAccountCommand(
                        type = FinancialAccountType.CREDIT,
                        subtype = FinancialAccountSubtype.CREDIT_CARD,
                        name = "Cartao teste",
                        issuerName = "Banco Teste",
                        cardData =
                            CardData(
                                closingDay = 7,
                                dueDay = 15,
                            ),
                    ),
                    accountId = accountId,
                    userId = userId,
                ).id,
            )

        val txDate = LocalDate.of(2026, 3, 1)
        val created =
            transactionService.create(
                command =
                    CreateTransactionCommand(
                        financialAccountId = cardAccountId,
                        creditDebitType = CreditDebitType.CREDIT,
                        type = TransactionType.OTHER_INCOME,
                        amount = BigDecimal("200.00"),
                        date = txDate,
                        description = "Pagamento recebido",
                        status = TransactionStatus.POSTED,
                    ),
                accountId = accountId,
                userId = userId,
            )

        val principal = principal()
        val before =
            transactionResolver.monthSummary(
                financialAccountId = cardAccountId.toString(),
                year = 2026,
                month = 3,
                principal = principal,
            )
        assertThat(before.totalIncome).isEqualTo(200.0)

        val updated =
            transactionResolver.updateTransaction(
                id = requireNotNull(created.id).toString(),
                input = mapOf("type" to "BILL_PAYMENT"),
                principal = principal,
            )

        assertThat(updated.type).isEqualTo(TransactionType.BILL_PAYMENT)
        assertThat(updated.creditDebitType).isEqualTo(CreditDebitType.CREDIT)
        assertThat(updated.billId).isEqualTo(created.billId)

        val after =
            transactionResolver.monthSummary(
                financialAccountId = cardAccountId.toString(),
                year = 2026,
                month = 3,
                principal = principal,
            )
        assertThat(after.totalIncome).isEqualTo(0.0)
        assertThat(after.totalExpenses).isEqualTo(0.0)
    }

    private fun principal() =
        RequestPrincipal(
            principalType = PrincipalType.USER,
            userId = userId,
            serviceId = null,
            token = "test-token",
            tokenId = "test-token-id",
            permissionsFromToken = emptyList(),
            currentAccountId = accountId,
        )
}
