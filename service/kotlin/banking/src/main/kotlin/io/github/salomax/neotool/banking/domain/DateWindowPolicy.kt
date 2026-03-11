package io.github.salomax.neotool.banking.domain

import java.time.LocalDate

object DateWindowPolicy {
    const val MAX_PAST_YEARS: Int = 5

    fun minAllowedDate(referenceDate: LocalDate = LocalDate.now()): LocalDate =
        referenceDate.minusYears(MAX_PAST_YEARS.toLong())
}
