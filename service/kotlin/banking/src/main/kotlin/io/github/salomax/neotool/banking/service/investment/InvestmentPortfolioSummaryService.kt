package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.graphql.investment.AssetGroupSummaryDTO
import io.github.salomax.neotool.banking.graphql.investment.InvestmentHoldingDTO
import io.github.salomax.neotool.banking.graphql.investment.InvestmentPortfolioSummaryDTO
import io.github.salomax.neotool.banking.graphql.investment.PerformancePeriodDTO
import io.github.salomax.neotool.banking.graphql.investment.PortfolioPerformanceDTO
import io.github.salomax.neotool.banking.graphql.investment.RecalculationStatusDTO
import io.github.salomax.neotool.banking.model.dto.investment.AssetGroupMapping
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

@Singleton
class InvestmentPortfolioSummaryService(
    private val investmentQueryService: InvestmentQueryService,
    private val portfolioPerformanceQueryService: PortfolioPerformanceQueryService,
) {
    fun getSummary(
        financialAccountId: UUID?,
        accountId: UUID,
        period: PerformancePeriodDTO = PerformancePeriodDTO.YTD,
    ): InvestmentPortfolioSummaryDTO =
        if (financialAccountId != null) {
            getSummaryForAccount(financialAccountId, accountId, period)
        } else {
            getAggregateSummary(accountId, period)
        }

    private fun getSummaryForAccount(
        financialAccountId: UUID,
        accountId: UUID,
        period: PerformancePeriodDTO,
    ): InvestmentPortfolioSummaryDTO =
        buildSummary(
            financialAccountId = financialAccountId,
            accountId = accountId,
            holdings = investmentQueryService.getHoldings(financialAccountId = financialAccountId, accountId = accountId),
            cashBalance = investmentQueryService.getInvestmentCashBalance(financialAccountId = financialAccountId, accountId = accountId),
            period = period,
        )

    private fun getAggregateSummary(accountId: UUID, period: PerformancePeriodDTO): InvestmentPortfolioSummaryDTO =
        buildSummary(
            financialAccountId = null,
            accountId = accountId,
            holdings = investmentQueryService.getHoldings(financialAccountId = null, accountId = accountId),
            cashBalance = investmentQueryService.getInvestmentCashBalance(financialAccountId = null, accountId = accountId),
            period = period,
        )

    private fun buildSummary(
        financialAccountId: UUID?,
        accountId: UUID,
        holdings: List<InvestmentHoldingDTO>,
        cashBalance: BigDecimal,
        period: PerformancePeriodDTO,
    ): InvestmentPortfolioSummaryDTO {
        val totalCostBasis = holdings.sumOfBigDecimal { it.quantity * it.averageCost }
        val totalGrossValue = holdings.sumOfBigDecimal { it.grossValue ?: 0.0 }
        val totalNetValue = holdings.sumOfBigDecimal { it.netValue ?: it.grossValue ?: 0.0 }
        val totalIncomeTax = holdings.sumOfBigDecimal { it.incomeTax ?: 0.0 }
        val totalIof = holdings.sumOfBigDecimal { it.iof ?: 0.0 }
        val grossGain = totalGrossValue - totalCostBasis
        val netGain = totalNetValue - totalCostBasis
        val totalPortfolioValue = totalGrossValue + cashBalance
        val valuationReferenceDate = holdings.mapNotNull { it.valuationReferenceDate }.maxOrNull()
        val currencyCode =
            holdings.mapNotNull { it.asset?.currencyCode }.distinct().singleOrNull()
                ?: "BRL"

        val groupSummaries =
            buildGroupSummaries(
                financialAccountId = financialAccountId,
                accountId = accountId,
                holdings = holdings,
                cashBalance = cashBalance,
                totalPortfolioValue = totalPortfolioValue,
                period = period,
            )

        return InvestmentPortfolioSummaryDTO(
            totalHoldingsCount = holdings.size,
            totalCostBasis = totalCostBasis.toDouble(),
            totalGrossValue = totalGrossValue.toDouble(),
            totalNetValue = totalNetValue.toDouble(),
            totalIncomeTax = totalIncomeTax.toDouble(),
            totalIof = totalIof.toDouble(),
            grossGain = grossGain.toDouble(),
            grossGainPercent = percentOrZero(grossGain, totalCostBasis),
            netGain = netGain.toDouble(),
            netGainPercent = percentOrZero(netGain, totalCostBasis),
            cashBalance = cashBalance.toDouble(),
            totalPortfolioValue = totalPortfolioValue.toDouble(),
            byAssetGroup = groupSummaries,
            hasEstimatedValues =
                holdings.any {
                    it.valuationSource == ValuationSource.ESTIMATED || it.valuationSource == ValuationSource.UNAVAILABLE
                },
            valuationReferenceDate = valuationReferenceDate,
            currencyCode = currencyCode,
            performance =
                buildPortfolioPerformance(
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    period = period,
                    holdings = holdings,
                ),
            recalculationStatus = buildRecalculationStatus(accountId),
        )
    }

    private fun buildPortfolioPerformance(
        financialAccountId: UUID?,
        accountId: UUID,
        period: PerformancePeriodDTO,
        holdings: List<InvestmentHoldingDTO>,
    ): PortfolioPerformanceDTO =
        portfolioPerformanceQueryService.getPortfolioPerformance(
            financialAccountId = financialAccountId,
            accountId = accountId,
            period = period,
            hasEstimatedValuesFallback =
                holdings.any {
                    it.valuationSource == ValuationSource.ESTIMATED || it.valuationSource == ValuationSource.UNAVAILABLE
                },
        )

    private fun buildRecalculationStatus(accountId: UUID): RecalculationStatusDTO =
        portfolioPerformanceQueryService.getRecalculationStatus(accountId)

    private fun buildGroupSummaries(
        financialAccountId: UUID?,
        accountId: UUID,
        holdings: List<InvestmentHoldingDTO>,
        cashBalance: BigDecimal,
        totalPortfolioValue: BigDecimal,
        period: PerformancePeriodDTO,
    ): List<AssetGroupSummaryDTO> {
        val grouped =
            holdings
                .groupBy { AssetGroupMapping.groupKeyFor(it.asset?.assetType) }
                .map { (groupKey, groupHoldings) ->
                    val totalCostBasis = groupHoldings.sumOfBigDecimal { it.quantity * it.averageCost }
                    val totalGrossValue = groupHoldings.sumOfBigDecimal { it.grossValue ?: 0.0 }
                    AssetGroupSummaryDTO(
                        groupKey = groupKey,
                        label = AssetGroupMapping.labelFor(groupKey),
                        count = groupHoldings.size,
                        totalCostBasis = totalCostBasis.toDouble(),
                        totalGrossValue = totalGrossValue.toDouble(),
                        grossGainPercent = percentOrNull(totalGrossValue - totalCostBasis, totalCostBasis),
                        twr =
                            portfolioPerformanceQueryService.getAssetGroupPerformance(
                                financialAccountId = financialAccountId,
                                accountId = accountId,
                                groupKey = groupKey,
                                period = period,
                            ),
                        allocationPercent = percentOrZero(totalGrossValue, totalPortfolioValue),
                        isCash = false,
                    )
                }

        val cashGroup =
            if (cashBalance.compareTo(BigDecimal.ZERO) == 0) {
                emptyList()
            } else {
                listOf(
                    AssetGroupSummaryDTO(
                        groupKey = AssetGroupMapping.CASH,
                        label = AssetGroupMapping.labelFor(AssetGroupMapping.CASH),
                        count = 0,
                        totalCostBasis = 0.0,
                        totalGrossValue = cashBalance.toDouble(),
                        grossGainPercent = null,
                        twr = null,
                        allocationPercent = percentOrZero(cashBalance, totalPortfolioValue),
                        isCash = true,
                    ),
                )
            }

        return (grouped + cashGroup).sortedByDescending { it.allocationPercent }
    }

    private fun List<InvestmentHoldingDTO>.sumOfBigDecimal(selector: (InvestmentHoldingDTO) -> Double): BigDecimal =
        fold(BigDecimal.ZERO) { acc, holding -> acc + BigDecimal.valueOf(selector(holding)) }

    private fun percentOrZero(
        numerator: BigDecimal,
        denominator: BigDecimal,
    ): Double = percentOrNull(numerator, denominator) ?: 0.0

    private fun percentOrNull(
        numerator: BigDecimal,
        denominator: BigDecimal,
    ): Double? {
        if (denominator.compareTo(BigDecimal.ZERO) == 0) return null
        return numerator.divide(denominator, 8, RoundingMode.HALF_UP).toDouble()
    }
}
