package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.*
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class HoldingValuationResult(
    val grossValue: BigDecimal?,
    val incomeTax: BigDecimal?,
    val iof: BigDecimal?,
    val netValue: BigDecimal?,
    val valuationSource: ValuationSource,
    val valuationReferenceDate: LocalDate?,
)

enum class ValuationSource {
    PROVIDER,
    MARKET_DATA,
    MANUAL,
    CALCULATED,
    ESTIMATED,
    UNAVAILABLE,
}

@Singleton
class FixedIncomeValuationService(
    private val clock: () -> LocalDate = { LocalDate.now() },
) {
    private val logger = KotlinLogging.logger {}
    private val mathContext = MathContext(12, RoundingMode.HALF_UP)

    fun estimate(
        holding: Holding,
        indicatorRates: Map<Indexer, BigDecimal>,
        valuationReferenceDate: LocalDate? = null,
        monthlySeries: Map<Indexer, List<Pair<LocalDate, BigDecimal>>> = emptyMap(),
    ): HoldingValuationResult {
        if (holding.quantity <= BigDecimal.ZERO || holding.averageCost <= BigDecimal.ZERO) {
            logger.debug { "Holding ${holding.id}: quantity or averageCost <= 0 → UNAVAILABLE" }
            return unavailable()
        }

        val indexer = holding.indexer ?: run {
            logger.debug { "Holding ${holding.id}: indexer is null → UNAVAILABLE" }
            return unavailable()
        }
        val issueDate = holding.issueDate ?: run {
            logger.debug { "Holding ${holding.id}: issueDate is null → UNAVAILABLE (set issueDate or record with tradeDate so it defaults)" }
            return unavailable()
        }

        val today = clock()
        val endDate =
            holding.maturityDate?.let { maturity ->
                if (today.isAfter(maturity)) maturity else today
            } ?: today

        if (!endDate.isAfter(issueDate)) {
            val principal = principal(holding)
            return HoldingValuationResult(
                grossValue = principal,
                incomeTax = BigDecimal.ZERO,
                iof = BigDecimal.ZERO,
                netValue = principal,
                valuationSource = valuationSourceFor(indexer),
                valuationReferenceDate = valuationReferenceDate,
            )
        }

        val calendarDays = calendarDaysBetween(issueDate, endDate)
        val businessDays = businessDaysBetween(issueDate, endDate)
        val principal = principal(holding)

        val grossValue =
            when (indexer) {
                Indexer.PRE_FIXADO ->
                    accrualPreFixed(
                        principal = principal,
                        annualRate = holding.preFixedRate ?: return unavailable(),
                        businessDays = businessDays,
                    )

                Indexer.CDI,
                Indexer.DI,
                Indexer.SELIC,
                -> {
                    val annualRate = indicatorRates[indexer] ?: run {
                        logger.debug { "Holding ${holding.id}: no indicator rate for $indexer (map size=${indicatorRates.size}) → UNAVAILABLE" }
                        return unavailable()
                    }
                    val indexerPct =
                        holding.indexerPercentage
                            ?: run {
                                logger.warn { "Holding ${holding.id}: indexerPercentage is null for $indexer; defaulting to 100%. Consider fixing data quality." }
                                BigDecimal.ONE
                            }
                    accrualDailyIndexed(
                        principal = principal,
                        annualRate = annualRate,
                        indexerPercentage = indexerPct,
                        businessDays = businessDays,
                    )
                }

                Indexer.IPCA,
                Indexer.IGP_M,
                Indexer.IGP_DI,
                Indexer.INPC,
                Indexer.TR,
                -> {
                    val indexerPercentage = holding.indexerPercentage ?: BigDecimal.ZERO
                    val series = monthlySeries[indexer]
                    if (series != null && series.isNotEmpty()) {
                        // Accumulate actual monthly values between issueDate's month and endDate's month
                        val startOfIssueMonth = issueDate.withDayOfMonth(1)
                        val startOfEndMonth = endDate.withDayOfMonth(1)
                        val inflationFactor =
                            series
                                .filter { (refDate, _) -> !refDate.isBefore(startOfIssueMonth) && !refDate.isAfter(startOfEndMonth) }
                                .fold(BigDecimal.ONE) { acc, (_, rate) ->
                                    acc.multiply(BigDecimal.ONE.add(rate, mathContext), mathContext)
                                }
                        // Apply spread on top of inflation-adjusted principal
                        accrualPreFixed(
                            principal = principal.multiply(inflationFactor, mathContext),
                            annualRate = indexerPercentage,
                            businessDays = businessDays,
                        )
                    } else {
                        // Fallback: project using latest annual rate snapshot
                        val indicatorRate = indicatorRates[indexer] ?: run {
                            logger.debug { "Holding ${holding.id}: no indicator rate for $indexer (map size=${indicatorRates.size}) → UNAVAILABLE" }
                            return unavailable()
                        }
                        val effectiveAnnual =
                            if (indexerPercentage <= BigDecimal.ZERO) {
                                indicatorRate
                            } else {
                                (BigDecimal.ONE + indicatorRate)
                                    .multiply(BigDecimal.ONE + indexerPercentage, mathContext)
                                    .subtract(BigDecimal.ONE, mathContext)
                            }
                        accrualPreFixed(
                            principal = principal,
                            annualRate = effectiveAnnual,
                            businessDays = businessDays,
                        )
                    }
                }

                Indexer.OUTROS,
                Indexer.BCP,
                Indexer.TLC,
                -> return unavailable()
            }

        val gain = grossValue - principal
        if (gain <= BigDecimal.ZERO) {
            return HoldingValuationResult(
                grossValue = grossValue,
                incomeTax = BigDecimal.ZERO,
                iof = BigDecimal.ZERO,
                netValue = grossValue,
                valuationSource = valuationSourceFor(indexer),
                valuationReferenceDate = valuationReferenceDate,
            )
        }

        val iofAmount = BrazilianFixedIncomeTaxRules.computeIof(gain, calendarDays)
        val taxableGain = gain - iofAmount
        val incomeTax = BrazilianFixedIncomeTaxRules.computeIr(taxableGain, calendarDays, holding.isTaxExempt)

        val netValue = grossValue - iofAmount - incomeTax

        return HoldingValuationResult(
            grossValue = grossValue,
            incomeTax = incomeTax,
            iof = iofAmount,
            netValue = netValue,
            valuationSource = valuationSourceFor(indexer),
            valuationReferenceDate = valuationReferenceDate,
        )
    }

    fun businessDaysBetween(
        start: LocalDate,
        end: LocalDate,
    ): Int {
        val days = calendarDaysBetween(start, end)
        if (days <= 0) return 0
        val bdApprox = (days.toDouble() / 365.0) * 252.0
        return bdApprox.toInt()
    }

    fun calendarDaysBetween(
        start: LocalDate,
        end: LocalDate,
    ): Int {
        if (!end.isAfter(start)) return 0
        return ChronoUnit.DAYS.between(start, end).toInt()
    }

    private fun principal(holding: Holding): BigDecimal =
        holding.quantity.multiply(holding.averageCost, mathContext)

    private fun valuationSourceFor(indexer: Indexer): ValuationSource =
        when (indexer) {
            Indexer.PRE_FIXADO -> ValuationSource.CALCULATED
            else -> ValuationSource.ESTIMATED
        }

    private fun accrualPreFixed(
        principal: BigDecimal,
        annualRate: BigDecimal,
        businessDays: Int,
    ): BigDecimal {
        if (businessDays <= 0) return principal
        val exponent = businessDays.toDouble() / 252.0
        val base = BigDecimal.ONE.add(annualRate, mathContext).toDouble()
        val factor = Math.pow(base, exponent)
        return principal.multiply(BigDecimal.valueOf(factor), mathContext)
    }

    private fun accrualDailyIndexed(
        principal: BigDecimal,
        annualRate: BigDecimal,
        indexerPercentage: BigDecimal,
        businessDays: Int,
    ): BigDecimal {
        if (businessDays <= 0) return principal
        val onePlusAnnual = BigDecimal.ONE.add(annualRate, mathContext).toDouble()
        val daily = Math.pow(onePlusAnnual, 1.0 / 252.0) - 1.0
        val dailyWithPct = daily * indexerPercentage.toDouble()
        val factor = Math.pow(1.0 + dailyWithPct, businessDays.toDouble())
        return principal.multiply(BigDecimal.valueOf(factor), mathContext)
    }

    private fun unavailable(): HoldingValuationResult =
        HoldingValuationResult(
            grossValue = null,
            incomeTax = null,
            iof = null,
            netValue = null,
            valuationSource = ValuationSource.UNAVAILABLE,
            valuationReferenceDate = null,
        )

}
