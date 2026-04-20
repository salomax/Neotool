package io.github.salomax.neotool.banking.service.investment.recalculation

import io.github.salomax.neotool.banking.domain.investment.Indexer
import io.github.salomax.neotool.banking.domain.portfolio.InvestmentAssetDailyPrice
import io.github.salomax.neotool.banking.service.investment.DailyGroupValuation
import io.github.salomax.neotool.banking.model.dto.investment.GridClassMapping
import java.math.BigDecimal
import java.time.LocalDate
import java.util.TreeMap
import java.util.UUID

data class DailyDimensionValuation(
    val holdingsValueByGroup: Map<String, DailyGroupValuation>,
    val holdingsValueByGridClass: Map<String, DailyGroupValuation>,
)

/**
 * Advances each timeline once and computes group + grid-class valuation maps in a single pass.
 */
fun mergeDimensionValuations(
    timelines: List<HistoricalHoldingTimeline>,
    date: LocalDate,
    rateMap: Map<Indexer, BigDecimal>,
    monthlySeries: Map<Indexer, List<Pair<LocalDate, BigDecimal>>>,
    pricesByAsset: Map<UUID, TreeMap<LocalDate, InvestmentAssetDailyPrice>>,
): DailyDimensionValuation {
    val byGroup = mutableMapOf<String, DailyGroupValuation>()
    val byGridClass = mutableMapOf<String, DailyGroupValuation>()

    timelines.forEach { timeline ->
        timeline.advanceTo(date)
        val valuation = timeline.valueAt(date, rateMap, monthlySeries, pricesByAsset)
        if (valuation.totalValue <= BigDecimal.ZERO) return@forEach

        upsertValuation(byGroup, timeline.groupKey, valuation)
        GridClassMapping.gridClassFor(timeline.asset.assetType)?.let { gridClassKey ->
            upsertValuation(byGridClass, gridClassKey, valuation)
        }
    }

    return DailyDimensionValuation(
        holdingsValueByGroup = byGroup,
        holdingsValueByGridClass = byGridClass,
    )
}

private fun upsertValuation(
    container: MutableMap<String, DailyGroupValuation>,
    key: String,
    valuation: DailyGroupValuation,
) {
    val existing = container[key]
    container[key] =
        if (existing == null) {
            valuation
        } else {
            DailyGroupValuation(
                totalValue = existing.totalValue + valuation.totalValue,
                hasEstimatedValues = existing.hasEstimatedValues || valuation.hasEstimatedValues,
            )
        }
}
