package io.github.salomax.neotool.banking.domain.investment

import io.github.salomax.neotool.banking.service.investment.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@DisplayName("FixedIncomeValuationService")
class FixedIncomeValuationServiceTest {

    private val startDate = LocalDate.of(2024, 1, 1)

    private fun holding(
        indexer: Indexer,
        quantity: String = "10000.00",
        averageCost: String = "1.00",
        preFixedRate: String? = null,
        issueDate: LocalDate = startDate,
        maturityDate: LocalDate? = null,
        isTaxExempt: Boolean = false,
        indexerPercentage: String? = null,
    ): Holding =
        Holding(
            id = UUID.randomUUID(),
            financialAccountId = UUID.randomUUID(),
            accountId = UUID.randomUUID(),
            assetId = UUID.randomUUID(),
            quantity = BigDecimal(quantity),
            averageCost = BigDecimal(averageCost),
            indexer = indexer,
            indexerPercentage = indexerPercentage?.let { BigDecimal(it) },
            preFixedRate = preFixedRate?.let { BigDecimal(it) },
            issueDate = issueDate,
            maturityDate = maturityDate,
            isTaxExempt = isTaxExempt,
        )

    @Test
    fun `PRE_FIXADO accrual matches expected example`() {
        val tPlus252 = startDate.plusDays(365)
        val service = FixedIncomeValuationService { tPlus252 }

        val h =
            holding(
                indexer = Indexer.PRE_FIXADO,
                quantity = "10000.00",
                averageCost = "1.00",
                preFixedRate = "0.12",
            )

        val result =
            service.estimate(
                holding = h,
                indicatorRates = emptyMap(),
            )

        assertThat(result.grossValue).isNotNull
        val gross = result.grossValue!!
        assertThat(gross.toDouble()).isCloseTo(11200.0, within(1.0))
    }

    @Test
    fun `CDI accrual uses daily rate and indexer percentage`() {
        val tPlus126 = startDate.plusDays(183)
        val service = FixedIncomeValuationService { tPlus126 }

        val h =
            holding(
                indexer = Indexer.CDI,
                quantity = "10000.00",
                averageCost = "1.00",
                indexerPercentage = "1.10",
            )

        val cdiAnnual = BigDecimal("0.1325")
        val result =
            service.estimate(
                holding = h,
                indicatorRates = mapOf(Indexer.CDI to cdiAnnual),
            )

        val expected =
            run {
                val principal = 10_000.0
                val onePlusAnnual = 1.0 + 0.1325
                val cdiDaily = Math.pow(onePlusAnnual, 1.0 / 252.0) - 1.0
                val dailyWithPct = cdiDaily * 1.10
                val factor = Math.pow(1.0 + dailyWithPct, 126.0)
                principal * factor
            }

        assertThat(result.grossValue).isNotNull
        assertThat(result.grossValue!!.toDouble()).isCloseTo(expected, within(expected * 0.001))
    }

    @Test
    fun `IPCA+ accrual combines price index and spread`() {
        val tPlus252 = startDate.plusDays(365)
        val service = FixedIncomeValuationService { tPlus252 }

        val h =
            holding(
                indexer = Indexer.IPCA,
                quantity = "10000.00",
                averageCost = "1.00",
                preFixedRate = "0.06",
            )

        val ipcaAnnual = BigDecimal("0.065")
        val result =
            service.estimate(
                holding = h,
                indicatorRates = mapOf(Indexer.IPCA to ipcaAnnual),
            )

        val effectiveAnnual = (1.0 + 0.065) * (1.0 + 0.06) - 1.0
        val expected =
            run {
                val principal = 10_000.0
                val factor = Math.pow(1.0 + effectiveAnnual, 252.0 / 252.0)
                principal * factor
            }

        assertThat(result.grossValue).isNotNull
        assertThat(result.grossValue!!.toDouble()).isCloseTo(expected, within(expected * 0.001))
    }

    @Test
    fun `tax-exempt holdings return zero IR`() {
        val tPlus200 = startDate.plusDays(250)
        val service = FixedIncomeValuationService { tPlus200 }

        val h =
            holding(
                indexer = Indexer.PRE_FIXADO,
                quantity = "10000.00",
                averageCost = "1.00",
                preFixedRate = "0.12",
                isTaxExempt = true,
            )

        val result =
            service.estimate(
                holding = h,
                indicatorRates = emptyMap(),
            )

        assertThat(result.incomeTax).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `past-maturity holdings cap time at maturity date`() {
        val maturity = startDate.plusDays(365)
        val farFuture = startDate.plusYears(5)
        val service = FixedIncomeValuationService { farFuture }

        val h =
            holding(
                indexer = Indexer.PRE_FIXADO,
                quantity = "10000.00",
                averageCost = "1.00",
                preFixedRate = "0.12",
                maturityDate = maturity,
            )

        val resultAtMaturity =
            service.estimate(
                holding = h,
                indicatorRates = emptyMap(),
            )

        val serviceAtMaturity = FixedIncomeValuationService { maturity }
        val resultExactMaturity =
            serviceAtMaturity.estimate(
                holding = h,
                indicatorRates = emptyMap(),
            )

        assertThat(resultAtMaturity.grossValue!!.toDouble())
            .isCloseTo(resultExactMaturity.grossValue!!.toDouble(), within(0.01))
    }

    @Test
    fun `missing indicator returns unavailable`() {
        val tPlus100 = startDate.plusDays(150)
        val service = FixedIncomeValuationService { tPlus100 }

        val h =
            holding(
                indexer = Indexer.CDI,
                quantity = "10000.00",
                averageCost = "1.00",
                indexerPercentage = "1.10",
            )

        val result =
            service.estimate(
                holding = h,
                indicatorRates = emptyMap(),
            )

        assertThat(result.grossValue).isNull()
        assertThat(result.valuationSource).isEqualTo(ValuationSource.UNAVAILABLE)
    }

    @Test
    fun `OUTROS indexer returns unavailable`() {
        val tPlus100 = startDate.plusDays(150)
        val service = FixedIncomeValuationService { tPlus100 }

        val h =
            holding(
                indexer = Indexer.OUTROS,
                quantity = "10000.00",
                averageCost = "1.00",
            )

        val result =
            service.estimate(
                holding = h,
                indicatorRates = mapOf(Indexer.CDI to BigDecimal("0.12")),
            )

        assertThat(result.grossValue).isNull()
        assertThat(result.valuationSource).isEqualTo(ValuationSource.UNAVAILABLE)
    }

    @Test
    fun `IR and IOF combined on day 10 - taxable gain is gain minus IOF`() {
        // Day 10: IOF still applies; IR applies to (gain - iof). T1.4.6 combined path.
        val day10 = startDate.plusDays(10)
        val service = FixedIncomeValuationService { day10 }

        val h =
            holding(
                indexer = Indexer.PRE_FIXADO,
                quantity = "10000.00",
                averageCost = "1.00",
                preFixedRate = "0.12",
                isTaxExempt = false,
            )

        val result =
            service.estimate(
                holding = h,
                indicatorRates = emptyMap(),
            )

        assertThat(result.grossValue).isNotNull
        assertThat(result.incomeTax).isNotNull
        assertThat(result.iof).isNotNull
        val principal = BigDecimal("10000.00")
        val gain = result.grossValue!! - principal
        assertThat(gain).isGreaterThan(BigDecimal.ZERO)
        assertThat(result.iof!!).isGreaterThan(BigDecimal.ZERO)
        val taxableGain = gain - result.iof!!
        assertThat(taxableGain).isLessThan(gain)
        val expectedIr = taxableGain.multiply(BigDecimal("0.225"))
        assertThat(result.incomeTax!!.toDouble()).isCloseTo(expectedIr.toDouble(), within(0.01))
    }
}

