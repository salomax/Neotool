package io.github.salomax.neotool.banking.service.investment.recalculation

import io.github.salomax.neotool.banking.domain.investment.Indexer
import io.github.salomax.neotool.banking.domain.portfolio.InvestmentAssetDailyPrice
import io.github.salomax.neotool.banking.service.investment.DailyGroupValuation
import java.math.BigDecimal
import java.time.LocalDate
import java.util.TreeMap
import java.util.UUID

/**
 * Rolls up per-holding MTM into asset-group buckets for a single [date] (used by portfolio quota recalculation).
 */
fun mergeGroupValuations(
    timelines: List<HistoricalHoldingTimeline>,
    date: LocalDate,
    rateMap: Map<Indexer, BigDecimal>,
    monthlySeries: Map<Indexer, List<Pair<LocalDate, BigDecimal>>>,
    pricesByAsset: Map<UUID, TreeMap<LocalDate, InvestmentAssetDailyPrice>>,
): Map<String, DailyGroupValuation> {
    val holdingsValueByGroup = mutableMapOf<String, DailyGroupValuation>()
    timelines.forEach { timeline ->
        timeline.advanceTo(date)
        val valuation = timeline.valueAt(date, rateMap, monthlySeries, pricesByAsset)
        if (valuation.totalValue <= BigDecimal.ZERO) return@forEach

        val groupKey = timeline.groupKey
        val existing = holdingsValueByGroup[groupKey]
        holdingsValueByGroup[groupKey] =
            if (existing == null) {
                valuation
            } else {
                DailyGroupValuation(
                    totalValue = existing.totalValue + valuation.totalValue,
                    hasEstimatedValues = existing.hasEstimatedValues || valuation.hasEstimatedValues,
                )
            }
    }
    return holdingsValueByGroup
}
