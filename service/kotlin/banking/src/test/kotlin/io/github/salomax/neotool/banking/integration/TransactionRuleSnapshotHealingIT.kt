package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.domain.transaction.CategoryApplicableTo
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.MonthlyBalanceSnapshot
import io.github.salomax.neotool.banking.domain.transaction.PatternType
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionSource
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.model.dto.account.CreateFinancialAccountCommand
import io.github.salomax.neotool.banking.model.dto.transaction.CreateTransactionRuleInput
import io.github.salomax.neotool.banking.repo.transaction.MonthlyBalanceSnapshotRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.github.salomax.neotool.banking.service.account.FinancialAccountService
import io.github.salomax.neotool.banking.service.transaction.MonthSummaryQueryService
import io.github.salomax.neotool.banking.service.transaction.SnapshotComputationService
import io.github.salomax.neotool.banking.service.transaction.TransactionRuleService
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("TransactionRule snapshot healing IT")
class TransactionRuleSnapshotHealingIT : BankingPostgresIntegrationTest() {
    @Inject
    lateinit var financialAccountService: FinancialAccountService

    @Inject
    lateinit var transactionRepository: TransactionRepository

    @Inject
    lateinit var transactionRuleService: TransactionRuleService

    @Inject
    lateinit var snapshotRepository: MonthlyBalanceSnapshotRepository

    @Inject
    lateinit var snapshotComputationService: SnapshotComputationService

    @Inject
    lateinit var monthSummaryQueryService: MonthSummaryQueryService

    @PersistenceContext
    lateinit var entityManager: EntityManager

    private lateinit var accountId: UUID
    private lateinit var userId: UUID

    @BeforeEach
    fun initContextIds() {
        accountId = UUID.randomUUID()
        userId = UUID.randomUUID()
    }

    @Test
    fun `applyToHistorical marks snapshot dirty and monthSummary heals transfer reclassification`() {
        val financialAccountId = createBankAccount()
        val affectedMonth = YearMonth.now().minusMonths(2)

        transactionRepository.save(
            transaction(
                financialAccountId = financialAccountId,
                amount = BigDecimal("80.00"),
                date = affectedMonth.atDay(10),
                description = "Savings transfer",
                type = TransactionType.OTHER_EXPENSE,
            ),
        )
        snapshotComputationService.recomputeChain(
            financialAccountId = financialAccountId,
            accountId = accountId,
            fromYear = affectedMonth.year,
            fromMonth = affectedMonth.monthValue,
        )

        val preRuleSnapshot =
            requireNotNull(
                snapshotRepository.findByYearMonth(financialAccountId, affectedMonth.year, affectedMonth.monthValue),
            )
        assertThat(preRuleSnapshot.isDirty).isFalse()
        assertThat(preRuleSnapshot.totalExpenses).isEqualByComparingTo("80.00")

        val rule =
            transactionRuleService.create(
                CreateTransactionRuleInput(
                    typeOverride = CategoryApplicableTo.TRANSFER,
                    pattern = "Savings transfer",
                    patternType = PatternType.CONTAINS,
                    applicableTo = CategoryApplicableTo.EXPENSE,
                ),
                accountId,
            )

        val result = transactionRuleService.applyToHistorical(requireNotNull(rule.id), accountId)

        assertThat(result.affectedCount).isEqualTo(1)
        assertThat(
            requireNotNull(
                snapshotRepository.findByYearMonth(financialAccountId, affectedMonth.year, affectedMonth.monthValue),
            ).isDirty,
        ).isTrue()

        val summary =
            monthSummaryQueryService.getMonthSummary(
                financialAccountId,
                accountId,
                affectedMonth.year,
                affectedMonth.monthValue,
            )

        assertThat(summary.totalExpenses).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(summary.transfersOut).isEqualByComparingTo("80.00")
        assertThat(summary.closingBalance).isEqualByComparingTo("-80.00")
        assertThat(
            requireNotNull(
                snapshotRepository.findByYearMonth(financialAccountId, affectedMonth.year, affectedMonth.monthValue),
            ).isDirty,
        ).isFalse()
    }

    @Test
    fun `remediation migration marks stale transfer snapshot dirty and read path heals it`() {
        val financialAccountId = createBankAccount()
        val affectedMonth = YearMonth.now().minusMonths(3)

        transactionRepository.save(
            transaction(
                financialAccountId = financialAccountId,
                amount = BigDecimal("55.00"),
                date = affectedMonth.atDay(12),
                description = "Own account transfer",
                type = TransactionType.TRANSFER,
            ),
        )
        snapshotRepository.upsert(
            MonthlyBalanceSnapshot(
                financialAccountId = financialAccountId,
                accountId = accountId,
                year = affectedMonth.year,
                month = affectedMonth.monthValue,
                openingBalance = BigDecimal.ZERO,
                closingBalance = BigDecimal("-55.00"),
                totalIncome = BigDecimal.ZERO,
                totalExpenses = BigDecimal("55.00"),
                isDirty = false,
                computedAt = Instant.now(),
            ),
        )

        assertThat(readSnapshotDirtyFlag(financialAccountId, affectedMonth.year, affectedMonth.monthValue)).isFalse()

        runMigration("V0_28__mark_snapshots_dirty_for_rule_reclassification_fix.sql")
        entityManager.clear()

        assertThat(readSnapshotDirtyFlag(financialAccountId, affectedMonth.year, affectedMonth.monthValue)).isTrue()

        val summary =
            monthSummaryQueryService.getMonthSummary(
                financialAccountId,
                accountId,
                affectedMonth.year,
                affectedMonth.monthValue,
            )

        assertThat(summary.totalExpenses).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(summary.transfersOut).isEqualByComparingTo("55.00")
        assertThat(summary.closingBalance).isEqualByComparingTo("-55.00")
        assertThat(
            requireNotNull(
                snapshotRepository.findByYearMonth(financialAccountId, affectedMonth.year, affectedMonth.monthValue),
            ).isDirty,
        ).isFalse()
    }

    private fun createBankAccount(): UUID =
        requireNotNull(
            financialAccountService.create(
                CreateFinancialAccountCommand(
                    type = FinancialAccountType.BANK,
                    subtype = FinancialAccountSubtype.CONTA_CORRENTE,
                    name = "Conta teste",
                    issuerName = "Banco Teste",
                ),
                accountId = accountId,
                userId = userId,
            ).id,
        )

    private fun transaction(
        financialAccountId: UUID,
        amount: BigDecimal,
        date: java.time.LocalDate,
        description: String,
        type: TransactionType,
    ) = Transaction(
        financialAccountId = financialAccountId,
        accountId = accountId,
        createdByUserId = userId,
        creditDebitType = CreditDebitType.DEBIT,
        type = type,
        status = TransactionStatus.POSTED,
        source = TransactionSource.OFX,
        amount = amount,
        date = date,
        description = description,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    private fun runMigration(fileName: String) {
        val sql =
            requireNotNull(javaClass.classLoader.getResourceAsStream("db/migration/$fileName")) {
                "Migration $fileName not found"
            }.bufferedReader().use { it.readText() }
        entityManager.flush()
        entityManager.createNativeQuery(sql).executeUpdate()
        entityManager.flush()
    }

    private fun readSnapshotDirtyFlag(
        financialAccountId: UUID,
        year: Int,
        month: Int,
    ): Boolean {
        entityManager.clear()
        val value =
            entityManager.createNativeQuery(
                """
                SELECT is_dirty
                FROM banking.monthly_balance_snapshots
                WHERE financial_account_id = CAST(:financialAccountId AS uuid)
                  AND year = :year
                  AND month = :month
                """.trimIndent(),
            )
                .setParameter("financialAccountId", financialAccountId.toString())
                .setParameter("year", year)
                .setParameter("month", month)
                .resultList
                .firstOrNull()
                ?: error("Snapshot not found for financialAccountId=$financialAccountId year=$year month=$month")

        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            else -> value.toString().toBooleanStrict()
        }
    }
}
