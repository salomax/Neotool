package io.github.salomax.neotool.banking.service.import.parser.csv

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("CsvReaderSelectionPolicy")
class CsvReaderSelectionPolicyTest {
    @Test
    fun `maps high medium and low scores to expected modes`() {
        val policy =
            CsvReaderSelectionPolicy(
                specificThreshold = 0.85,
                trySpecificThenFallbackThreshold = 0.60,
            )

        assertThat(policy.modeFor(0.90)).isEqualTo(CsvReaderSelectionMode.SPECIFIC_ONLY)
        assertThat(policy.modeFor(0.70)).isEqualTo(CsvReaderSelectionMode.SPECIFIC_THEN_GENERIC_FALLBACK)
        assertThat(policy.modeFor(0.59)).isEqualTo(CsvReaderSelectionMode.GENERIC_ONLY)
    }

    @Test
    fun `throws when thresholds are invalid`() {
        val lowSpecific =
            assertThrows<IllegalArgumentException> {
                CsvReaderSelectionPolicy(
                    specificThreshold = -0.1,
                    trySpecificThenFallbackThreshold = 0.6,
                )
            }
        assertThat(lowSpecific.message).contains("specificThreshold")

        val orderInvalid =
            assertThrows<IllegalArgumentException> {
                CsvReaderSelectionPolicy(
                    specificThreshold = 0.5,
                    trySpecificThenFallbackThreshold = 0.6,
                )
            }
        assertThat(orderInvalid.message).contains("greater than or equal")
    }
}
