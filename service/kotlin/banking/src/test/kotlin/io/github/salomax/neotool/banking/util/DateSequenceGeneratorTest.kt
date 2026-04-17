package io.github.salomax.neotool.banking.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DateSequenceGeneratorTest {
    @Test
    fun `bounded generates correct count for semi-annual on 2-year bond`() {
        val dates =
            DateSequenceGenerator.generate(
                start = LocalDate.of(2024, 1, 1),
                end = LocalDate.of(2026, 1, 1),
                stepMonths = 6L,
            )
        assertEquals(4, dates.size)
        assertEquals(LocalDate.of(2024, 7, 1), dates.first())
        assertEquals(LocalDate.of(2026, 1, 1), dates.last())
    }

    @Test
    fun `bounded returns empty when bond shorter than step`() {
        val dates =
            DateSequenceGenerator.generate(
                start = LocalDate.of(2024, 1, 1),
                end = LocalDate.of(2024, 3, 1),
                stepMonths = 6L,
            )
        assertTrue(dates.isEmpty())
    }
}
