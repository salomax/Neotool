package io.github.salomax.neotool.banking.domain.investment

import io.github.salomax.neotool.banking.util.DateSequenceGenerator
import jakarta.inject.Singleton
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

private data class ScheduleRow(
    val scheduledDate: LocalDate,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val isPartialPeriod: Boolean,
)

@Singleton
class CouponScheduleService {
    fun generateSchedule(
        holding: Holding,
        assetType: AssetType,
        transactions: List<InvestmentTransaction>,
        today: LocalDate = LocalDate.now(),
    ): List<CouponScheduleEntry> {
        val frequency = holding.couponFrequency
        val issueDate = holding.issueDate ?: return emptyList()
        val maturityDate = holding.maturityDate ?: return emptyList()
        if (frequency == null || frequency == CouponFrequency.AT_MATURITY) return emptyList()
        if (!maturityDate.isAfter(issueDate)) return emptyList()

        val rows =
            when {
                assetType == AssetType.TREASURY &&
                    frequency == CouponFrequency.SEMI_ANNUAL &&
                    holding.indexer == Indexer.IPCA ->
                    ntnbRows(issueDate, maturityDate)
                assetType == AssetType.TREASURY &&
                    frequency == CouponFrequency.SEMI_ANNUAL &&
                    holding.indexer == Indexer.PRE_FIXADO ->
                    ntnfRows(issueDate, maturityDate)
                assetType in CouponDayOfMonthRules.TESOURO_ASSET_TYPES ->
                    legacyRows(holding, frequency, issueDate, maturityDate)
                else ->
                    privateCreditRows(holding, frequency, issueDate, maturityDate)
            }
        if (rows.isEmpty()) return emptyList()

        val interestPayments = transactions.filter { it.type == InvestmentTransactionType.INTEREST_PAYMENT }

        return rows.mapIndexed { index, row ->
            val isPast = row.scheduledDate.isBefore(today)
            val matchedTransaction =
                if (isPast) {
                    interestPayments.firstOrNull { tx ->
                        abs(ChronoUnit.DAYS.between(tx.tradeDate, row.scheduledDate)) <= 7
                    }
                } else {
                    null
                }
            CouponScheduleEntry(
                seriesNumber = index + 1,
                scheduledDate = row.scheduledDate,
                periodStart = row.periodStart,
                periodEnd = row.periodEnd,
                isPartialPeriod = row.isPartialPeriod,
                isPast = isPast,
                matchedTransaction = matchedTransaction,
            )
        }
    }

    fun findMatchingEntry(
        holding: Holding,
        assetType: AssetType,
        transactions: List<InvestmentTransaction>,
        tradeDate: LocalDate,
    ): CouponScheduleEntry? =
        generateSchedule(holding, assetType, transactions, today = tradeDate)
            .firstOrNull { entry ->
                abs(ChronoUnit.DAYS.between(tradeDate, entry.scheduledDate)) <= 7
            }

    /** BR-P2-1: calendar-year parity selects Feb/Aug vs May/Nov; first payment strictly after issue; exclude maturity date. */
    private fun ntnbRows(
        issue: LocalDate,
        maturity: LocalDate,
    ): List<ScheduleRow> {
        val payments =
            buildList {
                var y = issue.year
                while (y <= maturity.year) {
                    val windows =
                        if (y % 2 == 0) {
                            listOf(LocalDate.of(y, Month.FEBRUARY, 15), LocalDate.of(y, Month.AUGUST, 15))
                        } else {
                            listOf(LocalDate.of(y, Month.MAY, 15), LocalDate.of(y, Month.NOVEMBER, 15))
                        }
                    addAll(windows)
                    y++
                }
            }
                .distinct()
                .sorted()
                .filter { it.isAfter(issue) && it.isBefore(maturity) }

        if (payments.isEmpty()) return emptyList()
        return payments.mapIndexed { index, scheduled ->
            val periodStart =
                if (index == 0) {
                    issue
                } else {
                    payments[index - 1].plusDays(1)
                }
            ScheduleRow(
                scheduledDate = scheduled,
                periodStart = periodStart,
                periodEnd = scheduled,
                isPartialPeriod = false,
            )
        }
    }

    /** BR-P2-2: Jan 1 / Jul 1; first strictly after issue; exclude maturity date. */
    private fun ntnfRows(
        issue: LocalDate,
        maturity: LocalDate,
    ): List<ScheduleRow> {
        val payments =
            buildList {
                var y = issue.year
                while (y <= maturity.year) {
                    for (m in listOf(Month.JANUARY, Month.JULY)) {
                        val d = LocalDate.of(y, m, 1)
                        if (d.isAfter(issue) && d.isBefore(maturity)) {
                            add(d)
                        }
                    }
                    y++
                }
            }.sorted()

        if (payments.isEmpty()) return emptyList()
        return payments.mapIndexed { index, scheduled ->
            val periodStart =
                if (index == 0) {
                    issue
                } else {
                    payments[index - 1].plusDays(1)
                }
            ScheduleRow(
                scheduledDate = scheduled,
                periodStart = periodStart,
                periodEnd = scheduled,
                isPartialPeriod = false,
            )
        }
    }

    /** Phase-1 style sequence for generic Tesouro and non-anchored combinations. */
    private fun legacyRows(
        holding: Holding,
        frequency: CouponFrequency,
        issueDate: LocalDate,
        maturityDate: LocalDate,
    ): List<ScheduleRow> {
        val dates =
            DateSequenceGenerator.generate(
                start = issueDate,
                end = maturityDate,
                stepMonths = frequency.toStepMonths(),
            )
        if (dates.isEmpty()) return emptyList()
        return dates.mapIndexed { index, scheduledDate ->
            val periodStart = if (index == 0) issueDate else dates[index - 1]
            ScheduleRow(
                scheduledDate = scheduledDate,
                periodStart = periodStart,
                periodEnd = scheduledDate,
                isPartialPeriod = false,
            )
        }
    }

    /** BR-P2-3: payment on coupon day in month after accrual period end. */
    private fun privateCreditRows(
        holding: Holding,
        frequency: CouponFrequency,
        issue: LocalDate,
        maturity: LocalDate,
    ): List<ScheduleRow> {
        val couponDay = holding.couponDayOfMonth ?: return emptyList()
        val stepMonths = frequency.toStepMonths()
        val rows = mutableListOf<ScheduleRow>()
        var periodStart = issue
        var periodEnd = issue.withDayOfMonth(1).plusMonths(stepMonths).minusDays(1)
        var index = 0
        while (true) {
            val scheduled = paymentInMonthAfterPeriodEnd(periodEnd, couponDay)
            if (!scheduled.isBefore(maturity)) {
                break
            }
            val isPartial = index == 0 && issue.dayOfMonth != 1
            rows.add(
                ScheduleRow(
                    scheduledDate = scheduled,
                    periodStart = periodStart,
                    periodEnd = periodEnd,
                    isPartialPeriod = isPartial,
                ),
            )
            index++
            periodStart = periodEnd.plusDays(1)
            if (!periodStart.isBefore(maturity)) {
                break
            }
            periodEnd = periodStart.withDayOfMonth(1).plusMonths(stepMonths).minusDays(1)
        }
        return rows
    }

    private fun paymentInMonthAfterPeriodEnd(
        periodEnd: LocalDate,
        couponDay: Int,
    ): LocalDate {
        val ym = YearMonth.from(periodEnd).plusMonths(1)
        val dom = couponDay.coerceAtMost(ym.lengthOfMonth())
        return ym.atDay(dom)
    }
}
