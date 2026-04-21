package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.portfolio.PortfolioFlowEventType
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Independent TWR oracle used in tests.
 *
 * Formula:
 * r_d = (V_d - V_{d-1} - CF_d) / V_{d-1}
 * TWR = Π(1 + r_d) - 1
 */
object TwrTestOracle {
    fun computeTwr(
        series: List<OracleValuePoint>,
        netFlowByDate: Map<LocalDate, BigDecimal>,
    ): Double? {
        if (series.size < 2) return null
        val sorted = series.sortedBy { it.date }
        var prevValue = sorted.first().totalValue
        if (prevValue <= BigDecimal.ZERO) return null

        var factor = BigDecimal.ONE
        sorted.drop(1).forEach { point ->
            if (prevValue <= BigDecimal.ZERO) return null
            val netFlow = netFlowByDate[point.date] ?: BigDecimal.ZERO
            val dailyReturn =
                point.totalValue
                    .subtract(prevValue)
                    .subtract(netFlow)
                    .divide(prevValue, SCALE, RoundingMode.HALF_UP)
            factor = factor.multiply(BigDecimal.ONE.add(dailyReturn), MathContext.DECIMAL64)
            prevValue = point.totalValue
        }
        return factor.subtract(BigDecimal.ONE).setScale(SCALE, RoundingMode.HALF_UP).toDouble()
    }

    fun canonicalNetFlow(
        eventType: PortfolioFlowEventType,
        grossAmount: BigDecimal,
    ): BigDecimal? =
        when (eventType) {
            PortfolioFlowEventType.DEPOSIT -> grossAmount
            PortfolioFlowEventType.WITHDRAWAL,
            PortfolioFlowEventType.DIVIDEND,
            PortfolioFlowEventType.INCOME_TAX,
            -> grossAmount.negate()
            PortfolioFlowEventType.CORPORATE_ACTION -> null
        }

    fun aggregateNetFlowByDate(
        rows: List<OracleFlowPoint>,
    ): Map<LocalDate, BigDecimal> =
        rows.asSequence()
            .mapNotNull { row -> canonicalNetFlow(row.eventType, row.grossAmount)?.let { amount -> row.date to amount } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, values) -> values.fold(BigDecimal.ZERO, BigDecimal::add) }

    data class OracleValuePoint(
        val date: LocalDate,
        val totalValue: BigDecimal,
    )

    data class OracleFlowPoint(
        val date: LocalDate,
        val eventType: PortfolioFlowEventType,
        val grossAmount: BigDecimal,
    )

    private const val SCALE = 8
}
