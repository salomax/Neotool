package io.github.salomax.neotool.banking.util

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object DateSequenceGenerator {
    /**
     * Generates a sequence of dates spaced [stepMonths] apart.
     *
     * When [end] is provided (bounded): generates backwards from [end],
     * stopping before [start]. Returns dates in ascending order.
     *
     * When [end] is null (open-ended): generates forward from [start]
     * up to [limit] entries.
     */
    fun generate(
        start: LocalDate,
        end: LocalDate? = null,
        stepMonths: Long,
        limit: Int = 600, // 50-year cap at monthly — safety valve
    ): List<LocalDate> {
        require(stepMonths > 0) { "stepMonths must be positive" }
        return if (end != null) {
            generateBounded(start, end, stepMonths)
        } else {
            generateOpenEnded(start, stepMonths, limit)
        }
    }

    private fun generateBounded(
        start: LocalDate,
        end: LocalDate,
        stepMonths: Long,
    ): List<LocalDate> {
        if (!end.isAfter(start)) return emptyList()
        if (ChronoUnit.MONTHS.between(start, end) < stepMonths) return emptyList()
        val dates = mutableListOf<LocalDate>()
        var current = end
        while (current.isAfter(start)) {
            dates.add(current)
            current = current.minusMonths(stepMonths)
        }
        return dates.reversed()
    }

    private fun generateOpenEnded(
        start: LocalDate,
        stepMonths: Long,
        limit: Int,
    ): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        var current = start.plusMonths(stepMonths)
        repeat(limit) {
            dates.add(current)
            current = current.plusMonths(stepMonths)
        }
        return dates
    }
}
