package io.github.salomax.neotool.financialdata.graphql.dto

import io.micronaut.core.annotation.Introspected
import java.time.LocalDate

@Introspected
data class FixedIncomeComparisonResultDTO(
    val summary: FixedIncomeSummaryDTO,
    val rows: List<FixedIncomeComparisonRowDTO>,
    val performanceSeries: List<FixedIncomePerformanceSeriesDTO>,
    val methodology: FixedIncomeMethodologyDTO,
)

@Introspected
data class FixedIncomeSummaryDTO(
    val baseProduct: String,
    val baseProductGroup: String,
    val baseRateModel: String,
    val baseEquivalentRateBps: Int,
    val baseRateDisplay: String,
    val principal: Double,
    val gross: Double,
    val ir: Double,
    val net: Double,
    val realGainPercent: Double,
)

@Introspected
data class FixedIncomeComparisonRowDTO(
    val label: String,
    val productGroup: String,
    val rateModel: String,
    val equivalentRateBps: Int,
    val contractedRateDisplay: String,
    val gross: Double,
    val ir: Double,
    val net: Double,
    val realGainPercent: Double,
    val isExempt: Boolean,
    val isBase: Boolean,
)

@Introspected
data class FixedIncomePerformanceSeriesDTO(
    val productGroup: String,
    val points: List<FixedIncomePerformancePointDTO>,
)

@Introspected
data class FixedIncomePerformancePointDTO(
    val date: LocalDate,
    val netValue: Double,
)

@Introspected
data class FixedIncomeMethodologyDTO(
    val dayCount: FixedIncomeDayCountMethodologyDTO,
    val tax: FixedIncomeTaxMethodologyDTO,
)

@Introspected
data class FixedIncomeDayCountMethodologyDTO(
    val accrualConvention: String,
    val calendarDays: Int,
    val businessDays: Int,
)

@Introspected
data class FixedIncomeTaxMethodologyDTO(
    val appliesOnProfitOnly: Boolean,
    val activeBracket: FixedIncomeTaxBracketDTO,
    val bracketTable: List<FixedIncomeTaxBracketDTO>,
)

@Introspected
data class FixedIncomeTaxBracketDTO(
    val fromCalendarDays: Int,
    val toCalendarDays: Int?,
    val rateDecimal: Double,
)
