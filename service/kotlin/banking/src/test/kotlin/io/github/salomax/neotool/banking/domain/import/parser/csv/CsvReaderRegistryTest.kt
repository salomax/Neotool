package io.github.salomax.neotool.banking.domain.import.parser.csv

import io.github.salomax.neotool.banking.domain.import.ParsedTransaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("CsvReaderRegistry")
class CsvReaderRegistryTest {
    private val fingerprint =
        CsvFingerprint(
            delimiter = ',',
            encoding = "UTF-8",
            hasHeader = true,
            normalizedColumns = listOf("data", "valor", "descricao"),
            datePatterns = setOf(CsvDatePattern.BR_DD_MM_YYYY_SLASH),
            amountPatterns = setOf(CsvAmountPattern.DOT_DECIMAL),
            descriptionTokens = setOf("pix"),
        )

    @Test
    fun `select returns SPECIFIC_ONLY when best score is high`() {
        val registry =
            CsvReaderRegistry(
                readers =
                    listOf(
                        FakeReader("csv-nubank", 0.92),
                        FakeReader("csv-itau", 0.40),
                    ),
            )

        val result = registry.select(fingerprint)

        assertThat(result.mode).isEqualTo(CsvReaderSelectionMode.SPECIFIC_ONLY)
        assertThat(result.selectedReader?.id()).isEqualTo("csv-nubank")
        assertThat(result.selectedMatch?.score).isEqualTo(0.92)
    }

    @Test
    fun `select returns SPECIFIC_THEN_GENERIC_FALLBACK when best score is medium`() {
        val registry =
            CsvReaderRegistry(
                readers =
                    listOf(
                        FakeReader("csv-a", 0.70),
                        FakeReader("csv-b", 0.52),
                    ),
            )

        val result = registry.select(fingerprint)

        assertThat(result.mode).isEqualTo(CsvReaderSelectionMode.SPECIFIC_THEN_GENERIC_FALLBACK)
        assertThat(result.selectedReader?.id()).isEqualTo("csv-a")
        assertThat(result.selectedMatch?.score).isEqualTo(0.70)
    }

    @Test
    fun `select returns GENERIC_ONLY when best score is low`() {
        val registry =
            CsvReaderRegistry(
                readers =
                    listOf(
                        FakeReader("csv-a", 0.51),
                        FakeReader("csv-b", 0.30),
                    ),
            )

        val result = registry.select(fingerprint)

        assertThat(result.mode).isEqualTo(CsvReaderSelectionMode.GENERIC_ONLY)
        assertThat(result.selectedReader).isNull()
        assertThat(result.selectedMatch).isNull()
    }

    @Test
    fun `rank orders by descending score then readerId`() {
        val registry =
            CsvReaderRegistry(
                readers =
                    listOf(
                        FakeReader("csv-c", 0.80),
                        FakeReader("csv-a", 0.90),
                        FakeReader("csv-b", 0.80),
                    ),
            )

        val ranked = registry.rank(fingerprint)

        assertThat(ranked.map { it.readerId }).containsExactly("csv-a", "csv-b", "csv-c")
    }

    @Test
    fun `normalizes mismatched readerId from reader support response`() {
        val registry =
            CsvReaderRegistry(
                readers =
                    listOf(
                        FakeReader("csv-a", 0.83, returnedReaderId = "wrong-id"),
                    ),
            )

        val ranked = registry.rank(fingerprint)

        assertThat(ranked).hasSize(1)
        assertThat(ranked[0].readerId).isEqualTo("csv-a")
    }

    @Test
    fun `throws when reader ids are duplicated`() {
        val ex =
            assertThrows<IllegalArgumentException> {
                CsvReaderRegistry(
                    readers =
                        listOf(
                            FakeReader("dup", 0.90),
                            FakeReader("dup", 0.40),
                        ),
                )
            }

        assertThat(ex.message).contains("Reader ids must be unique")
    }

    private class FakeReader(
        private val id: String,
        private val score: Double,
        private val returnedReaderId: String = id,
    ) : BankCsvReader {
        override fun id(): String = id

        override fun supports(fingerprint: CsvFingerprint): CsvReaderMatch =
            CsvReaderMatch(
                readerId = returnedReaderId,
                score = score,
                reasons = listOf("test"),
            )

        override fun parse(bytes: ByteArray): List<ParsedTransaction> = emptyList()
    }
}
