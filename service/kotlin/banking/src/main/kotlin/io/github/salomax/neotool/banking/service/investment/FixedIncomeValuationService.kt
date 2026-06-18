package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.CouponFrequency
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.Indexer
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.model.dto.investment.HoldingValuationResult
import io.github.salomax.neotool.common.finance.tax.brazil.fixedincome.BrazilianFixedIncomeTaxRules
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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
        asOfDate: LocalDate = clock(),
        allowRateProjectionPastSeries: Boolean = false,
        transactions: List<InvestmentTransaction> = emptyList(),
    ): HoldingValuationResult {
        if (holding.quantity <= BigDecimal.ZERO || holding.averageCost <= BigDecimal.ZERO) {
            logger.debug { "Holding ${holding.id}: quantity or averageCost <= 0 → UNAVAILABLE" }
            return unavailable()
        }

        val indexer =
            holding.indexer ?: run {
                logger.debug { "Holding ${holding.id}: indexer is null → UNAVAILABLE" }
                return unavailable()
            }
        val issueDate =
            holding.issueDate ?: run {
                logger.debug {
                    "Holding ${holding.id}: issueDate is null → UNAVAILABLE " +
                        "(set issueDate or record with tradeDate so it defaults)"
                }
                return unavailable()
            }
        val accrualContext = resolveAccrualContext(holding, issueDate, asOfDate, transactions)

        val today = asOfDate
        val endDate =
            holding.maturityDate?.let { maturity ->
                if (today.isAfter(maturity)) maturity else today
            } ?: today

        if (!endDate.isAfter(accrualContext.accrualStart)) {
            val principal = accrualContext.principal
            return HoldingValuationResult(
                grossValue = principal,
                incomeTax = BigDecimal.ZERO,
                iof = BigDecimal.ZERO,
                netValue = principal,
                valuationSource = valuationSourceFor(indexer),
                valuationReferenceDate = valuationReferenceDate,
            )
        }

        val calendarDays = calendarDaysBetween(accrualContext.accrualStart, endDate)
        val businessDays = businessDaysBetween(accrualContext.accrualStart, endDate)
        val principal = accrualContext.principal

        val grossValue =
            when (indexer) {
                Indexer.PRE_FIXADO ->
                    accrualPreFixed(
                        principal = principal,
                        annualRate = holding.fixedAnnualRate ?: return unavailable(),
                        businessDays = businessDays,
                    )

                Indexer.CDI,
                Indexer.DI,
                -> {
                    val indexerPct =
                        holding.indexerPercentage
                            ?: run {
                                logger.warn {
                                    "Holding ${holding.id}: indexerPercentage is null for $indexer; " +
                                        "defaulting to 100%. Consider fixing data quality."
                                }
                                BigDecimal.ONE
                            }

                    fun fallbackDailyAccrual(): BigDecimal? {
                        val annualRate = indicatorRates[indexer] ?: return null
                        return accrualDailyIndexed(
                            principal = principal,
                            annualRate = annualRate,
                            indexerPercentage = indexerPct,
                            businessDays = businessDays,
                        )
                    }
                    val historicalSeries = monthlySeries[indexer]
                    if (!historicalSeries.isNullOrEmpty()) {
                        val historicalCoverageEnd =
                            minOf(endDate, monthlySeriesCoverageEnd(historicalSeries) ?: endDate)
                        val historicalGross =
                            accrualMonthlyIndexed(
                                principal = principal,
                                monthlySeries = historicalSeries,
                                indexerPercentage = indexerPct,
                                startDate = accrualContext.accrualStart,
                                endDate = historicalCoverageEnd,
                            )
                        if (historicalGross != null) {
                            if (allowRateProjectionPastSeries && endDate.isAfter(historicalCoverageEnd)) {
                                val annualRate =
                                    indicatorRates[indexer] ?: run {
                                        logger.debug {
                                            "Holding ${holding.id}: no $indexer rate; " +
                                                "mapSize=${indicatorRates.size} → UNAVAILABLE"
                                        }
                                        return unavailable()
                                    }
                                accrualDailyIndexed(
                                    principal = historicalGross,
                                    annualRate = annualRate,
                                    indexerPercentage = indexerPct,
                                    businessDays = businessDaysBetween(historicalCoverageEnd, endDate),
                                )
                            } else {
                                historicalGross
                            }
                        } else {
                            fallbackDailyAccrual() ?: run {
                                logger.debug {
                                    "Holding ${holding.id}: no $indexer rate; " +
                                        "mapSize=${indicatorRates.size} → UNAVAILABLE"
                                }
                                return unavailable()
                            }
                        }
                    } else {
                        fallbackDailyAccrual() ?: run {
                            logger.debug {
                                "Holding ${holding.id}: no $indexer rate; " +
                                    "mapSize=${indicatorRates.size} → UNAVAILABLE"
                            }
                            return unavailable()
                        }
                    }
                }

                Indexer.SELIC,
                Indexer.IPCA,
                Indexer.IGP_M,
                Indexer.IGP_DI,
                Indexer.INPC,
                Indexer.TR,
                -> {
                    val spreadRate = holding.fixedAnnualRate ?: holding.indexerPercentage ?: BigDecimal.ZERO

                    fun fallbackIndexedAccrual(): BigDecimal? {
                        val indicatorRate = indicatorRates[indexer] ?: return null
                        val indexedGross = accrualPreFixed(principal, indicatorRate, businessDays)
                        val spreadGross = accrualPreFixed(principal, spreadRate, businessDays)
                        return combineGrossValues(
                            principal = principal,
                            indexedGross = indexedGross,
                            spreadGross = spreadGross,
                        )
                    }
                    val series = monthlySeries[indexer]
                    if (series != null && series.isNotEmpty()) {
                        val historicalCoverageEnd = minOf(endDate, monthlySeriesCoverageEnd(series) ?: endDate)
                        val indexedGrossWithSeries =
                            if (indexer == Indexer.SELIC) {
                                accrualMonthlyIndexed(
                                    principal = principal,
                                    monthlySeries = series,
                                    indexerPercentage = BigDecimal.ONE,
                                    startDate = accrualContext.accrualStart,
                                    endDate = historicalCoverageEnd,
                                )
                            } else {
                                principal.multiply(
                                    fullMonthFactor(
                                        series = series,
                                        issueDate = accrualContext.accrualStart,
                                        endDate = historicalCoverageEnd,
                                    ),
                                    mathContext,
                                )
                            }
                        val indexedGross =
                            if (indexedGrossWithSeries != null) {
                                if (allowRateProjectionPastSeries && endDate.isAfter(historicalCoverageEnd)) {
                                    val indicatorRate =
                                        indicatorRates[indexer] ?: run {
                                            logger.debug {
                                                "Holding ${holding.id}: no indicator rate for $indexer " +
                                                    "(map size=${indicatorRates.size}) → UNAVAILABLE"
                                            }
                                            return unavailable()
                                        }
                                    accrualPreFixed(
                                        principal = indexedGrossWithSeries,
                                        annualRate = indicatorRate,
                                        businessDays = businessDaysBetween(historicalCoverageEnd, endDate),
                                    )
                                } else {
                                    indexedGrossWithSeries
                                }
                            } else {
                                null
                            }
                        if (indexedGross == null) {
                            fallbackIndexedAccrual() ?: run {
                                logger.debug {
                                    "Holding ${holding.id}: no $indexer rate; " +
                                        "mapSize=${indicatorRates.size} → UNAVAILABLE"
                                }
                                return unavailable()
                            }
                        } else {
                            val spreadGross = accrualPreFixed(principal, spreadRate, businessDays)
                            combineGrossValues(
                                principal = principal,
                                indexedGross = indexedGross,
                                spreadGross = spreadGross,
                            )
                        }
                    } else {
                        fallbackIndexedAccrual() ?: run {
                            logger.debug {
                                "Holding ${holding.id}: no $indexer rate; " +
                                    "mapSize=${indicatorRates.size} → UNAVAILABLE"
                            }
                            return unavailable()
                        }
                    }
                }

                Indexer.OUTROS ->
                    accrualPreFixed(
                        principal = principal,
                        annualRate = holding.fixedAnnualRate ?: return unavailable(),
                        businessDays = businessDays,
                    )

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

    private fun principal(holding: Holding): BigDecimal = holding.quantity.multiply(holding.averageCost, mathContext)

    private data class AccrualContext(
        val accrualStart: LocalDate,
        val principal: BigDecimal,
    )

    private fun resolveAccrualContext(
        holding: Holding,
        issueDate: LocalDate,
        asOfDate: LocalDate,
        transactions: List<InvestmentTransaction>,
    ): AccrualContext {
        val couponFrequency = holding.couponFrequency
        val basePrincipal = principal(holding)
        if (couponFrequency == null || couponFrequency == CouponFrequency.AT_MATURITY) {
            return AccrualContext(
                accrualStart = issueDate,
                principal = basePrincipal,
            )
        }

        val lastInterestPayment =
            transactions
                .asSequence()
                .filter { tx -> tx.type == InvestmentTransactionType.INTEREST_PAYMENT }
                .filter { tx -> !tx.tradeDate.isAfter(asOfDate) }
                .maxByOrNull { tx -> tx.tradeDate }

        return if (lastInterestPayment != null) {
            AccrualContext(
                accrualStart = lastInterestPayment.tradeDate,
                principal = basePrincipal,
            )
        } else {
            AccrualContext(
                accrualStart = issueDate,
                principal = basePrincipal,
            )
        }
    }

    private fun valuationSourceFor(indexer: Indexer): ValuationSource =
        when (indexer) {
            Indexer.PRE_FIXADO,
            Indexer.OUTROS,
            -> ValuationSource.CALCULATED
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

    private fun accrualMonthlyIndexed(
        principal: BigDecimal,
        monthlySeries: List<Pair<LocalDate, BigDecimal>>,
        indexerPercentage: BigDecimal,
        startDate: LocalDate,
        endDate: LocalDate,
    ): BigDecimal? {
        if (!endDate.isAfter(startDate)) return principal

        var applied = false
        var factor = BigDecimal.ONE

        for ((referenceDate, monthlyRate) in monthlySeries.sortedBy { it.first }) {
            val monthStart = referenceDate.withDayOfMonth(1)
            val monthEnd = monthStart.plusMonths(1)
            if (!monthEnd.isAfter(startDate) || !endDate.isAfter(monthStart)) continue

            val accrualStart = if (startDate.isAfter(monthStart)) startDate else monthStart
            val accrualEnd = if (endDate.isBefore(monthEnd)) endDate else monthEnd
            if (!accrualEnd.isAfter(accrualStart)) continue

            val monthBusinessDays = weekdaysBetween(monthStart, monthEnd)
            val accrualBusinessDays = weekdaysBetween(accrualStart, accrualEnd)
            if (monthBusinessDays <= 0 || accrualBusinessDays <= 0) continue

            val monthlyWithPct = monthlyRate.multiply(indexerPercentage, mathContext)
            val monthFactor = BigDecimal.ONE.add(monthlyWithPct, mathContext).toDouble()
            val fraction = accrualBusinessDays.toDouble() / monthBusinessDays.toDouble()
            factor = factor.multiply(BigDecimal.valueOf(Math.pow(monthFactor, fraction)), mathContext)
            applied = true
        }

        return if (applied) principal.multiply(factor, mathContext) else null
    }

    private fun weekdaysBetween(
        start: LocalDate,
        end: LocalDate,
    ): Int {
        if (!end.isAfter(start)) return 0
        var cursor = start
        var days = 0
        while (cursor.isBefore(end)) {
            if (cursor.dayOfWeek.value <= 5) days += 1
            cursor = cursor.plusDays(1)
        }
        return days
    }

    private fun monthlySeriesCoverageEnd(series: List<Pair<LocalDate, BigDecimal>>): LocalDate? =
        series.maxOfOrNull { (referenceDate, _) ->
            referenceDate.withDayOfMonth(1).plusMonths(1)
        }

    private fun fullMonthFactor(
        series: List<Pair<LocalDate, BigDecimal>>,
        issueDate: LocalDate,
        endDate: LocalDate,
    ): BigDecimal {
        val startOfIssueMonth = issueDate.withDayOfMonth(1)
        val startOfEndMonth = endDate.withDayOfMonth(1)
        return series
            .filter { (refDate, _) -> !refDate.isBefore(startOfIssueMonth) && !refDate.isAfter(startOfEndMonth) }
            .fold(BigDecimal.ONE) { acc, (_, rate) ->
                acc.multiply(BigDecimal.ONE.add(rate, mathContext), mathContext)
            }
    }

    private fun combineGrossValues(
        principal: BigDecimal,
        indexedGross: BigDecimal,
        spreadGross: BigDecimal,
    ): BigDecimal =
        indexedGross
            .add(spreadGross, mathContext)
            .subtract(principal, mathContext)

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
