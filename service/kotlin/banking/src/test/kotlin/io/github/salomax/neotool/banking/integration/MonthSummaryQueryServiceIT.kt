package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.banking.domain.account.CreateFinancialAccountCommand
import io.github.salomax.neotool.banking.service.account.FinancialAccountService
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.service.transaction.MonthSummaryQueryService
import io.github.salomax.neotool.banking.domain.transaction.MonthlyBalanceSnapshotRepository
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
@DisplayName("MonthSummaryQueryService IT")
class MonthSummaryQueryServiceIT : BankingPostgresIntegrationTest() {
    @Inject
    lateinit var monthSummaryQueryService: MonthSummaryQueryService

    @Inject
    lateinit var financialAccountService: FinancialAccountService

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
    fun `getMonthSummary computes missing snapshot without transaction session errors`() {
        val created =
            financialAccountService.create(
                CreateFinancialAccountCommand(
                    type = FinancialAccountType.BANK,
                    subtype = FinancialAccountSubtype.CONTA_CORRENTE,
                    name = "Conta sem snapshot",
                    issuerName = "Banco Teste",
                ),
                accountId = accountId,
                userId = userId,
            )
        val financialAccountId = requireNotNull(created.id)
        val today = LocalDate.now()

        // Guard: no initial balance means no snapshot pre-created for the queried month.
        assertThat(snapshotRepository.findByYearMonth(financialAccountId, today.year, today.monthValue)).isNull()

        val summary =
            monthSummaryQueryService.getMonthSummary(
                financialAccountId,
                accountId,
                today.year,
                today.monthValue,
            )

        assertThat(summary.year).isEqualTo(today.year)
        assertThat(summary.month).isEqualTo(today.monthValue)
        assertThat(summary.openingBalance).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(summary.closingBalance).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(summary.totalIncome).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(summary.totalExpenses).isEqualByComparingTo(BigDecimal.ZERO)

        assertThat(snapshotRepository.findByYearMonth(financialAccountId, today.year, today.monthValue)).isNotNull()
    }
}
