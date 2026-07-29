package io.github.salomax.neotool.common.finance.tax.engine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

@DisplayName("TaxEngine")
class TaxEngineTest {
    private val referenceDate = LocalDate.of(2026, 1, 31)

    @Test
    fun `returns identical results for identical inputs`() {
        val lots = profitableLots()
        val regime = FakeTaxRegime()
        val navAt = { _: LocalDate -> BigDecimal("100.00") }

        val first = computeTax(lots, regime, referenceDate, navAt)
        val second = computeTax(lots, regime, referenceDate, navAt)

        assertThat(second).isEqualTo(first)
    }

    @Test
    fun `returns identical results regardless of lot processing order`() {
        val lots = profitableLots()
        val regime = FakeTaxRegime()
        val navAt = { _: LocalDate -> BigDecimal("100.00") }

        val forward = computeTax(lots, regime, referenceDate, navAt)
        val reversed = computeTax(lots.reversed(), regime, referenceDate, navAt)

        assertThat(reversed).isEqualTo(forward)
        assertThat(reversed.lots.map(TaxLotResult::lotId)).containsExactly("lot-a", "lot-b")
        assertThat(reversed.totalIof).isEqualByComparingTo("1.6500000")
        assertThat(reversed.totalPeriodicWithholdingDue).isEqualByComparingTo("3.417500000")
        assertThat(reversed.totalFinalTaxDue).isEqualByComparingTo("13.67000000")
    }

    @Test
    fun `defaults IOF to zero when the regime does not override it`() {
        val regime =
            object : TaxRegime {
                override fun periodicWithholdingRate(elapsedDays: Int) = null

                override fun finalRate(elapsedDays: Int) = BigDecimal("0.20")
            }

        val result =
            computeTax(
                lots = listOf(profitableLots().single { it.id == "lot-a" }),
                regime = regime,
                referenceDate = referenceDate,
                navAt = { BigDecimal("100.00") },
            )

        assertThat(result.lots.single().iofDue).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(result.totalIof).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(result.totalFinalTaxDue).isEqualByComparingTo("8.0000")
    }

    @Test
    fun `excludes lots with non-positive gains`() {
        val lots =
            listOf(
                TaxLot(
                    id = "zero-gain",
                    quantity = BigDecimal("2"),
                    unitCost = BigDecimal("100.00"),
                    acquisitionDate = referenceDate.minusDays(10),
                ),
                TaxLot(
                    id = "negative-gain",
                    quantity = BigDecimal("3"),
                    unitCost = BigDecimal("110.00"),
                    acquisitionDate = referenceDate.minusDays(20),
                ),
            )

        val result =
            computeTax(
                lots = lots,
                regime = FakeTaxRegime(),
                referenceDate = referenceDate,
                navAt = { BigDecimal("100.00") },
            )

        assertThat(result.lots).isEmpty()
        assertThat(result.totalIof).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(result.totalPeriodicWithholdingDue).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(result.totalFinalTaxDue).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `returns an empty result when NAV is unavailable`() {
        val result =
            computeTax(
                lots = profitableLots(),
                regime = FakeTaxRegime(),
                referenceDate = referenceDate,
                navAt = { null },
            )

        assertThat(result.referenceDate).isEqualTo(referenceDate)
        assertThat(result.lots).isEmpty()
        assertThat(result.totalIof).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(result.totalPeriodicWithholdingDue).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(result.totalFinalTaxDue).isEqualByComparingTo(BigDecimal.ZERO)
    }

    private fun profitableLots() =
        listOf(
            TaxLot(
                id = "lot-b",
                quantity = BigDecimal("3"),
                unitCost = BigDecimal("90.00"),
                acquisitionDate = referenceDate.minusDays(15),
            ),
            TaxLot(
                id = "lot-a",
                quantity = BigDecimal("2"),
                unitCost = BigDecimal("80.00"),
                acquisitionDate = referenceDate.minusDays(30),
            ),
        )

    private class FakeTaxRegime : TaxRegime {
        override fun iofRate(elapsedDays: Int): BigDecimal = BigDecimal(elapsedDays).movePointLeft(3)

        override fun periodicWithholdingRate(elapsedDays: Int) = BigDecimal("0.05")

        override fun finalRate(elapsedDays: Int) = BigDecimal("0.20")
    }
}
