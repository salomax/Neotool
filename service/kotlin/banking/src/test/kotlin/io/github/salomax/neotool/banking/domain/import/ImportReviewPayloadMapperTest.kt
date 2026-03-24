package io.github.salomax.neotool.banking.domain.import

import io.github.salomax.neotool.banking.service.import.*
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

@DisplayName("ImportReviewPayloadMapper")
class ImportReviewPayloadMapperTest {
    private val objectMapper = jacksonObjectMapper().apply { registerModule(JavaTimeModule()) }

    @Test
    fun `reads legacy array payload as canonical envelope`() {
        val legacyPayload = objectMapper.writeValueAsString(listOf(sampleCandidate(rowIndex = 2)))

        val parsed = ImportReviewPayloadMapper.fromJson(objectMapper, legacyPayload)

        assertThat(parsed).isNotNull
        assertThat(parsed!!.schemaId).isEqualTo(ImportReviewPayloadMapper.SCHEMA_ID)
        assertThat(parsed.schemaVersion).isEqualTo(ImportReviewPayloadMapper.SCHEMA_VERSION)
        assertThat(parsed.duplicateCandidates).hasSize(1)
        assertThat(parsed.duplicateCandidates.first().rowIndex).isEqualTo(2)
        assertThat(parsed.gate).isNull()
    }

    @Test
    fun `writes and reads canonical envelope with duplicates and gate`() {
        val payloadJson =
            ImportReviewPayloadMapper.toJson(
                objectMapper = objectMapper,
                duplicateCandidates = listOf(sampleCandidate()),
                gate =
                    mapOf(
                        "gate_reason" to "llm_low_confidence",
                        "confidence" to 0.42,
                    ),
            )

        val parsed = ImportReviewPayloadMapper.fromJson(objectMapper, payloadJson)

        assertThat(parsed).isNotNull
        assertThat(parsed!!.schemaId).isEqualTo(ImportReviewPayloadMapper.SCHEMA_ID)
        assertThat(parsed.duplicateCandidates).hasSize(1)
        assertThat(parsed.gate).isNotNull
        assertThat(parsed.gate!!["gate_reason"]).isEqualTo("llm_low_confidence")
    }

    @Test
    fun `returns null when encoding empty payload`() {
        val payloadJson =
            ImportReviewPayloadMapper.toJson(
                objectMapper = objectMapper,
                duplicateCandidates = emptyList(),
                gate = null,
            )

        assertThat(payloadJson).isNull()
    }

    private fun sampleCandidate(rowIndex: Int = 0): ImportDuplicateCandidate =
        ImportDuplicateCandidate(
            rowIndex = rowIndex,
            parsed =
                ParsedTransaction(
                    providerTransactionId = "pid-$rowIndex",
                    creditDebitType = CreditDebitType.DEBIT,
                    amount = BigDecimal("10.50"),
                    date = LocalDate.of(2026, 1, 10),
                    description = "Coffee",
                    currencyCode = "BRL",
                ),
            candidates = emptyList(),
        )
}
