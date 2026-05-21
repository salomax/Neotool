package io.github.salomax.neotool.banking.domain.import

import io.github.salomax.neotool.banking.service.import.CanonicalReaderOutputMapper
import io.github.salomax.neotool.banking.service.import.parser.CsvNubankParser
import io.github.salomax.neotool.banking.service.import.parser.OFXParser
import io.github.salomax.neotool.banking.service.import.parser.csv.GenericCsvReader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

@DisplayName("CanonicalReaderOutputMapper")
class CanonicalReaderOutputMapperTest {
    private fun loadFixture(path: String): ByteArray = javaClass.getResourceAsStream(path)!!.readBytes()

    @Test
    fun `maps OFX parsed rows to canonical output and back without data loss`() {
        val rows = OFXParser.parse(loadFixture("/fixtures/ofx/ofx_sgml_sample.ofx"))

        val canonical =
            CanonicalReaderOutputMapper.fromOfx(
                rows = rows,
                rawMetadata = mapOf("format" to "OFX"),
            )
        val restored = CanonicalReaderOutputMapper.toParsedTransactions(canonical)

        assertThat(canonical.schemaId).isEqualTo(CanonicalReaderOutputMapper.SCHEMA_ID)
        assertThat(canonical.schemaVersion).isEqualTo(CanonicalReaderOutputMapper.SCHEMA_VERSION)
        assertThat(canonical.processorType).isEqualTo(CanonicalProcessorType.DETERMINISTIC)
        assertThat(canonical.readerId).isEqualTo("ofx-parser")
        assertThat(canonical.confidence).isEqualTo(1.0)
        assertThat(canonical.rawMetadata["format"]).isEqualTo("OFX")
        assertThat(restored).containsExactlyElementsOf(rows)
    }

    @Test
    fun `maps CSV nubank rows to canonical output and back without data loss`() {
        val rows = CsvNubankParser.parse(loadFixture("/fixtures/csv/nubank_sample.csv"))

        val canonical =
            CanonicalReaderOutputMapper.fromCsv(
                rows = rows,
                readerId = CsvNubankParser.id(),
                confidence = 0.93,
                warnings = listOf("specific_reader_used"),
                rawMetadata = mapOf("selection_mode" to "SPECIFIC_ONLY"),
            )
        val restored = CanonicalReaderOutputMapper.toParsedTransactions(canonical)

        assertThat(canonical.processorType).isEqualTo(CanonicalProcessorType.DETERMINISTIC)
        assertThat(canonical.readerId).isEqualTo(CsvNubankParser.id())
        assertThat(canonical.confidence).isEqualTo(0.93)
        assertThat(canonical.warnings).containsExactly("specific_reader_used")
        assertThat(canonical.rawMetadata["selection_mode"]).isEqualTo("SPECIFIC_ONLY")
        assertThat(restored).containsExactlyElementsOf(rows)
    }

    @Test
    fun `maps CSV generic rows with AUTO processor type`() {
        val csv =
            """
            date,amount,description,id
            2026-01-01,-10.50,Coffee,tx-1
            2026-01-02,25.00,Refund,tx-2
            """.trimIndent()
        val rows = GenericCsvReader.parse(csv.toByteArray(StandardCharsets.UTF_8))

        val canonical =
            CanonicalReaderOutputMapper.fromCsv(
                rows = rows,
                readerId = GenericCsvReader.id(),
                confidence = 0.72,
            )
        val restored = CanonicalReaderOutputMapper.toParsedTransactions(canonical)

        assertThat(canonical.processorType).isEqualTo(CanonicalProcessorType.AUTO)
        assertThat(canonical.readerId).isEqualTo(GenericCsvReader.id())
        assertThat(restored).containsExactlyElementsOf(rows)
    }
}
