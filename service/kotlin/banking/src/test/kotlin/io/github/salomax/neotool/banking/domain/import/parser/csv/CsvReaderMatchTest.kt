package io.github.salomax.neotool.banking.domain.import.parser.csv

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("CsvReaderMatch")
class CsvReaderMatchTest {

    @Test
    fun `creates match when readerId and score are valid`() {
        val match = CsvReaderMatch(
            readerId = "csv-nubank",
            score = 0.75,
            reasons = listOf("header_match"),
        )

        assertThat(match.readerId).isEqualTo("csv-nubank")
        assertThat(match.score).isEqualTo(0.75)
        assertThat(match.reasons).containsExactly("header_match")
    }

    @Test
    fun `throws when readerId is blank or score is out of bounds`() {
        val blankId = assertThrows<IllegalArgumentException> {
            CsvReaderMatch(readerId = " ", score = 0.5)
        }
        assertThat(blankId.message).contains("readerId")

        val invalidScore = assertThrows<IllegalArgumentException> {
            CsvReaderMatch(readerId = "csv-a", score = 1.2)
        }
        assertThat(invalidScore.message).contains("score")
    }
}
