package io.github.salomax.neotool.financialdata.graphql.mapper

import io.github.salomax.neotool.financialdata.domain.fixedincome.ComparisonRow
import io.github.salomax.neotool.financialdata.domain.fixedincome.DayCountMethodology
import io.github.salomax.neotool.financialdata.domain.fixedincome.FixedIncomeComparisonResult
import io.github.salomax.neotool.financialdata.domain.fixedincome.FixedIncomeSummary
import io.github.salomax.neotool.financialdata.domain.fixedincome.Methodology
import io.github.salomax.neotool.financialdata.domain.fixedincome.PerformancePoint
import io.github.salomax.neotool.financialdata.domain.fixedincome.PerformanceSeries
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
import jakarta.inject.Singleton

@Singleton
class FixedIncomeComparisonMapper {
    fun toResultDTO(result: FixedIncomeComparisonResult): FixedIncomeComparisonResultDTO =
        FixedIncomeComparisonResultDTO(
            summary = toSummaryDTO(result.summary),
            rows = result.rows.map(::toRowDTO),
            performanceSeries = result.performanceSeries.map(::toPerformanceSeriesDTO),
            methodology = toMethodologyDTO(result.methodology),
        )

    private fun toSummaryDTO(summary: FixedIncomeSummary): FixedIncomeSummaryDTO =
        FixedIncomeSummaryDTO(
            baseProduct = summary.baseProduct.name,
            baseProductGroup = summary.baseProductGroup.name,
            baseRateModel = summary.baseRateModel.name,
            baseEquivalentRateBps = summary.baseEquivalentRateBps,
            baseRateDisplay = summary.baseRateDisplay,
            principal = summary.principal.toDouble(),
            gross = summary.gross.toDouble(),
            ir = summary.ir.toDouble(),
            net = summary.net.toDouble(),
            realGainPercent = summary.realGainPercent.toDouble(),
        )

    private fun toRowDTO(row: ComparisonRow): FixedIncomeComparisonRowDTO =
        FixedIncomeComparisonRowDTO(
            label = row.label,
            productGroup = row.productGroup.name,
            rateModel = row.rateModel.name,
            equivalentRateBps = row.equivalentRateBps,
            contractedRateDisplay = row.contractedRateDisplay,
            gross = row.gross.toDouble(),
            ir = row.ir.toDouble(),
            net = row.net.toDouble(),
            realGainPercent = row.realGainPercent.toDouble(),
            isExempt = row.isExempt,
            isBase = row.isBase,
        )

    private fun toPerformanceSeriesDTO(series: PerformanceSeries): FixedIncomePerformanceSeriesDTO =
        FixedIncomePerformanceSeriesDTO(
            productGroup = series.productGroup.name,
            points = series.points.map(::toPerformancePointDTO),
        )

    private fun toPerformancePointDTO(point: PerformancePoint): FixedIncomePerformancePointDTO =
        FixedIncomePerformancePointDTO(
            date = point.date,
            netValue = point.netValue.toDouble(),
        )

    private fun toMethodologyDTO(m: Methodology): FixedIncomeMethodologyDTO =
        FixedIncomeMethodologyDTO(
            dayCount = toDayCountMethodologyDTO(m.dayCount),
            tax = toTaxMethodologyDTO(m.tax),
        )

    private fun toDayCountMethodologyDTO(dayCount: DayCountMethodology): FixedIncomeDayCountMethodologyDTO =
        FixedIncomeDayCountMethodologyDTO(
            accrualConvention = dayCount.accrualConvention,
            calendarDays = dayCount.calendarDays,
            businessDays = dayCount.businessDays,
        )

    private fun toTaxMethodologyDTO(tax: TaxMethodology): FixedIncomeTaxMethodologyDTO =
        FixedIncomeTaxMethodologyDTO(
            appliesOnProfitOnly = tax.appliesOnProfitOnly,
            activeBracket = toTaxBracketDTO(tax.activeBracket),
            bracketTable = tax.bracketTable.map(::toTaxBracketDTO),
        )

    private fun toTaxBracketDTO(bracket: TaxBracket): FixedIncomeTaxBracketDTO =
        FixedIncomeTaxBracketDTO(
            fromCalendarDays = bracket.fromCalendarDays,
            toCalendarDays = bracket.toCalendarDays,
            rateDecimal = bracket.rateDecimal.toDouble(),
        )
}
