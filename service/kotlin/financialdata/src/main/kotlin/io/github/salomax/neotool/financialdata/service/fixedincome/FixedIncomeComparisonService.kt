package io.github.salomax.neotool.financialdata.service.fixedincome

import io.github.salomax.neotool.common.tracing.TraceExecution
import io.github.salomax.neotool.financialdata.domain.fixedincome.ComparisonRow
import io.github.salomax.neotool.financialdata.domain.fixedincome.DayCountMethodology
import io.github.salomax.neotool.financialdata.domain.fixedincome.FixedIncomeComparisonInput
import io.github.salomax.neotool.financialdata.domain.fixedincome.FixedIncomeComparisonResult
import io.github.salomax.neotool.financialdata.domain.fixedincome.FixedIncomeInvalidEffectiveRateException
import io.github.salomax.neotool.financialdata.domain.fixedincome.FixedIncomeSummary
import io.github.salomax.neotool.financialdata.domain.fixedincome.Methodology
import io.github.salomax.neotool.financialdata.domain.fixedincome.PerformancePoint
import io.github.salomax.neotool.financialdata.domain.fixedincome.PerformanceSeries
import io.github.salomax.neotool.financialdata.domain.fixedincome.ProductGroup
import io.github.salomax.neotool.financialdata.domain.fixedincome.RateModel
import io.github.salomax.neotool.financialdata.domain.fixedincome.TaxBracket
import io.github.salomax.neotool.financialdata.domain.fixedincome.TaxMethodology
import io.github.salomax.neotool.financialdata.domain.fixedincome.bpsToPercent
import io.github.salomax.neotool.financialdata.domain.fixedincome.defaultProductForGroup
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private const val SCALE = 10
private val HUNDRED = BigDecimal("100")

/**
 * Single entry point for fixed income comparison: orchestrates tax, day count, rate calc, and solver.
 * Produces one row per (product group × rate model), with base scenario first.
 */
@TraceExecution
@Singleton
class FixedIncomeComparisonService(
    private val taxPolicyService: TaxPolicyService,
    private val dayCountService: DayCountService,
    private val equivalentYieldSolver: EquivalentYieldSolver,
) {
    fun compare(input: FixedIncomeComparisonInput): FixedIncomeComparisonResult {
        val (baseGross, baseIr, baseNet) = equivalentYieldSolver.computeGrossIrNet(input)
        val calendarDays = ChronoUnit.DAYS.between(input.startDate, input.endDate).toInt()
        val businessDays = dayCountService.businessDays(calendarDays)
        val baseGroup = input.productGroup
        val baseProduct = input.baseProduct()
        val baseRateModel = input.rateModel
        val baseEquivalentRateBps = input.contractedRateBps!!
        val baseRateDisplay = contractedRateDisplay(input)
        val baseRealGain = realGainPercent(input.principal, baseNet)

        val equivalentRows = mutableListOf<ComparisonRow>()
        for (group in ProductGroup.entries) {
            for (rateModel in RateModel.entries) {
                if (group == baseGroup && rateModel == baseRateModel) {
                    continue
                }
                val row =
                    solveEquivalentRow(
                        input = input,
                        baseNet = baseNet,
                        group = group,
                        rateModel = rateModel,
                    ) ?: continue
                equivalentRows.add(row)
            }
        }

        val baseRow =
            ComparisonRow(
                label = "$baseGroup / ${baseRateModel.name} (base)",
                productGroup = baseGroup,
                rateModel = baseRateModel,
                equivalentRateBps = baseEquivalentRateBps,
                contractedRateDisplay = baseRateDisplay,
                gross = baseGross,
                ir = baseIr,
                net = baseNet,
                realGainPercent = baseRealGain,
                isExempt = baseGroup == ProductGroup.EXEMPT_IR,
                isBase = true,
            )
        val rows = listOf(baseRow) + equivalentRows
        val performanceSeries = buildPerformanceSeries(input, rows)
        val activeBracket = taxBracketForDays(calendarDays)
        val bracketTable = taxBracketTable()
        val methodology =
            Methodology(
                dayCount =
                    DayCountMethodology(
                        accrualConvention = "BUSINESS_252",
                        calendarDays = calendarDays,
                        businessDays = businessDays,
                    ),
                tax =
                    TaxMethodology(
                        appliesOnProfitOnly = true,
                        activeBracket = activeBracket,
                        bracketTable = bracketTable,
                    ),
            )
        val summary =
            FixedIncomeSummary(
                baseProduct = baseProduct,
                baseProductGroup = baseGroup,
                baseRateModel = baseRateModel,
                baseEquivalentRateBps = baseEquivalentRateBps,
                baseRateDisplay = baseRateDisplay,
                principal = input.principal.setScale(SCALE, RoundingMode.HALF_UP),
                gross = baseGross,
                ir = baseIr,
                net = baseNet,
                realGainPercent = baseRealGain,
            )

        return FixedIncomeComparisonResult(
            summary = summary,
            rows = rows,
            performanceSeries = performanceSeries,
            methodology = methodology,
        )
    }

    private fun taxBracketTable(): List<TaxBracket> =
        listOf(
            TaxBracket(1, 180, BigDecimal("0.225").setScale(SCALE, RoundingMode.HALF_UP)),
            TaxBracket(181, 360, BigDecimal("0.20").setScale(SCALE, RoundingMode.HALF_UP)),
            TaxBracket(361, 720, BigDecimal("0.175").setScale(SCALE, RoundingMode.HALF_UP)),
            TaxBracket(721, null, BigDecimal("0.15").setScale(SCALE, RoundingMode.HALF_UP)),
        )

    private fun taxBracketForDays(calendarDays: Int): TaxBracket =
        when {
            calendarDays <= 180 -> TaxBracket(1, 180, taxPolicyService.irBracketRate(calendarDays))
            calendarDays <= 360 -> TaxBracket(181, 360, taxPolicyService.irBracketRate(calendarDays))
            calendarDays <= 720 -> TaxBracket(361, 720, taxPolicyService.irBracketRate(calendarDays))
            else -> TaxBracket(721, null, taxPolicyService.irBracketRate(calendarDays))
        }

    private fun buildPerformanceSeries(
        input: FixedIncomeComparisonInput,
        rows: List<ComparisonRow>,
    ): List<PerformanceSeries> {
        val dates = timelineDates(input.startDate, input.endDate)
        return ProductGroup.entries.mapNotNull { group ->
            val seriesRate =
                if (group == input.productGroup) {
                    input.contractedRateBps
                } else {
                    rows.firstOrNull { row ->
                        row.productGroup == group && row.rateModel == input.rateModel
                    }?.equivalentRateBps
                } ?: return@mapNotNull null

            val groupedInput =
                inputForGroup(input, group).copy(
                    rateModel = input.rateModel,
                    contractedRateBps = seriesRate,
                )
            val points =
                dates.map { date ->
                    if (date == groupedInput.startDate) {
                        PerformancePoint(
                            date = date,
                            netValue = groupedInput.principal.setScale(SCALE, RoundingMode.HALF_UP),
                        )
                    } else {
                        val pointInput = groupedInput.copy(endDate = date)
                        val netValue = equivalentYieldSolver.computeGrossIrNet(pointInput).third
                        PerformancePoint(date = date, netValue = netValue)
                    }
                }
            PerformanceSeries(productGroup = group, points = points)
        }
    }

    private fun timelineDates(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<LocalDate> {
        val dates = mutableListOf(startDate)
        var cursor = startDate.plusMonths(1)
        while (cursor.isBefore(endDate)) {
            dates.add(cursor)
            cursor = cursor.plusMonths(1)
        }
        if (endDate != startDate) dates.add(endDate)
        return dates
    }

    private fun solveEquivalentRow(
        input: FixedIncomeComparisonInput,
        baseNet: BigDecimal,
        group: ProductGroup,
        rateModel: RateModel,
    ): ComparisonRow? {
        val baseForGroup = inputForGroup(input, group)
        val equivalentRateBps =
            equivalentYieldSolver.solveEquivalentRate(
                baseForGroup,
                baseNet,
                rateModel,
            ) ?: return null
        val synthetic = buildSyntheticInput(baseForGroup, rateModel, equivalentRateBps)
        val (gross, ir, net) =
            try {
                equivalentYieldSolver.computeGrossIrNet(synthetic)
            } catch (_: FixedIncomeInvalidEffectiveRateException) {
                return null
            }
        return ComparisonRow(
            label = "$group / ${rateModel.name}",
            productGroup = group,
            rateModel = rateModel,
            equivalentRateBps = equivalentRateBps,
            contractedRateDisplay = equivalentRateDisplay(rateModel, equivalentRateBps),
            gross = gross,
            ir = ir,
            net = net,
            realGainPercent = realGainPercent(input.principal, net),
            isExempt = group == ProductGroup.EXEMPT_IR,
            isBase = false,
        )
    }

    private fun inputForGroup(
        input: FixedIncomeComparisonInput,
        group: ProductGroup,
    ): FixedIncomeComparisonInput {
        if (input.productGroup == group) {
            return if (input.product == null) {
                input.copy(product = defaultProductForGroup(group))
            } else {
                input
            }
        }
        return input.copy(productGroup = group, product = defaultProductForGroup(group))
    }

    private fun realGainPercent(
        principal: BigDecimal,
        net: BigDecimal,
    ): BigDecimal {
        if (principal == BigDecimal.ZERO) return BigDecimal.ZERO
        val gain = net.subtract(principal).divide(principal, SCALE, RoundingMode.HALF_UP)
        return gain.multiply(HUNDRED).setScale(SCALE, RoundingMode.HALF_UP)
    }

    private fun contractedRateDisplay(input: FixedIncomeComparisonInput): String =
        when (input.rateModel) {
            RateModel.CDI -> "${bpsToPercent(input.contractedRateBps!!)}% CDI"
            RateModel.FIXED -> "${bpsToPercent(input.contractedRateBps!!)}% fixed"
            RateModel.IPCA_PLUS -> "IPCA+${bpsToPercent(input.contractedRateBps!!)}%"
        }

    private fun equivalentRateDisplay(
        rateModel: RateModel,
        rateBps: Int,
    ): String =
        when (rateModel) {
            RateModel.CDI -> "${bpsToPercent(rateBps)}% CDI"
            RateModel.FIXED -> "${bpsToPercent(rateBps)}% fixed"
            RateModel.IPCA_PLUS -> "IPCA+${bpsToPercent(rateBps)}%"
        }

    private fun buildSyntheticInput(
        base: FixedIncomeComparisonInput,
        targetRateModel: RateModel,
        candidateRateBps: Int,
    ): FixedIncomeComparisonInput =
        when (targetRateModel) {
            RateModel.CDI ->
                base.copy(
                    rateModel = RateModel.CDI,
                    contractedRateBps = candidateRateBps,
                    cdiAnnualRateBps = base.cdiAnnualRateBps,
                    ipcaAnnualRateBps = base.ipcaAnnualRateBps,
                )
            RateModel.FIXED ->
                base.copy(
                    rateModel = RateModel.FIXED,
                    contractedRateBps = candidateRateBps,
                    cdiAnnualRateBps = base.cdiAnnualRateBps,
                    ipcaAnnualRateBps = base.ipcaAnnualRateBps,
                )
            RateModel.IPCA_PLUS ->
                base.copy(
                    rateModel = RateModel.IPCA_PLUS,
                    contractedRateBps = candidateRateBps,
                    ipcaAnnualRateBps = base.ipcaAnnualRateBps,
                    cdiAnnualRateBps = base.cdiAnnualRateBps,
                )
        }
}
