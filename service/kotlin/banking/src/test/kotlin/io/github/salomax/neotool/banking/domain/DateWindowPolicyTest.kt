package io.github.salomax.neotool.banking.domain

import io.github.salomax.neotool.banking.shared.DateWindowPolicy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate

@DisplayName("DateWindowPolicy")
class DateWindowPolicyTest {
    @Test
    fun `minAllowedDate returns reference date minus 5 years`() {
        val referenceDate = LocalDate.of(2026, 3, 11)

        val result = DateWindowPolicy.minAllowedDate(referenceDate)

        assertThat(result).isEqualTo(LocalDate.of(2021, 3, 11))
    }
}
