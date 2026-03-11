package io.github.salomax.neotool.banking.domain.pdfimport.reader

import io.github.salomax.neotool.banking.domain.import.CanonicalProcessorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("NubankCreditCardBillPdfReader")
class NubankCreditCardBillPdfReaderTest {
    private val reader = NubankCreditCardBillPdfReader()

    private fun loadFixture(name: String): String =
        javaClass.getResourceAsStream("/fixtures/pdf/$name")!!.readBytes().toString(Charsets.UTF_8)

    @Nested
    @DisplayName("reader contract")
    inner class ReaderContractTests {
        @Test
        fun `returns stable reader id and supported profile`() {
            assertThat(reader.readerId).isEqualTo("pdf-reader-nubank-credit-card-v1")
            assertThat(reader.supportedProfileIds).contains("pdf-nubank-credit-card-bill-v1")
        }
    }

    @Nested
    @DisplayName("parse fixture")
    inner class ParseFixtureTests {
        @Test
        fun `parses known profile fixture into canonical deterministic output`() {
            val text = loadFixture("nubank_known_profile_extracted.txt")

            val output =
                reader.parse(
                    DeterministicPdfReaderInput(
                        profileId = "pdf-nubank-credit-card-bill-v1",
                        documentType = "CREDIT_CARD_BILL",
                        extractedText = text,
                        extractionMetadata = mapOf("statement_year" to 2026),
                    ),
                )

            assertThat(output.processorType).isEqualTo(CanonicalProcessorType.DETERMINISTIC)
            assertThat(output.readerId).isEqualTo("pdf-reader-nubank-credit-card-v1")
            assertThat(output.confidence).isEqualTo(0.98)
            assertThat(output.warnings).doesNotContain("no_transaction_rows_parsed")
            assertThat(output.normalizedRecords).hasSize(4)

            val summary = output.normalizedRecords.first()
            assertThat(summary["record_type"]).isEqualTo("PDF_CARD_SUMMARY")
            assertThat(summary["card_last_four_digits"]).isEqualTo("1234")
            assertThat(summary["declared_total"]).isEqualTo(BigDecimal("153.40"))
            assertThat(summary["computed_total"]).isEqualTo(BigDecimal("153.40"))
            assertThat(summary["total_matches_declared"]).isEqualTo(true)

            val tx1 = output.normalizedRecords[1]
            assertThat(tx1["record_type"]).isEqualTo("PDF_CARD_TRANSACTION")
            assertThat(tx1["date"]).isEqualTo("2026-01-15")
            assertThat(tx1["description"]).isEqualTo("MERCADO EXTRA")
            assertThat(tx1["amount"]).isEqualTo(BigDecimal("45.90"))
            assertThat(tx1["credit_debit_type"]).isEqualTo("DEBIT")

            val tx2 = output.normalizedRecords[2]
            assertThat(tx2["date"]).isEqualTo("2026-01-22")
            assertThat(tx2["amount"]).isEqualTo(BigDecimal("32.00"))
            assertThat(tx2["credit_debit_type"]).isEqualTo("CREDIT")

            val tx3 = output.normalizedRecords[3]
            assertThat(tx3["date"]).isEqualTo("2026-01-24")
            assertThat(tx3["amount"]).isEqualTo(BigDecimal("139.50"))
            assertThat(tx3["installment_current"]).isEqualTo(2)
            assertThat(tx3["installment_total"]).isEqualTo(3)
        }

        @Test
        fun `parse is deterministic and reproducible for same input`() {
            val text = loadFixture("nubank_known_profile_extracted.txt")
            val input =
                DeterministicPdfReaderInput(
                    profileId = "pdf-nubank-credit-card-bill-v1",
                    documentType = "CREDIT_CARD_BILL",
                    extractedText = text,
                    extractionMetadata = mapOf("statement_year" to 2026),
                )

            val first = reader.parse(input)
            val second = reader.parse(input)

            assertThat(first).isEqualTo(second)
        }
    }
}
