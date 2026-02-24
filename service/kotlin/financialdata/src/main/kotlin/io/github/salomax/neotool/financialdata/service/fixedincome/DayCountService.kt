package io.github.salomax.neotool.financialdata.service.fixedincome

import jakarta.inject.Singleton

/**
 * Day count for fixed income: 252 business days per year.
 * business_days = round(calendar_days × 252 / 365).
 */
@Singleton
class DayCountService {
    fun businessDays(calendarDays: Int): Int {
        require(calendarDays > 0) { "calendarDays must be positive" }
        val bd = (calendarDays * 252.0 / 365.0)
        return kotlin.math.round(bd).toInt()
    }
}
