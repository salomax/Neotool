package io.github.salomax.neotool.banking.service.billimport.pdf

import io.github.salomax.neotool.banking.domain.import.CanonicalProcessorType
import io.github.salomax.neotool.banking.domain.import.CanonicalReaderOutput
import io.github.salomax.neotool.banking.service.import.CanonicalReaderOutputMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("DeterministicPdfReaderRouter")
class DeterministicPdfReaderRouterTest {
    @Test
    fun `routes known profile to matching reader and returns canonical output`() {
        val reader =
            object : DeterministicPdfReader {
                override val readerId: String = "stub-reader"
                override val supportedProfileIds: Set<String> = setOf("profile-known")

                override fun parse(input: DeterministicPdfReaderInput): CanonicalReaderOutput =
                    CanonicalReaderOutput(
                        schemaId = CanonicalReaderOutputMapper.SCHEMA_ID,
                        schemaVersion = CanonicalReaderOutputMapper.SCHEMA_VERSION,
                        processorType = CanonicalProcessorType.DETERMINISTIC,
                        readerId = readerId,
                        confidence = 1.0,
                        normalizedRecords = listOf(mapOf("record_type" to "PDF_CARD_SUMMARY")),
                    )
            }

        val router = DeterministicPdfReaderRouter(DeterministicPdfReaderRegistry(listOf(reader)))
        val output =
            router.parse(
                profileId = "profile-known",
                documentType = "CREDIT_CARD_BILL",
                extractedText = "text",
                extractionMetadata = emptyMap(),
            )

        assertThat(output).isNotNull
        assertThat(output?.processorType).isEqualTo(CanonicalProcessorType.DETERMINISTIC)
        assertThat(output?.readerId).isEqualTo("stub-reader")
    }

    @Test
    fun `returns null when no reader supports the profile`() {
        val router = DeterministicPdfReaderRouter(DeterministicPdfReaderRegistry(emptyList()))

        val output =
            router.parse(
                profileId = "profile-unknown",
                documentType = "CREDIT_CARD_BILL",
                extractedText = "text",
                extractionMetadata = emptyMap(),
            )

        assertThat(output).isNull()
    }
}
