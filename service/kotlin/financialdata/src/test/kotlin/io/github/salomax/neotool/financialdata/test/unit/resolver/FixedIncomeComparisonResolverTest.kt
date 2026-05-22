package io.github.salomax.neotool.financialdata.test.unit.resolver

import graphql.schema.DataFetchingEnvironment
import io.github.salomax.neotool.financialdata.domain.fixedincome.ComparisonRow
import io.github.salomax.neotool.financialdata.domain.fixedincome.DayCountMethodology
import io.github.salomax.neotool.financialdata.domain.fixedincome.FixedIncomeComparisonInput
import io.github.salomax.neotool.financialdata.domain.fixedincome.FixedIncomeComparisonResult
import io.github.salomax.neotool.financialdata.domain.fixedincome.FixedIncomeProduct
import io.github.salomax.neotool.financialdata.domain.fixedincome.FixedIncomeSummary
import io.github.salomax.neotool.financialdata.domain.fixedincome.Methodology
import io.github.salomax.neotool.financialdata.domain.fixedincome.PerformancePoint
import io.github.salomax.neotool.financialdata.domain.fixedincome.PerformanceSeries
import io.github.salomax.neotool.financialdata.domain.fixedincome.ProductGroup
import io.github.salomax.neotool.financialdata.domain.fixedincome.RateModel
import io.github.salomax.neotool.financialdata.domain.fixedincome.TaxBracket
import io.github.salomax.neotool.financialdata.domain.fixedincome.TaxMethodology
import io.github.salomax.neotool.financialdata.graphql.dto.FixedIncomeComparisonResultDTO
import io.github.salomax.neotool.financialdata.graphql.dto.FixedIncomeComparisonRowDTO
import io.github.salomax.neotool.financialdata.graphql.dto.FixedIncomeDayCountMethodologyDTO
import io.github.salomax.neotool.financialdata.graphql.dto.FixedIncomeMethodologyDTO
import io.github.salomax.neotool.financialdata.graphql.dto.FixedIncomePerformancePointDTO
import io.github.salomax.neotool.financialdata.graphql.dto.FixedIncomePerformanceSeriesDTO
import io.github.salomax.neotool.financialdata.graphql.dto.FixedIncomeSummaryDTO
import io.github.salomax.neotool.financialdata.graphql.dto.FixedIncomeTaxBracketDTO
import io.github.salomax.neotool.financialdata.graphql.dto.FixedIncomeTaxMethodologyDTO
import io.github.salomax.neotool.financialdata.graphql.mapper.FixedIncomeComparisonMapper
import io.github.salomax.neotool.financialdata.graphql.resolver.FixedIncomeComparisonResolver
import io.github.salomax.neotool.financialdata.service.fixedincome.FixedIncomeComparisonService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

class FixedIncomeComparisonResolverTest {
    private val comparisonService: FixedIncomeComparisonService = mock()
    private val mapper: FixedIncomeComparisonMapper = mock()
    private lateinit var resolver: FixedIncomeComparisonResolver
    private lateinit var env: DataFetchingEnvironment

    @BeforeEach
    fun setUp() {
        resolver = FixedIncomeComparisonResolver(comparisonService, mapper)
        env = mock()
    }

    @Test
    fun `throws when input is null`() {
        whenever(env.getArgument<Map<String, Any?>>("input")).thenReturn(null)

        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                resolver.fixedIncomeComparison(env)
            }

        assertThat(ex.message).contains("input is required")
    }

    @Test
    fun `throws when productGroup is missing`() {
        whenever(env.getArgument<Map<String, Any?>>("input")).thenReturn(validInputMap() - "productGroup")

        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                resolver.fixedIncomeComparison(env)
            }

        assertThat(ex.message).contains("productGroup is required")
    }

    @Test
    fun `throws when principal is missing`() {
        whenever(env.getArgument<Map<String, Any?>>("input")).thenReturn(validInputMap() - "principal")

        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                resolver.fixedIncomeComparison(env)
            }

        assertThat(ex.message).contains("principal is required")
    }

    @Test
    fun `throws when startDate is missing`() {
        whenever(env.getArgument<Map<String, Any?>>("input")).thenReturn(validInputMap() - "startDate")

        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                resolver.fixedIncomeComparison(env)
            }

        assertThat(ex.message).contains("startDate is required")
    }

    @Test
    fun `throws when endDate is missing`() {
        whenever(env.getArgument<Map<String, Any?>>("input")).thenReturn(validInputMap() - "endDate")

        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                resolver.fixedIncomeComparison(env)
            }

        assertThat(ex.message).contains("endDate is required")
    }

    @Test
    fun `throws when rateModel is missing`() {
        whenever(env.getArgument<Map<String, Any?>>("input")).thenReturn(validInputMap() - "rateModel")

        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                resolver.fixedIncomeComparison(env)
            }

        assertThat(ex.message).contains("rateModel is required")
    }

    @Test
    fun `throws for unknown productGroup value`() {
        whenever(env.getArgument<Map<String, Any?>>("input")).thenReturn(
            validInputMap() + ("productGroup" to "INVALID"),
        )

        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                resolver.fixedIncomeComparison(env)
            }

        assertThat(ex.message).contains("Unknown productGroup")
    }

    @Test
    fun `throws for unknown rateModel value`() {
        whenever(env.getArgument<Map<String, Any?>>("input")).thenReturn(
            validInputMap() + ("rateModel" to "BOGUS"),
        )

        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                resolver.fixedIncomeComparison(env)
            }

        assertThat(ex.message).contains("Unknown rateModel")
    }

    @Test
    fun `parses principal from Int`() {
        val inputMap = validInputMap() + ("principal" to 10000)
        val captured = runSuccessfulComparisonAndCaptureInput(inputMap)

        assertThat(captured.principal).isEqualByComparingTo(BigDecimal("10000"))
    }

    @Test
    fun `parses principal from Long`() {
        val inputMap = validInputMap() + ("principal" to 10000L)
        val captured = runSuccessfulComparisonAndCaptureInput(inputMap)

        assertThat(captured.principal).isEqualByComparingTo(BigDecimal("10000"))
    }

    @Test
    fun `parses principal from String`() {
        val inputMap = validInputMap() + ("principal" to "10000.50")
        val captured = runSuccessfulComparisonAndCaptureInput(inputMap)

        assertThat(captured.principal).isEqualByComparingTo(BigDecimal("10000.50"))
    }

    @Test
    fun `parses startDate from String`() {
        val inputMap = validInputMap() + ("startDate" to "2024-01-01")
        val captured = runSuccessfulComparisonAndCaptureInput(inputMap)

        assertThat(captured.startDate).isEqualTo(LocalDate.of(2024, 1, 1))
    }

    @Test
    fun `parses startDate from LocalDate directly`() {
        val inputMap = validInputMap() + ("startDate" to LocalDate.of(2024, 1, 1))
        val captured = runSuccessfulComparisonAndCaptureInput(inputMap)

        assertThat(captured.startDate).isEqualTo(LocalDate.of(2024, 1, 1))
    }

    @Test
    fun `parses contractedRateBps from Int`() {
        val inputMap = validInputMap() + ("contractedRateBps" to 1200)
        val captured = runSuccessfulComparisonAndCaptureInput(inputMap)

        assertThat(captured.contractedRateBps).isEqualTo(1200)
    }

    @Test
    fun `parses contractedRateBps from Long`() {
        val inputMap = validInputMap() + ("contractedRateBps" to 1200L)
        val captured = runSuccessfulComparisonAndCaptureInput(inputMap)

        assertThat(captured.contractedRateBps).isEqualTo(1200)
    }

    @Test
    fun `parses contractedRateBps from String`() {
        val inputMap = validInputMap() + ("contractedRateBps" to "1200")
        val captured = runSuccessfulComparisonAndCaptureInput(inputMap)

        assertThat(captured.contractedRateBps).isEqualTo(1200)
    }

    @Test
    fun `throws when contractedRateBps is a non-integer Double`() {
        whenever(env.getArgument<Map<String, Any?>>("input")).thenReturn(
            validInputMap() + ("contractedRateBps" to 12.5),
        )

        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                resolver.fixedIncomeComparison(env)
            }

        assertThat(ex.message).contains("must be an integer bps")
    }

    @Test
    fun `full valid input succeeds and delegates to service`() {
        val inputMap = validInputMap()
        val result = mockResult()
        val expected = mockResultDto()
        whenever(env.getArgument<Map<String, Any?>>("input")).thenReturn(inputMap)
        whenever(comparisonService.compare(any())).thenReturn(result)
        whenever(mapper.toResultDTO(result)).thenReturn(expected)

        val actual = resolver.fixedIncomeComparison(env)

        assertThat(actual).isEqualTo(expected)
        verify(comparisonService, times(1)).compare(any())
        verify(mapper, times(1)).toResultDTO(eq(result))
    }

    private fun runSuccessfulComparisonAndCaptureInput(inputMap: Map<String, Any?>): FixedIncomeComparisonInput {
        val result = mockResult()
        val resultDto = mockResultDto()
        whenever(env.getArgument<Map<String, Any?>>("input")).thenReturn(inputMap)
        whenever(comparisonService.compare(any())).thenReturn(result)
        whenever(mapper.toResultDTO(result)).thenReturn(resultDto)

        resolver.fixedIncomeComparison(env)

        val captor = argumentCaptor<FixedIncomeComparisonInput>()
        verify(comparisonService).compare(captor.capture())
        return captor.firstValue
    }

    private fun validInputMap(): Map<String, Any?> =
        mapOf(
            "productGroup" to ProductGroup.TAXABLE_IR.name,
            "product" to FixedIncomeProduct.CDB.name,
            "principal" to 10000,
            "startDate" to "2024-01-01",
            "endDate" to "2024-12-31",
            "rateModel" to RateModel.FIXED.name,
            "contractedRateBps" to 1200,
            "cdiAnnualRateBps" to 1000,
            "ipcaAnnualRateBps" to 500,
        )

    private fun mockResult(): FixedIncomeComparisonResult =
        FixedIncomeComparisonResult(
            summary =
                FixedIncomeSummary(
                    baseProduct = FixedIncomeProduct.CDB,
                    baseProductGroup = ProductGroup.TAXABLE_IR,
                    baseRateModel = RateModel.FIXED,
                    baseEquivalentRateBps = 1200,
                    baseRateDisplay = "12.00% fixed",
                    principal = BigDecimal("10000"),
                    gross = BigDecimal("11200"),
                    ir = BigDecimal("180"),
                    net = BigDecimal("11020"),
                    realGainPercent = BigDecimal("10.20"),
                ),
            rows =
                listOf(
                    ComparisonRow(
                        label = "TAXABLE_IR / FIXED (base)",
                        productGroup = ProductGroup.TAXABLE_IR,
                        rateModel = RateModel.FIXED,
                        equivalentRateBps = 1200,
                        contractedRateDisplay = "12.00% fixed",
                        gross = BigDecimal("11200"),
                        ir = BigDecimal("180"),
                        net = BigDecimal("11020"),
                        realGainPercent = BigDecimal("10.20"),
                        isExempt = false,
                        isBase = true,
                    ),
                ),
            performanceSeries =
                listOf(
                    PerformanceSeries(
                        productGroup = ProductGroup.TAXABLE_IR,
                        points =
                            listOf(
                                PerformancePoint(LocalDate.of(2024, 1, 1), BigDecimal("10000")),
                            ),
                    ),
                ),
            methodology =
                Methodology(
                    dayCount =
                        DayCountMethodology(
                            accrualConvention = "BUSINESS_252",
                            calendarDays = 365,
                            businessDays = 252,
                        ),
                    tax =
                        TaxMethodology(
                            appliesOnProfitOnly = true,
                            activeBracket =
                                TaxBracket(
                                    fromCalendarDays = 181,
                                    toCalendarDays = 360,
                                    rateDecimal = BigDecimal("0.20"),
                                ),
                            bracketTable =
                                listOf(
                                    TaxBracket(
                                        fromCalendarDays = 181,
                                        toCalendarDays = 360,
                                        rateDecimal = BigDecimal("0.20"),
                                    ),
                                ),
                        ),
                ),
        )

    private fun mockResultDto(): FixedIncomeComparisonResultDTO =
        FixedIncomeComparisonResultDTO(
            summary =
                FixedIncomeSummaryDTO(
                    baseProduct = "CDB",
                    baseProductGroup = "TAXABLE_IR",
                    baseRateModel = "FIXED",
                    baseEquivalentRateBps = 1200,
                    baseRateDisplay = "12.00% fixed",
                    principal = 10000.0,
                    gross = 11200.0,
                    ir = 180.0,
                    net = 11020.0,
                    realGainPercent = 10.2,
                ),
            rows =
                listOf(
                    FixedIncomeComparisonRowDTO(
                        label = "TAXABLE_IR / FIXED (base)",
                        productGroup = "TAXABLE_IR",
                        rateModel = "FIXED",
                        equivalentRateBps = 1200,
                        contractedRateDisplay = "12.00% fixed",
                        gross = 11200.0,
                        ir = 180.0,
                        net = 11020.0,
                        realGainPercent = 10.2,
                        isExempt = false,
                        isBase = true,
                    ),
                ),
            performanceSeries =
                listOf(
                    FixedIncomePerformanceSeriesDTO(
                        productGroup = "TAXABLE_IR",
                        points = listOf(FixedIncomePerformancePointDTO(LocalDate.of(2024, 1, 1), 10000.0)),
                    ),
                ),
            methodology =
                FixedIncomeMethodologyDTO(
                    dayCount =
                        FixedIncomeDayCountMethodologyDTO(
                            accrualConvention = "BUSINESS_252",
                            calendarDays = 365,
                            businessDays = 252,
                        ),
                    tax =
                        FixedIncomeTaxMethodologyDTO(
                            appliesOnProfitOnly = true,
                            activeBracket = FixedIncomeTaxBracketDTO(181, 360, 0.2),
                            bracketTable = listOf(FixedIncomeTaxBracketDTO(181, 360, 0.2)),
                        ),
                ),
        )
}
