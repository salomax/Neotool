package io.github.salomax.neotool.banking.domain.investment

import io.github.salomax.neotool.banking.model.domain.investment.policy.BrazilianBusinessCalendar
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.Month

@DisplayName("BrazilianBusinessCalendar")
class BrazilianBusinessCalendarTest {

    @Test
    fun `last business day of May uses the legal month-end business day`() {
        assertThat(BrazilianBusinessCalendar.lastBusinessDay(2025, Month.MAY))
            .isEqualTo(LocalDate.of(2025, 5, 30))
    }

    @Test
    fun `last business day of November skips weekends`() {
        assertThat(BrazilianBusinessCalendar.lastBusinessDay(2025, Month.NOVEMBER))
            .isEqualTo(LocalDate.of(2025, 11, 28))
    }
}
