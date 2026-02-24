package io.github.salomax.neotool.financialdata.test.unit.service.fixedincome

import io.github.salomax.neotool.financialdata.service.fixedincome.DayCountService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DayCountServiceTest {
    private val service = DayCountService()

    @Test
    fun `businessDays 180 gives 124`() {
        assertThat(service.businessDays(180)).isEqualTo(124)
    }

    @Test
    fun `businessDays 181 gives 125`() {
        assertThat(service.businessDays(181)).isEqualTo(125)
    }

    @Test
    fun `businessDays 360 gives 249`() {
        assertThat(service.businessDays(360)).isEqualTo(249)
    }

    @Test
    fun `businessDays 361 gives 249`() {
        assertThat(service.businessDays(361)).isEqualTo(249)
    }

    @Test
    fun `businessDays 720 gives 497`() {
        assertThat(service.businessDays(720)).isEqualTo(497)
    }

    @Test
    fun `businessDays 721 gives 498`() {
        assertThat(service.businessDays(721)).isEqualTo(498)
    }

    @Test
    fun `businessDays 365 gives 252`() {
        assertThat(service.businessDays(365)).isEqualTo(252)
    }

    @Test
    fun `throws when calendarDays zero`() {
        assertThrows<IllegalArgumentException> { service.businessDays(0) }
    }

    @Test
    fun `throws when calendarDays negative`() {
        assertThrows<IllegalArgumentException> { service.businessDays(-1) }
    }
}
