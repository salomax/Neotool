package io.github.salomax.neotool.banking.service.investment.recalculation

import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.HoldingPricingMode
import io.github.salomax.neotool.banking.domain.investment.Indexer
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.domain.portfolio.InvestmentAssetDailyPrice
import io.github.salomax.neotool.banking.model.dto.investment.AssetGroupMapping
import io.github.salomax.neotool.banking.service.investment.DailyGroupValuation
import io.github.salomax.neotool.banking.service.investment.FixedIncomeValuationService
import io.github.salomax.neotool.banking.service.investment.ValuationSource
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TreeMap
import java.util.UUID

/**
 * Replays [transactions] in trade-date order to simulate a holding's quantity/cost through time, then prices
 * the position for quota / TWR recalculation.
 */
class HistoricalHoldingTimeline(
    val baseHolding: Holding,
    val asset: Asset,
    val transactions: List<InvestmentTransaction>,
    private val fixedIncomeValuationService: FixedIncomeValuationService,
) {
    val groupKey: String = AssetGroupMapping.groupKeyFor(asset.assetType)

    private var cursor: Int = 0
    private var currentState: Holding =
        if (transactions.isEmpty()) {
            baseHolding
        } else {
            baseHolding.copy(quantity = BigDecimal.ZERO, averageCost = BigDecimal.ZERO)
        }

    fun advanceTo(date: LocalDate) {
        while (cursor < transactions.size && !transactions[cursor].tradeDate.isAfter(date)) {
            val day = transactions[cursor].tradeDate
            val dayTransactions = mutableListOf<InvestmentTransaction>()
            while (cursor < transactions.size && transactions[cursor].tradeDate == day) {
                dayTransactions += transactions[cursor]
                cursor += 1
            }

            // Normalize same-day cost-adding inflows into one atomic position update to avoid
            // permutation-dependent intermediate averageCost states during replay.
            val normalizedInputs = normalizeDayInputs(dayTransactions)
            normalizedInputs.forEach { input ->
                currentState = HoldingPositionEvolution.evolve(currentState, input)
            }
        }
    }

    private fun normalizeDayInputs(dayTransactions: List<InvestmentTransaction>): List<PositionEvolutionInput> {
        val orderedDayTransactions =
            dayTransactions.sortedWith(
                compareBy<InvestmentTransaction>(
                    { it.createdAt },
                    { it.id.toString() },
                    { it.type.name },
                    { it.direction.name },
                ),
            )

        val (costAddingTransactions, passthroughTransactions) =
            orderedDayTransactions.partition { it.isCostAddingInflow() }

        val aggregatedCostAddingInput = aggregateCostAddingInputs(costAddingTransactions)
        return buildList {
            if (aggregatedCostAddingInput != null) add(aggregatedCostAddingInput)
            passthroughTransactions.mapTo(this, PositionEvolutionInput::from)
        }
    }

    private fun aggregateCostAddingInputs(transactions: List<InvestmentTransaction>): PositionEvolutionInput? {
        if (transactions.isEmpty()) return null
        if (transactions.size == 1) return PositionEvolutionInput.from(transactions.first())

        val totalQuantity = transactions.fold(BigDecimal.ZERO) { acc, tx -> acc + requireNotNull(tx.quantity) }

        val weightedCost =
            transactions.fold(BigDecimal.ZERO) { acc, tx ->
                acc + requireNotNull(tx.quantity).multiply(requireNotNull(tx.unitPrice), MATH_CONTEXT)
            }
        val weightedUnitPrice = weightedCost.divide(totalQuantity, MATH_CONTEXT)
        val firstType = transactions.first().type
        val aggregatedType =
            if (transactions.all { it.type == firstType }) {
                firstType
            } else {
                InvestmentTransactionType.PURCHASE
            }

        return PositionEvolutionInput(
            type = aggregatedType,
            direction = InvestmentTransactionDirection.INFLOW,
            quantity = totalQuantity,
            unitPrice = weightedUnitPrice,
        )
    }

    private fun InvestmentTransaction.isCostAddingInflow(): Boolean {
        if (direction != InvestmentTransactionDirection.INFLOW) return false
        if (quantity == null || unitPrice == null) return false
        if (quantity <= BigDecimal.ZERO || unitPrice <= BigDecimal.ZERO) return false
        return type in COST_ADDING_INFLOW_TYPES
    }

    fun valueAt(
        date: LocalDate,
        rateMap: Map<Indexer, BigDecimal>,
        monthlySeries: Map<Indexer, List<Pair<LocalDate, BigDecimal>>>,
        pricesByAsset: Map<UUID, TreeMap<LocalDate, InvestmentAssetDailyPrice>>,
    ): DailyGroupValuation {
        if (currentState.quantity <= BigDecimal.ZERO) {
            return DailyGroupValuation(BigDecimal.ZERO, false)
        }

        val priceMap = pricesByAsset[asset.id!!]
        val exactPrice = priceMap?.get(date)
        if (exactPrice != null) {
            return DailyGroupValuation(
                totalValue =
                    currentState.quantity.multiply(
                        exactPrice.closePrice,
                        MATH_CONTEXT,
                    ).multiply(asset.priceFactor, MATH_CONTEXT),
                hasEstimatedValues = false,
            )
        }

        val fallbackPrice = priceMap?.floorEntry(date)?.value
        if (fallbackPrice != null) {
            return DailyGroupValuation(
                totalValue =
                    currentState.quantity.multiply(
                        fallbackPrice.closePrice,
                        MATH_CONTEXT,
                    ).multiply(asset.priceFactor, MATH_CONTEXT),
                hasEstimatedValues = true,
            )
        }

        if (asset.assetType in FIXED_INCOME_ASSET_TYPES) {
            val result =
                fixedIncomeValuationService.estimate(
                    holding = currentState,
                    assetType = asset.assetType,
                    indicatorRates = rateMap,
                    valuationReferenceDate = date,
                    monthlySeries = monthlySeries,
                    asOfDate = date,
                    allowRateProjectionPastSeries = true,
                )
            result.grossValue?.let {
                return DailyGroupValuation(
                    totalValue = it,
                    hasEstimatedValues = result.valuationSource == ValuationSource.ESTIMATED,
                )
            }
        }

        val manualGrossValue = currentState.manualGrossValue
        val manualValuedAt = currentState.manualValuedAt
        if (
            currentState.pricingMode == HoldingPricingMode.MANUAL &&
            manualGrossValue != null &&
            (manualValuedAt == null || !manualValuedAt.isAfter(date))
        ) {
            return DailyGroupValuation(totalValue = manualGrossValue, hasEstimatedValues = true)
        }

        val providerDate = currentState.lastValuedAt?.atZone(ZoneOffset.UTC)?.toLocalDate()
        val providerGrossValue = currentState.grossValue
        if (providerGrossValue != null && providerDate != null && !providerDate.isAfter(date)) {
            return DailyGroupValuation(totalValue = providerGrossValue, hasEstimatedValues = true)
        }

        return DailyGroupValuation(
            totalValue = currentState.quantity.multiply(currentState.averageCost, MATH_CONTEXT),
            hasEstimatedValues = true,
        )
    }

    private companion object {
        val COST_ADDING_INFLOW_TYPES: Set<InvestmentTransactionType> =
            setOf(
                InvestmentTransactionType.PURCHASE,
                InvestmentTransactionType.SUBSCRIPTION,
                InvestmentTransactionType.CUSTODY_TRANSFER,
                InvestmentTransactionType.OWNERSHIP_TRANSFER,
                InvestmentTransactionType.QUOTA_TRANSFER,
            )
        val FIXED_INCOME_ASSET_TYPES: Set<AssetType> =
            setOf(
                AssetType.CDB,
                AssetType.RDB,
                AssetType.LCI,
                AssetType.LCA,
                AssetType.LC,
                AssetType.LF,
                AssetType.LIG,
                AssetType.DEBENTURES,
                AssetType.CRI,
                AssetType.CRA,
                AssetType.TREASURY,
            )
        val MATH_CONTEXT: MathContext = MathContext(12, RoundingMode.HALF_UP)
    }
}
