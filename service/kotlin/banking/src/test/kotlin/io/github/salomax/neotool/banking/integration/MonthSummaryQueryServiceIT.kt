package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.banking.domain.account.CreateFinancialAccountCommand
import io.github.salomax.neotool.banking.service.account.FinancialAccountService
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.service.transaction.SnapshotComputationService
import io.github.salomax.neotool.banking.service.transaction.MonthSummaryQueryService
import io.github.salomax.neotool.banking.repo.transaction.MonthlyBalanceSnapshotRepository
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.github.salomax.neotool.banking.domain.transaction.TransactionSource
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("MonthSummaryQueryService IT")
open class MonthSummaryQueryServiceIT : BankingPostgresIntegrationTest() {
    @Inject
    lateinit var monthSummaryQueryService: MonthSummaryQueryService

    @Inject
    lateinit var financialAccountService: FinancialAccountService

    @Inject
    lateinit var snapshotRepository: MonthlyBalanceSnapshotRepository

    @Inject
    lateinit var transactionRepository: TransactionRepository

    @Inject
    lateinit var snapshotComputationService: SnapshotComputationService

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

    @Test
    @Transactional
    open fun `recomputeChain sees newly saved transaction in the same transaction`() {
        val created =
            financialAccountService.create(
                CreateFinancialAccountCommand(
                    type = FinancialAccountType.BANK,
                    subtype = FinancialAccountSubtype.CONTA_CORRENTE,
                    name = "Conta com movimento",
                    issuerName = "Banco Teste",
                ),
                accountId = accountId,
                userId = userId,
            )
        val financialAccountId = requireNotNull(created.id)
        val today = LocalDate.now()

        transactionRepository.save(
            Transaction(
                financialAccountId = financialAccountId,
                accountId = accountId,
                createdByUserId = userId,
                creditDebitType = CreditDebitType.CREDIT,
                type = TransactionType.OTHER_INCOME,
                status = TransactionStatus.POSTED,
                source = TransactionSource.CSV,
                amount = BigDecimal("123.45"),
                date = today,
                description = "Credito importado",
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            ),
        )

        snapshotComputationService.recomputeChain(
            financialAccountId = financialAccountId,
            accountId = accountId,
            fromYear = today.year,
            fromMonth = today.monthValue,
        )

        val summary =
            monthSummaryQueryService.getMonthSummary(
                financialAccountId,
                accountId,
                today.year,
                today.monthValue,
            )

        assertThat(summary.totalIncome).isEqualByComparingTo("123.45")
        assertThat(summary.closingBalance).isEqualByComparingTo("123.45")
        assertThat(summary.openingBalance).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(snapshotRepository.findByYearMonth(financialAccountId, today.year, today.monthValue)).isNotNull()
    }

    @Test
    fun `requesting a later month backfills snapshots from the earliest transaction month`() {
        val created =
            financialAccountService.create(
                CreateFinancialAccountCommand(
                    type = FinancialAccountType.BANK,
                    subtype = FinancialAccountSubtype.CONTA_CORRENTE,
                    name = "Conta historica",
                    issuerName = "Banco Teste",
                ),
                accountId = accountId,
                userId = userId,
            )
        val financialAccountId = requireNotNull(created.id)
        val importedMonth = YearMonth.now().minusMonths(14)
        val viewedMonth = YearMonth.now().minusMonths(2)

        transactionRepository.save(
            Transaction(
                financialAccountId = financialAccountId,
                accountId = accountId,
                createdByUserId = userId,
                creditDebitType = CreditDebitType.CREDIT,
                type = TransactionType.OTHER_INCOME,
                status = TransactionStatus.POSTED,
                source = TransactionSource.CSV,
                amount = BigDecimal("321.00"),
                date = importedMonth.atDay(15),
                description = "Importado no passado",
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            ),
        )

        val summary =
            monthSummaryQueryService.getMonthSummary(
                financialAccountId,
                accountId,
                viewedMonth.year,
                viewedMonth.monthValue,
            )

        assertThat(summary.closingBalance).isEqualByComparingTo("321.00")
        assertThat(summary.openingBalance).isEqualByComparingTo("321.00")
        assertThat(snapshotRepository.findByYearMonth(financialAccountId, importedMonth.year, importedMonth.monthValue)).isNotNull()
        assertThat(snapshotRepository.findByYearMonth(financialAccountId, viewedMonth.year, viewedMonth.monthValue)).isNotNull()
    }

    @Test
    fun `getMonthSummary for prior month succeeds when all activity is in the current month`() {
        val today = LocalDate.now()
        val prev = YearMonth.from(today).minusMonths(1)
        val created =
            financialAccountService.create(
                CreateFinancialAccountCommand(
                    type = FinancialAccountType.BANK,
                    subtype = FinancialAccountSubtype.CONTA_CORRENTE,
                    name = "Conta saldo inicial hoje",
                    issuerName = "Banco Teste",
                    initialBalance = BigDecimal("1000.00"),
                    initialBalanceDate = today,
                ),
                accountId = accountId,
                userId = userId,
            )
        val financialAccountId = requireNotNull(created.id)

        val summary =
            monthSummaryQueryService.getMonthSummary(
                financialAccountId,
                accountId,
                prev.year,
                prev.monthValue,
            )

        assertThat(summary.year).isEqualTo(prev.year)
        assertThat(summary.month).isEqualTo(prev.monthValue)
        assertThat(summary.totalIncome).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(summary.totalExpenses).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(snapshotRepository.findByYearMonth(financialAccountId, prev.year, prev.monthValue)).isNotNull()
    }
}
