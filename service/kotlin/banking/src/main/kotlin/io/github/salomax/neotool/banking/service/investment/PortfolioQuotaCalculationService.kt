package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.portfolio.InvestmentPortfolioFlowEvent
import io.github.salomax.neotool.banking.domain.portfolio.InvestmentPortfolioQuota
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioDimensionType
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class DailyGroupValuation(
    val totalValue: BigDecimal,
    val hasEstimatedValues: Boolean,
)

@Singleton
class PortfolioQuotaCalculationService {
    fun calculateDayQuota(
        financialAccountId: UUID,
        date: LocalDate,
        previousPortfolioQuota: InvestmentPortfolioQuota?,
        previousGroupQuotas: Map<String, InvestmentPortfolioQuota>,
        holdingsValueByGroup: Map<String, DailyGroupValuation>,
        flowEvents: List<InvestmentPortfolioFlowEvent>,
    ): List<InvestmentPortfolioQuota> {
        val now = Instant.now()
        val results = mutableListOf<InvestmentPortfolioQuota>()
        val portfolioValueEnd = holdingsValueByGroup.values.fold(BigDecimal.ZERO) { acc, item -> acc + item.totalValue }

        val portfolioQuota =
            calculatePortfolioQuota(
                financialAccountId = financialAccountId,
                date = date,
                previousQuota = previousPortfolioQuota,
                portfolioValueEnd = portfolioValueEnd,
                hasEstimatedValues = holdingsValueByGroup.values.any { it.hasEstimatedValues },
                flowEvents = flowEvents,
                now = now,
            )
        if (portfolioQuota != null) {
            results += portfolioQuota
        }

        holdingsValueByGroup.forEach { (groupKey, valuation) ->
            calculateGroupQuota(
                financialAccountId = financialAccountId,
                date = date,
                groupKey = groupKey,
                previousQuota = previousGroupQuotas[groupKey],
                totalValueEnd = valuation.totalValue,
                hasEstimatedValues = valuation.hasEstimatedValues,
                now = now,
            )?.let(results::add)
        }

        return results
    }

    private fun calculatePortfolioQuota(
        financialAccountId: UUID,
        date: LocalDate,
        previousQuota: InvestmentPortfolioQuota?,
        portfolioValueEnd: BigDecimal,
        hasEstimatedValues: Boolean,
        flowEvents: List<InvestmentPortfolioFlowEvent>,
        now: Instant,
    ): InvestmentPortfolioQuota? {
        if (previousQuota == null) {
            if (portfolioValueEnd <= BigDecimal.ZERO) return null
            return InvestmentPortfolioQuota(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                quotaDate = date,
                quotaValue = INITIAL_QUOTA_VALUE,
                totalQuotas = portfolioValueEnd.setScale(QUOTA_SCALE, RoundingMode.HALF_UP),
                totalPortfolioValue = portfolioValueEnd.setScale(VALUE_SCALE, RoundingMode.HALF_UP),
                hasEstimatedValues = hasEstimatedValues,
                createdAt = now,
                updatedAt = now,
            )
        }

        if (previousQuota.totalPortfolioValue <= BigDecimal.ZERO) {
            if (portfolioValueEnd <= BigDecimal.ZERO) return null
            return InvestmentPortfolioQuota(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                quotaDate = date,
                quotaValue = INITIAL_QUOTA_VALUE,
                totalQuotas = portfolioValueEnd.setScale(QUOTA_SCALE, RoundingMode.HALF_UP),
                totalPortfolioValue = portfolioValueEnd.setScale(VALUE_SCALE, RoundingMode.HALF_UP),
                hasEstimatedValues = hasEstimatedValues,
                createdAt = now,
                updatedAt = now,
            )
        }

        val netFlows = flowEvents.fold(BigDecimal.ZERO) { acc, event -> acc + signedFlowAmount(event) }
        val dailyReturn =
            portfolioValueEnd
                .subtract(previousQuota.totalPortfolioValue)
                .subtract(netFlows)
                .divide(previousQuota.totalPortfolioValue, QUOTA_SCALE, RoundingMode.HALF_UP)
        val quotaValue =
            previousQuota.quotaValue
                .multiply(BigDecimal.ONE + dailyReturn)
                .max(BigDecimal.ZERO)
                .setScale(QUOTA_SCALE, RoundingMode.HALF_UP)
        val totalQuotas =
            previousQuota.totalQuotas
                .add(flowEvents.fold(BigDecimal.ZERO) { acc, event -> acc + event.quotasTransacted })
                .max(BigDecimal.ZERO)
                .setScale(QUOTA_SCALE, RoundingMode.HALF_UP)

        return InvestmentPortfolioQuota(
            financialAccountId = financialAccountId,
            dimensionType = PortfolioDimensionType.PORTFOLIO,
            dimensionKey = null,
            quotaDate = date,
            quotaValue = quotaValue,
            totalQuotas = totalQuotas,
            totalPortfolioValue = portfolioValueEnd.setScale(VALUE_SCALE, RoundingMode.HALF_UP),
            hasEstimatedValues = hasEstimatedValues,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun calculateGroupQuota(
        financialAccountId: UUID,
        date: LocalDate,
        groupKey: String,
        previousQuota: InvestmentPortfolioQuota?,
        totalValueEnd: BigDecimal,
        hasEstimatedValues: Boolean,
        now: Instant,
    ): InvestmentPortfolioQuota? {
        if (totalValueEnd <= BigDecimal.ZERO) return null

        if (previousQuota == null || previousQuota.totalPortfolioValue <= BigDecimal.ZERO) {
            return InvestmentPortfolioQuota(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.ASSET_GROUP,
                dimensionKey = groupKey,
                quotaDate = date,
                quotaValue = INITIAL_QUOTA_VALUE,
                totalQuotas = totalValueEnd.setScale(QUOTA_SCALE, RoundingMode.HALF_UP),
                totalPortfolioValue = totalValueEnd.setScale(VALUE_SCALE, RoundingMode.HALF_UP),
                hasEstimatedValues = hasEstimatedValues,
                createdAt = now,
                updatedAt = now,
            )
        }

        val dailyReturn =
            totalValueEnd
                .subtract(previousQuota.totalPortfolioValue)
                .divide(previousQuota.totalPortfolioValue, QUOTA_SCALE, RoundingMode.HALF_UP)
        val quotaValue =
            previousQuota.quotaValue
                .multiply(BigDecimal.ONE + dailyReturn)
                .max(BigDecimal.ZERO)
                .setScale(QUOTA_SCALE, RoundingMode.HALF_UP)

        return InvestmentPortfolioQuota(
            financialAccountId = financialAccountId,
            dimensionType = PortfolioDimensionType.ASSET_GROUP,
            dimensionKey = groupKey,
            quotaDate = date,
            quotaValue = quotaValue,
            totalQuotas = previousQuota.totalQuotas.setScale(QUOTA_SCALE, RoundingMode.HALF_UP),
            totalPortfolioValue = totalValueEnd.setScale(VALUE_SCALE, RoundingMode.HALF_UP),
            hasEstimatedValues = hasEstimatedValues,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun signedFlowAmount(event: InvestmentPortfolioFlowEvent): BigDecimal =
        when (event.eventType) {
            io.github.salomax.neotool.banking.domain.portfolio.PortfolioFlowEventType.DEPOSIT,
            io.github.salomax.neotool.banking.domain.portfolio.PortfolioFlowEventType.DIVIDEND,
            io.github.salomax.neotool.banking.domain.portfolio.PortfolioFlowEventType.CORPORATE_ACTION,
            -> event.grossAmount
            io.github.salomax.neotool.banking.domain.portfolio.PortfolioFlowEventType.WITHDRAWAL,
            io.github.salomax.neotool.banking.domain.portfolio.PortfolioFlowEventType.INCOME_TAX,
            -> event.grossAmount.negate()
        }

    private companion object {
        val INITIAL_QUOTA_VALUE: BigDecimal = BigDecimal.ONE.setScale(8, RoundingMode.HALF_UP)
        const val QUOTA_SCALE = 8
        const val VALUE_SCALE = 2
    }
}
