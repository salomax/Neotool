package io.github.salomax.neotool.banking.domain.investment

import io.github.salomax.neotool.banking.service.investment.FixedIncomeValuationService
import io.github.salomax.neotool.banking.service.investment.ValuationSource
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
        fixedAnnualRate: String? = null,
        issueDate: LocalDate = startDate,
        maturityDate: LocalDate? = null,
        isTaxExempt: Boolean = false,
        indexerPercentage: String? = null,
        couponFrequency: CouponFrequency? = null,
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
            fixedAnnualRate = fixedAnnualRate?.let { BigDecimal(it) },
            issueDate = issueDate,
            maturityDate = maturityDate,
            isTaxExempt = isTaxExempt,
            couponFrequency = couponFrequency,
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
                fixedAnnualRate = "0.12",
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
    fun `CDI historical monthly series is preferred over latest rolling annual rate`() {
        val issueDate = LocalDate.of(2024, 2, 18)
        val currentDate = LocalDate.of(2026, 3, 26)
        val service = FixedIncomeValuationService { currentDate }

        val h =
            holding(
                indexer = Indexer.CDI,
                quantity = "10000.00",
                averageCost = "1.00",
                issueDate = issueDate,
                indexerPercentage = "1.2393",
            )

        val monthlySeries =
            mapOf(
                Indexer.CDI to
                    listOf(
                        LocalDate.of(2024, 2, 1) to BigDecimal("0.0080"),
                        LocalDate.of(2024, 3, 1) to BigDecimal("0.0083"),
                        LocalDate.of(2024, 4, 1) to BigDecimal("0.0089"),
                        LocalDate.of(2024, 5, 1) to BigDecimal("0.0083"),
                        LocalDate.of(2024, 6, 1) to BigDecimal("0.0079"),
                        LocalDate.of(2024, 7, 1) to BigDecimal("0.0091"),
                        LocalDate.of(2024, 8, 1) to BigDecimal("0.0087"),
                        LocalDate.of(2024, 9, 1) to BigDecimal("0.0084"),
                        LocalDate.of(2024, 10, 1) to BigDecimal("0.0093"),
                        LocalDate.of(2024, 11, 1) to BigDecimal("0.0079"),
                        LocalDate.of(2024, 12, 1) to BigDecimal("0.0093"),
                        LocalDate.of(2025, 1, 1) to BigDecimal("0.0101"),
                        LocalDate.of(2025, 2, 1) to BigDecimal("0.0099"),
                        LocalDate.of(2025, 3, 1) to BigDecimal("0.0096"),
                        LocalDate.of(2025, 4, 1) to BigDecimal("0.0106"),
                        LocalDate.of(2025, 5, 1) to BigDecimal("0.0114"),
                        LocalDate.of(2025, 6, 1) to BigDecimal("0.0110"),
                        LocalDate.of(2025, 7, 1) to BigDecimal("0.0128"),
                        LocalDate.of(2025, 8, 1) to BigDecimal("0.0116"),
                        LocalDate.of(2025, 9, 1) to BigDecimal("0.0122"),
                        LocalDate.of(2025, 10, 1) to BigDecimal("0.0128"),
                        LocalDate.of(2025, 11, 1) to BigDecimal("0.0105"),
                        LocalDate.of(2025, 12, 1) to BigDecimal("0.0122"),
                        LocalDate.of(2026, 1, 1) to BigDecimal("0.0116"),
                        LocalDate.of(2026, 2, 1) to BigDecimal("0.0100"),
                        LocalDate.of(2026, 3, 1) to BigDecimal("0.0094"),
                    ),
            )

        val result =
            service.estimate(
                holding = h,
                indicatorRates = mapOf(Indexer.CDI to BigDecimal("0.1449119371")),
                monthlySeries = monthlySeries,
            )

        assertThat(result.grossValue).isNotNull
        assertThat(result.grossValue!!.toDouble()).isCloseTo(13664.34, within(0.5))
        assertThat(result.grossValue!!.toDouble()).isLessThan(14000.0)
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
                fixedAnnualRate = "0.06",
            )

        val ipcaAnnual = BigDecimal("0.065")
        val result =
            service.estimate(
                holding = h,
                indicatorRates = mapOf(Indexer.IPCA to ipcaAnnual),
            )

        val expected =
            run {
                val principal = 10_000.0
                val indexedGross = principal * (1.0 + 0.065)
                val spreadGross = principal * (1.0 + 0.06)
                indexedGross + spreadGross - principal
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
                fixedAnnualRate = "0.12",
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
                fixedAnnualRate = "0.12",
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
    fun `OUTROS indexer uses pre fixed rate`() {
        val tPlus100 = startDate.plusDays(150)
        val service = FixedIncomeValuationService { tPlus100 }

        val h =
            holding(
                indexer = Indexer.OUTROS,
                quantity = "10000.00",
                averageCost = "1.00",
                fixedAnnualRate = "0.12",
            )

        val result =
            service.estimate(
                holding = h,
                indicatorRates = mapOf(Indexer.CDI to BigDecimal("0.12")),
            )

        assertThat(result.grossValue).isNotNull
        assertThat(result.grossValue!!.toDouble()).isGreaterThan(10000.0)
        assertThat(result.valuationSource).isEqualTo(ValuationSource.CALCULATED)
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
                fixedAnnualRate = "0.12",
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

    @Test
    fun `IPCA monthly series with spread matches issuer style mark to date valuation`() {
        val issueDate = LocalDate.of(2024, 9, 23)
        val currentDate = LocalDate.of(2026, 3, 25)
        val service = FixedIncomeValuationService { currentDate }
        val principal = BigDecimal("10674.30")

        val h =
            holding(
                indexer = Indexer.IPCA,
                quantity = principal.toPlainString(),
                averageCost = "1.00",
                fixedAnnualRate = "0.088",
                issueDate = issueDate,
            )

        val monthlySeries =
            mapOf(
                Indexer.IPCA to
                    listOf(
                        LocalDate.of(2024, 9, 1) to BigDecimal("0.0044"),
                        LocalDate.of(2024, 10, 1) to BigDecimal("0.0056"),
                        LocalDate.of(2024, 11, 1) to BigDecimal("0.0039"),
                        LocalDate.of(2024, 12, 1) to BigDecimal("0.0052"),
                        LocalDate.of(2025, 1, 1) to BigDecimal("0.0016"),
                        LocalDate.of(2025, 2, 1) to BigDecimal("0.0131"),
                        LocalDate.of(2025, 3, 1) to BigDecimal("0.0056"),
                        LocalDate.of(2025, 4, 1) to BigDecimal("0.0043"),
                        LocalDate.of(2025, 5, 1) to BigDecimal("0.0026"),
                        LocalDate.of(2025, 6, 1) to BigDecimal("0.0024"),
                        LocalDate.of(2025, 7, 1) to BigDecimal("0.0026"),
                        LocalDate.of(2025, 8, 1) to BigDecimal("-0.0011"),
                        LocalDate.of(2025, 9, 1) to BigDecimal("0.0048"),
                        LocalDate.of(2025, 10, 1) to BigDecimal("0.0009"),
                        LocalDate.of(2025, 11, 1) to BigDecimal("0.0018"),
                        LocalDate.of(2025, 12, 1) to BigDecimal("0.0033"),
                        LocalDate.of(2026, 1, 1) to BigDecimal("0.0033"),
                        LocalDate.of(2026, 2, 1) to BigDecimal("0.0070"),
                    ),
            )

        val result =
            service.estimate(
                holding = h,
                indicatorRates = mapOf(Indexer.IPCA to BigDecimal("0.0701")),
                monthlySeries = monthlySeries,
            )

        assertThat(result.grossValue).isNotNull
        assertThat(result.grossValue!!.toDouble()).isCloseTo(12900.25, within(2.0))
        assertThat(result.netValue).isNotNull
        assertThat(result.netValue!!.toDouble()).isCloseTo(12510.71, within(2.0))
    }

    @Test
    fun `projection after monthly series coverage keeps historical accrual and does not drop`() {
        val issueDate = LocalDate.of(2024, 9, 23)
        val service = FixedIncomeValuationService { LocalDate.of(2026, 3, 27) }
        val principal = BigDecimal("10674.30")
        val monthlySeries =
            mapOf(
                Indexer.IPCA to
                    listOf(
                        LocalDate.of(2024, 9, 1) to BigDecimal("0.0044"),
                        LocalDate.of(2024, 10, 1) to BigDecimal("0.0056"),
                        LocalDate.of(2024, 11, 1) to BigDecimal("0.0039"),
                        LocalDate.of(2024, 12, 1) to BigDecimal("0.0052"),
                        LocalDate.of(2025, 1, 1) to BigDecimal("0.0016"),
                        LocalDate.of(2025, 2, 1) to BigDecimal("0.0131"),
                        LocalDate.of(2025, 3, 1) to BigDecimal("0.0056"),
                        LocalDate.of(2025, 4, 1) to BigDecimal("0.0043"),
                        LocalDate.of(2025, 5, 1) to BigDecimal("0.0026"),
                        LocalDate.of(2025, 6, 1) to BigDecimal("0.0024"),
                        LocalDate.of(2025, 7, 1) to BigDecimal("0.0026"),
                        LocalDate.of(2025, 8, 1) to BigDecimal("-0.0011"),
                        LocalDate.of(2025, 9, 1) to BigDecimal("0.0048"),
                        LocalDate.of(2025, 10, 1) to BigDecimal("0.0009"),
                        LocalDate.of(2025, 11, 1) to BigDecimal("0.0018"),
                        LocalDate.of(2025, 12, 1) to BigDecimal("0.0033"),
                        LocalDate.of(2026, 1, 1) to BigDecimal("0.0033"),
                        LocalDate.of(2026, 2, 1) to BigDecimal("0.0070"),
                    ),
            )
        val h =
            holding(
                indexer = Indexer.IPCA,
                quantity = principal.toPlainString(),
                averageCost = "1.00",
                fixedAnnualRate = "0.088",
                issueDate = issueDate,
            )

        val historical =
            service.estimate(
                holding = h,
                indicatorRates = mapOf(Indexer.IPCA to BigDecimal("0.0701")),
                monthlySeries = monthlySeries,
                asOfDate = LocalDate.of(2026, 3, 27),
            )
        val projected =
            service.estimate(
                holding = h,
                indicatorRates = mapOf(Indexer.IPCA to BigDecimal("0.0701")),
                monthlySeries = monthlySeries,
                asOfDate = LocalDate.of(2026, 3, 28),
                allowRateProjectionPastSeries = true,
            )

        assertThat(historical.grossValue).isNotNull
        assertThat(projected.grossValue).isNotNull
        assertThat(projected.grossValue!!).isGreaterThanOrEqualTo(historical.grossValue)
        assertThat(projected.netValue!!).isGreaterThanOrEqualTo(historical.netValue)
    }

    @Test
    fun `coupon holdings accrue from last interest payment date`() {
        val service = FixedIncomeValuationService { LocalDate.of(2026, 3, 30) }
        val holding =
            holding(
                indexer = Indexer.CDI,
                quantity = "10000.00",
                averageCost = "1.00",
                issueDate = LocalDate.of(2025, 1, 1),
                indexerPercentage = "1.00",
                couponFrequency = CouponFrequency.QUARTERLY,
            )

        val lastCouponPayment =
            InvestmentTransaction(
                id = UUID.randomUUID(),
                holdingId = holding.id!!,
                financialAccountId = holding.financialAccountId,
                accountId = holding.accountId,
                createdByUserId = UUID.randomUUID(),
                direction = InvestmentTransactionDirection.INFLOW,
                type = InvestmentTransactionType.INTEREST_PAYMENT,
                grossValue = BigDecimal("200.00"),
                tradeDate = LocalDate.of(2026, 1, 15),
                source = InvestmentSource.MANUAL,
            )

        val withCoupon =
            service.estimate(
                holding = holding,
                indicatorRates = mapOf(Indexer.CDI to BigDecimal("0.12")),
                transactions = listOf(lastCouponPayment),
            )
        val withoutCoupon =
            service.estimate(
                holding = holding,
                indicatorRates = mapOf(Indexer.CDI to BigDecimal("0.12")),
            )

        assertThat(withCoupon.grossValue).isNotNull
        assertThat(withoutCoupon.grossValue).isNotNull
        assertThat(withCoupon.grossValue!!).isGreaterThan(BigDecimal("10000.00"))
        assertThat(withCoupon.grossValue!!).isLessThan(withoutCoupon.grossValue)
    }
}
