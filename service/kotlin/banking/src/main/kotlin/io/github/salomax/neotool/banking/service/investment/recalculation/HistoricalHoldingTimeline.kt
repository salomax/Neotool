package io.github.salomax.neotool.banking.service.investment.recalculation

import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.HoldingPricingMode
import io.github.salomax.neotool.banking.domain.investment.Indexer
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.portfolio.InvestmentAssetDailyPrice
import io.github.salomax.neotool.banking.service.investment.AssetGroupMapping
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
        if (transactions.isEmpty()) baseHolding
        else baseHolding.copy(quantity = BigDecimal.ZERO, averageCost = BigDecimal.ZERO)

    fun advanceTo(date: LocalDate) {
        while (cursor < transactions.size && !transactions[cursor].tradeDate.isAfter(date)) {
            currentState =
                HoldingPositionEvolution.evolve(
                    currentState,
                    PositionEvolutionInput.from(transactions[cursor]),
                )
            cursor += 1
        }
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
                totalValue = currentState.quantity.multiply(exactPrice.closePrice, MATH_CONTEXT).multiply(asset.priceFactor, MATH_CONTEXT),
                hasEstimatedValues = false,
            )
        }

        val fallbackPrice = priceMap?.floorEntry(date)?.value
        if (fallbackPrice != null) {
            return DailyGroupValuation(
                totalValue = currentState.quantity.multiply(fallbackPrice.closePrice, MATH_CONTEXT).multiply(asset.priceFactor, MATH_CONTEXT),
                hasEstimatedValues = true,
            )
        }

        if (asset.assetType in FIXED_INCOME_ASSET_TYPES) {
            val result =
                fixedIncomeValuationService.estimate(
                    holding = currentState,
                    indicatorRates = rateMap,
                    valuationReferenceDate = date,
                    monthlySeries = monthlySeries,
                    asOfDate = date,
                    allowRateProjectionPastSeries = true,
                )
            result.grossValue?.let {
                return DailyGroupValuation(totalValue = it, hasEstimatedValues = result.valuationSource == ValuationSource.ESTIMATED)
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
