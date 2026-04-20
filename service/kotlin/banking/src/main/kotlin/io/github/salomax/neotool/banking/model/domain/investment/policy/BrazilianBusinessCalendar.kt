package io.github.salomax.neotool.banking.model.domain.investment.policy

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month

object BrazilianBusinessCalendar {
    fun lastBusinessDay(
        year: Int,
        month: Month,
    ): LocalDate {
        var date = LocalDate.of(year, month, month.length(java.time.Year.isLeap(year.toLong())))
        while (!isBusinessDay(date)) {
            date = date.minusDays(1)
        }
        return date
    }

    fun isBusinessDay(date: LocalDate): Boolean =
        date.dayOfWeek != DayOfWeek.SATURDAY &&
            date.dayOfWeek != DayOfWeek.SUNDAY &&
            !isNationalHoliday(date)

    private fun isNationalHoliday(date: LocalDate): Boolean {
        val easter = easterSunday(date.year)
        val carnivalMonday = easter.minusDays(48)
        val carnivalTuesday = easter.minusDays(47)
        val goodFriday = easter.minusDays(2)
        val corpusChristi = easter.plusDays(60)

        return when (date) {
            LocalDate.of(date.year, Month.JANUARY, 1),
            LocalDate.of(date.year, Month.APRIL, 21),
            LocalDate.of(date.year, Month.MAY, 1),
            LocalDate.of(date.year, Month.SEPTEMBER, 7),
            LocalDate.of(date.year, Month.OCTOBER, 12),
            LocalDate.of(date.year, Month.NOVEMBER, 2),
            LocalDate.of(date.year, Month.NOVEMBER, 15),
            LocalDate.of(date.year, Month.NOVEMBER, 20),
            LocalDate.of(date.year, Month.DECEMBER, 25),
            carnivalMonday,
            carnivalTuesday,
            goodFriday,
            corpusChristi,
            -> true
            else -> false
        }
    }

    // Anonymous Gregorian algorithm.
    private fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }
}
