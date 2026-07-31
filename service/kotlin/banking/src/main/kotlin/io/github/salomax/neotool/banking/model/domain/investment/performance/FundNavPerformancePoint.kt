package io.github.salomax.neotool.banking.model.domain.investment.performance

import io.github.salomax.neotool.banking.domain.investment.ComeCotasCheckpoint
import io.github.salomax.neotool.banking.domain.investment.FundMarketNav
import io.github.salomax.neotool.banking.domain.investment.FundMarketSnapshot
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.graphql.investment.HoldingPerformanceResolution
import io.github.salomax.neotool.banking.model.dto.investment.FundValuationResult
import io.github.salomax.neotool.banking.service.investment.ComeCotasSupport
import io.github.salomax.neotool.banking.service.investment.FundValuationService
import io.github.salomax.neotool.banking.service.investment.OpenLot
import io.github.salomax.neotool.banking.service.investment.ValuationSource
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

// ── Domain model ─────────────────────────────────────────────────────────────

internal data class FundNavPerformancePoint(
    val timestamp: Instant,
    val investedPrincipal: BigDecimal?,
    val netContributions: BigDecimal?,
    val currentCostBasis: BigDecimal?,
    val grossValue: BigDecimal?,
    val netValue: BigDecimal?,
    val incomeTax: BigDecimal?,
    val iof: BigDecimal?,
    val grossGain: BigDecimal?,
    val netGain: BigDecimal?,
    val grossGainPercent: BigDecimal?,
    val quantity: BigDecimal?,
    val valuationSource: ValuationSource,
    val valuationReferenceDate: LocalDate?,
    /** Funds never project — invariant enforced here, not at call sites. */
    val isProjected: Boolean = false,
)

// ── Public entry point ────────────────────────────────────────────────────────

/**
 * Builds daily or monthly performance points for a NAV-priced fund holding.
 *
 * One point per month-end from the first transaction date to [today] (or maturity,
 * whichever comes first). Points for months where no NAV is available are skipped.
 * No projection is ever produced — funds are purely historical.
 */
internal fun buildFundNavPerformancePoints(
    holding: Holding,
    transactions: List<InvestmentTransaction>,
    navSeries: List<Pair<LocalDate, BigDecimal>>,
    taxSnapshot: FundMarketSnapshot,
    fundValuationService: FundValuationService,
    checkpoints: List<ComeCotasCheckpoint> = emptyList(),
    resolution: HoldingPerformanceResolution = HoldingPerformanceResolution.MONTH,
    fromDate: LocalDate? = null,
    toDate: LocalDate? = null,
    today: LocalDate,
    mathContext: MathContext = MathContext(12, RoundingMode.HALF_UP),
): List<FundNavPerformancePoint> {
    if (transactions.isEmpty() || navSeries.isEmpty()) return emptyList()

    val nav = NavSeries(navSeries)
    val chronologicalTx = transactions.sortedWith(ComeCotasSupport.transactionOrdering())
    val historicalEndDate = minOf(holding.maturityDate ?: today, today)
    val startDate = chronologicalTx.first().tradeDate
    val windowEnd = minOf(toDate ?: historicalEndDate, historicalEndDate)
    val windowStart = maxOf(fromDate ?: startDate, startDate)
    if (windowEnd.isBefore(windowStart)) return emptyList()

    val pointDates = performanceDatesForResolution(windowStart, windowEnd, resolution, navSeries)
    if (pointDates.isEmpty()) return emptyList()

    var txIndex = 0
    var state = holding.asEmptyPerformanceState()
    val openLots = ArrayDeque<OpenLot>()
    var investedPrincipal = BigDecimal.ZERO
    var netContributions = BigDecimal.ZERO

    return buildList {
        for (pointDate in pointDates) {
            // Advance the transaction cursor up to and including pointDate — O(n) total.
            while (txIndex < chronologicalTx.size &&
                !chronologicalTx[txIndex].tradeDate.isAfter(pointDate)
            ) {
                val tx = chronologicalTx[txIndex++]
                state = state.applyPerformanceTransaction(tx)
                ComeCotasSupport.applyTransaction(openLots, tx)
                when {
                    tx.isCapitalContributionForPerformance() -> {
                        investedPrincipal += tx.grossValue
                        netContributions += tx.grossValue
                    }
                    tx.isCapitalWithdrawalForPerformance() -> {
                        investedPrincipal = (investedPrincipal - tx.grossValue).max(BigDecimal.ZERO)
                        netContributions -= tx.grossValue
                    }
                }
            }

            val costBasis = state.quantity.multiply(state.averageCost, mathContext)
            val commonFields = CommonFields(pointDate, investedPrincipal, netContributions, costBasis)

            if (state.quantity <= BigDecimal.ZERO || costBasis <= BigDecimal.ZERO) {
                add(emptyPositionPoint(commonFields, nav.onOrBefore(pointDate)))
                continue
            }

            val navAtPoint = nav.onOrBefore(pointDate) ?: continue // no NAV yet — skip point

            val valuation =
                runCatching {
                    fundValuationService.estimate(
                        state,
                        taxSnapshot.withNav(navAtPoint),
                        chronologicalTx.subList(0, txIndex),
                        checkpoints = checkpoints,
                        precomputedOpenLots = openLots,
                    )
                }.getOrNull() ?: continue

            add(valuedPoint(commonFields, navAtPoint, valuation, state.quantity, mathContext))
        }
    }
}

// ── Private helpers ───────────────────────────────────────────────────────────

/** Sorted NAV series with O(log n) lookup via binary search. */
private class NavSeries(series: List<Pair<LocalDate, BigDecimal>>) {
    private val sorted = series.sortedBy { it.first }

    /** Returns the latest entry whose date is ≤ [date], or null if none exists. */
    fun onOrBefore(date: LocalDate): Pair<LocalDate, BigDecimal>? {
        var lo = 0
        var hi = sorted.size - 1
        var best: Pair<LocalDate, BigDecimal>? = null
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid].first <= date) {
                best = sorted[mid]
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return best
    }
}

/** Fields shared between empty-position and valued point constructors. */
private data class CommonFields(
    val pointDate: LocalDate,
    val investedPrincipal: BigDecimal,
    val netContributions: BigDecimal,
    val costBasis: BigDecimal,
)

private fun emptyPositionPoint(
    common: CommonFields,
    nav: Pair<LocalDate, BigDecimal>?,
) = FundNavPerformancePoint(
    timestamp = common.pointDate.toUtcInstant(),
    investedPrincipal = common.investedPrincipal,
    netContributions = common.netContributions,
    currentCostBasis = BigDecimal.ZERO,
    grossValue = BigDecimal.ZERO,
    netValue = BigDecimal.ZERO,
    incomeTax = BigDecimal.ZERO,
    iof = BigDecimal.ZERO,
    grossGain = BigDecimal.ZERO,
    netGain = BigDecimal.ZERO,
    grossGainPercent = BigDecimal.ZERO,
    quantity = BigDecimal.ZERO,
    valuationSource = ValuationSource.MARKET_DATA,
    valuationReferenceDate = nav?.first,
)

private fun valuedPoint(
    common: CommonFields,
    nav: Pair<LocalDate, BigDecimal>,
    valuation: FundValuationResult,
    quantity: BigDecimal,
    mathContext: MathContext,
): FundNavPerformancePoint {
    val grossGain = valuation.grossValue?.subtract(common.costBasis)
    val netGain = valuation.netValue?.subtract(common.costBasis)
    return FundNavPerformancePoint(
        timestamp = common.pointDate.toUtcInstant(),
        investedPrincipal = common.investedPrincipal,
        netContributions = common.netContributions,
        currentCostBasis = common.costBasis,
        grossValue = valuation.grossValue,
        netValue = valuation.netValue,
        incomeTax = valuation.incomeTax,
        iof = valuation.iof,
        grossGain = grossGain,
        netGain = netGain,
        grossGainPercent = grossGain?.percentOf(common.costBasis, mathContext),
        quantity = quantity,
        valuationSource = valuation.valuationSource,
        valuationReferenceDate = valuation.valuationReferenceDate,
    )
}

/**
 * Point dates from [startDate] through [endDate].
 *
 * For DAY resolution, use all NAV dates in the window plus a terminal point at [endDate].
 * For MONTH resolution, use the first visible date, month-end dates, and [endDate].
 */
private fun performanceDatesForResolution(
    startDate: LocalDate,
    endDate: LocalDate,
    resolution: HoldingPerformanceResolution,
    navSeries: List<Pair<LocalDate, BigDecimal>>,
): List<LocalDate> =
    when (resolution) {
        HoldingPerformanceResolution.DAY ->
            (
                navSeries.map { it.first }.filter { !it.isBefore(startDate) && !it.isAfter(endDate) } + endDate
            ).distinct()
                .sorted()
        else -> {
            val startMonth = YearMonth.from(startDate)
            val endMonth = YearMonth.from(endDate)
            generateSequence(startMonth) { it.plusMonths(1) }
                .takeWhile { !it.isAfter(endMonth) }
                .map { month ->
                    when {
                        startMonth == endMonth -> endDate
                        month == startMonth -> startDate
                        month == endMonth -> endDate
                        else -> month.atEndOfMonth()
                    }
                }
                .distinct()
                .toList()
        }
    }

// ── Extension functions ───────────────────────────────────────────────────────

private fun LocalDate.toUtcInstant(): Instant = atStartOfDay().toInstant(ZoneOffset.UTC)

private fun BigDecimal.percentOf(
    base: BigDecimal,
    mathContext: MathContext,
): BigDecimal? = if (base > BigDecimal.ZERO) divide(base, mathContext) else null

private fun FundMarketSnapshot.withNav(nav: Pair<LocalDate, BigDecimal>): FundMarketSnapshot =
    copy(latestNav = FundMarketNav(date = nav.first, closePrice = nav.second))
