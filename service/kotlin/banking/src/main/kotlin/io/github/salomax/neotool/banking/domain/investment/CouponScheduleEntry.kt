package io.github.salomax.neotool.banking.domain.investment

import java.time.LocalDate

data class CouponScheduleEntry(
    /** 1-based index in the projected schedule (BR-P2-4). */
    val seriesNumber: Int,
    val scheduledDate: LocalDate,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    /** True when the first accrual period is shorter than a full cycle (BR-P2-3). */
    val isPartialPeriod: Boolean,
    val isPast: Boolean,
    val matchedTransaction: InvestmentTransaction? = null,
)
