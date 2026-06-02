package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.portfolio.PortfolioFlowEventType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.abs
import kotlin.random.Random

class TwrTestOracleTest {
    @Test
    fun `deposit does not create artificial return`() {
        val d0 = LocalDate.of(2026, 1, 1)
        val d1 = d0.plusDays(1)
        val twr =
            TwrTestOracle.computeTwr(
                series =
                    listOf(
                        TwrTestOracle.OracleValuePoint(d0, BigDecimal("1000.00")),
                        TwrTestOracle.OracleValuePoint(d1, BigDecimal("1500.00")),
                    ),
                netFlowByDate = mapOf(d1 to BigDecimal("500.00")),
            )

        assertThat(twr).isNotNull()
        assertThat(abs(twr!!)).isLessThanOrEqualTo(TwrTestTolerance.EPS_UNIT)
    }

    @Test
    fun `dividend with ex-dividend drop is neutral in holdings-only universe`() {
        val d0 = LocalDate.of(2026, 2, 10)
        val d1 = d0.plusDays(1)
        val twr =
            TwrTestOracle.computeTwr(
                series =
                    listOf(
                        TwrTestOracle.OracleValuePoint(d0, BigDecimal("10000.00")),
                        TwrTestOracle.OracleValuePoint(d1, BigDecimal("9500.00")),
                    ),
                netFlowByDate = mapOf(d1 to BigDecimal("-500.00")),
            )

        assertThat(twr).isNotNull()
        assertThat(abs(twr!!)).isLessThanOrEqualTo(TwrTestTolerance.EPS_UNIT)
    }

    @Test
    fun `partitioning the same strategy across two accounts preserves TWR`() {
        val seed = 42
        val random = Random(seed)
        val start = LocalDate.of(2026, 1, 1)
        val days = 25
        val splitA = BigDecimal("0.6")
        val splitB = BigDecimal("0.4")

        var prevTotal = BigDecimal("10000.00")
        var prevA = prevTotal.multiply(splitA)
        var prevB = prevTotal.multiply(splitB)
        val seriesTotal = mutableListOf(TwrTestOracle.OracleValuePoint(start, prevTotal))
        val seriesA = mutableListOf(TwrTestOracle.OracleValuePoint(start, prevA))
        val seriesB = mutableListOf(TwrTestOracle.OracleValuePoint(start, prevB))
        val flowsTotal = mutableMapOf<LocalDate, BigDecimal>()
        val flowsA = mutableMapOf<LocalDate, BigDecimal>()
        val flowsB = mutableMapOf<LocalDate, BigDecimal>()

        repeat(days) { offset ->
            val date = start.plusDays((offset + 1).toLong())
            val r = BigDecimal(random.nextDouble(-0.015, 0.02).toString())
            val cf = BigDecimal(random.nextInt(-200, 401).toString())

            val nextTotal = prevTotal.multiply(BigDecimal.ONE.add(r)).add(cf)
            val cfA = cf.multiply(splitA)
            val cfB = cf.multiply(splitB)
            val nextA = prevA.multiply(BigDecimal.ONE.add(r)).add(cfA)
            val nextB = prevB.multiply(BigDecimal.ONE.add(r)).add(cfB)

            seriesTotal += TwrTestOracle.OracleValuePoint(date, nextTotal)
            seriesA += TwrTestOracle.OracleValuePoint(date, nextA)
            seriesB += TwrTestOracle.OracleValuePoint(date, nextB)
            flowsTotal[date] = cf
            flowsA[date] = cfA
            flowsB[date] = cfB

            prevTotal = nextTotal
            prevA = nextA
            prevB = nextB
        }

        val totalTwr = TwrTestOracle.computeTwr(seriesTotal, flowsTotal)
        val twrA = TwrTestOracle.computeTwr(seriesA, flowsA)
        val twrB = TwrTestOracle.computeTwr(seriesB, flowsB)

        assertThat(totalTwr).isNotNull()
        assertThat(twrA).isNotNull()
        assertThat(twrB).isNotNull()
        assertThat(abs(totalTwr!! - twrA!!)).isLessThanOrEqualTo(TwrTestTolerance.EPS_UNIT)
        assertThat(abs(totalTwr - twrB!!)).isLessThanOrEqualTo(TwrTestTolerance.EPS_UNIT)
    }

    @Test
    fun `corporate action is ignored by canonical net flow mapping`() {
        val net =
            TwrTestOracle.canonicalNetFlow(
                eventType = PortfolioFlowEventType.CORPORATE_ACTION,
                grossAmount = BigDecimal("123.45"),
            )
        assertThat(net).isNull()
    }
}
