package io.github.salomax.neotool.financialdata.test.unit.domain

import io.github.salomax.neotool.financialdata.domain.QuarterRange
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class QuarterRangeTest {
    @Test
    fun `creates QuarterRange with from and to`() {
        val range = QuarterRange(from = "202401", to = "202403")

        assertThat(range.from).isEqualTo("202401")
        assertThat(range.to).isEqualTo("202403")
        assertThat(range.last).isNull()
    }

    @Test
    fun `creates QuarterRange with last`() {
        val range = QuarterRange(last = 4)

        assertThat(range.last).isEqualTo(4)
        assertThat(range.from).isNull()
        assertThat(range.to).isNull()
    }

    @Test
    fun `throws when neither from and to nor last specified`() {
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                QuarterRange()
            }
        assertThat(ex.message).contains("Either (from and to) or last must be specified")
    }

    @Test
    fun `throws when both from and to and last specified`() {
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                QuarterRange(from = "202401", to = "202403", last = 2)
            }
        assertThat(ex.message).contains("Cannot specify both")
    }

    @Test
    fun `throws when last is zero`() {
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                QuarterRange(last = 0)
            }
        assertThat(ex.message).contains("last must be greater than 0")
    }

    @Test
    fun `throws when last is negative`() {
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                QuarterRange(last = -1)
            }
        assertThat(ex.message).contains("last must be greater than 0")
    }
}
