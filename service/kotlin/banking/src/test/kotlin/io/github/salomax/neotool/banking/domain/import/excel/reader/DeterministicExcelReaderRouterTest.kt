package io.github.salomax.neotool.banking.service.import.excel

import io.github.salomax.neotool.banking.service.import.excel.*
import io.github.salomax.neotool.banking.service.import.*
import io.github.salomax.neotool.banking.domain.import.CanonicalProcessorType
import io.github.salomax.neotool.banking.domain.import.CanonicalReaderOutput
import io.github.salomax.neotool.banking.service.import.CanonicalReaderOutputMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("DeterministicExcelReaderRouter")
class DeterministicExcelReaderRouterTest {
    @Test
    fun `routes known profile to matching reader and returns canonical output`() {
        val reader =
            object : DeterministicExcelReader {
                override val readerId: String = "stub-excel-reader"
                override val supportedProfileIds: Set<String> = setOf("excel-profile-known")

                override fun parse(input: DeterministicExcelReaderInput): CanonicalReaderOutput =
                    CanonicalReaderOutput(
                        schemaId = CanonicalReaderOutputMapper.SCHEMA_ID,
                        schemaVersion = CanonicalReaderOutputMapper.SCHEMA_VERSION,
                        processorType = CanonicalProcessorType.DETERMINISTIC,
                        readerId = readerId,
                        confidence = 1.0,
                        normalizedRecords = listOf(mapOf("record_type" to "TRANSACTION")),
                    )
            }

        val router = DeterministicExcelReaderRouter(DeterministicExcelReaderRegistry(listOf(reader)))
        val output =
            router.parse(
                profileId = "excel-profile-known",
                documentType = "BANK_STATEMENT",
                extractedText = "text",
                extractionMetadata = emptyMap(),
            )

        assertThat(output).isNotNull
        assertThat(output?.processorType).isEqualTo(CanonicalProcessorType.DETERMINISTIC)
        assertThat(output?.readerId).isEqualTo("stub-excel-reader")
    }

    @Test
    fun `returns null when no reader supports profile`() {
        val router = DeterministicExcelReaderRouter(DeterministicExcelReaderRegistry(emptyList()))

        val output =
            router.parse(
                profileId = "excel-profile-unknown",
                documentType = "BANK_STATEMENT",
                extractedText = "text",
                extractionMetadata = emptyMap(),
            )

        assertThat(output).isNull()
    }
}
