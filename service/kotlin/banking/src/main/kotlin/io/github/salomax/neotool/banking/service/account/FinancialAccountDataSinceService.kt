package io.github.salomax.neotool.banking.service.account

import io.github.salomax.neotool.banking.repo.portfolio.PortfolioQuotaRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.time.LocalDate
import java.util.UUID

/**
 * Computes the earliest available month of data per financial account.
 *
 * Source tables:
 * - banking.transactions (MIN(date), non-deleted rows)
 * - banking.investment_portfolio_quota (MIN(quota_date), PORTFOLIO dimension)
 */
@Singleton
open class FinancialAccountDataSinceService(
    private val transactionRepository: TransactionRepository,
    private val portfolioQuotaRepository: PortfolioQuotaRepository,
) {
    @Transactional(readOnly = true)
    open fun findDataSinceByFinancialAccountIds(financialAccountIds: Collection<UUID>): Map<UUID, LocalDate> {
        if (financialAccountIds.isEmpty()) return emptyMap()
        val ids = financialAccountIds.distinct()

        val earliestTransactionByAccount = transactionRepository.findEarliestTransactionDates(ids)
        val earliestPortfolioByAccount = portfolioQuotaRepository.findEarliestPortfolioDates(ids)

        return ids.mapNotNull { financialAccountId ->
            val earliestDate =
                listOfNotNull(
                    earliestTransactionByAccount[financialAccountId],
                    earliestPortfolioByAccount[financialAccountId],
                ).minOrNull()
            earliestDate?.let { financialAccountId to it }
        }.toMap()
    }
}
