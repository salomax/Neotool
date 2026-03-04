package io.github.salomax.neotool.banking.domain.import.parser

import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.time.LocalDate

@DisplayName("OFXParser")
class OFXParserTest {

    private fun loadFixture(name: String): ByteArray =
        javaClass.getResourceAsStream("/fixtures/ofx/$name")!!.readBytes()

    @Nested
    @DisplayName("parse — SGML fixture")
    inner class ParseSgmlFixtureTests {

        @Test
        fun `parses ofx_sgml_sample and returns expected transactions`() {
            val bytes = loadFixture("ofx_sgml_sample.ofx")

            val result = OFXParser.parse(bytes)

            assertThat(result).hasSize(3)

            val debit1 = result[0]
            assertThat(debit1.providerTransactionId).isEqualTo("sgml-fitid-001")
            assertThat(debit1.creditDebitType).isEqualTo(CreditDebitType.DEBIT)
            assertThat(debit1.amount).isEqualByComparingTo(BigDecimal("25.50"))
            assertThat(debit1.date).isEqualTo(LocalDate.of(2024, 1, 15))
            assertThat(debit1.description).isEqualTo("Coffee Shop")
            assertThat(debit1.descriptionRaw).isEqualTo("Card purchase")
            assertThat(debit1.currencyCode).isEqualTo("BRL")
            assertThat(debit1.transactionDatetime).isNotNull()

            val debit2 = result[1]
            assertThat(debit2.providerTransactionId).isEqualTo("sgml-fitid-002")
            assertThat(debit2.creditDebitType).isEqualTo(CreditDebitType.DEBIT)
            assertThat(debit2.amount).isEqualByComparingTo(BigDecimal("120.30"))
            assertThat(debit2.date).isEqualTo(LocalDate.of(2024, 1, 16))
            assertThat(debit2.description).isEqualTo("Supermarket")
            assertThat(debit2.transactionDatetime).isNull()

            val credit = result[2]
            assertThat(credit.providerTransactionId).isEqualTo("sgml-fitid-003")
            assertThat(credit.creditDebitType).isEqualTo(CreditDebitType.CREDIT)
            assertThat(credit.amount).isEqualByComparingTo(BigDecimal("1500.00"))
            assertThat(credit.date).isEqualTo(LocalDate.of(2024, 1, 20))
            assertThat(credit.description).isEqualTo("Employer Inc")
            assertThat(credit.descriptionRaw).isEqualTo("Salary Jan")
        }
    }

    @Nested
    @DisplayName("parse — XML fixture")
    inner class ParseXmlFixtureTests {

        @Test
        fun `parses ofx_xml_sample and returns expected transactions`() {
            val bytes = loadFixture("ofx_xml_sample.ofx")

            val result = OFXParser.parse(bytes)

            assertThat(result).hasSize(3)

            assertThat(result[0].providerTransactionId).isEqualTo("xml-fitid-001")
            assertThat(result[0].creditDebitType).isEqualTo(CreditDebitType.DEBIT)
            assertThat(result[0].amount).isEqualByComparingTo(BigDecimal("89.90"))
            assertThat(result[0].date).isEqualTo(LocalDate.of(2024, 1, 10))
            assertThat(result[0].description).isEqualTo("Restaurant")
            assertThat(result[0].descriptionRaw).isEqualTo("Lunch")

            assertThat(result[1].providerTransactionId).isEqualTo("xml-fitid-002")
            assertThat(result[1].amount).isEqualByComparingTo(BigDecimal("35.00"))
            assertThat(result[1].description).isEqualTo("Pharmacy")

            assertThat(result[2].providerTransactionId).isEqualTo("xml-fitid-003")
            assertThat(result[2].creditDebitType).isEqualTo(CreditDebitType.CREDIT)
            assertThat(result[2].amount).isEqualByComparingTo(BigDecimal("200.00"))
            assertThat(result[2].description).isEqualTo("Friend")
            assertThat(result[2].descriptionRaw).isEqualTo("Transfer received")
        }
    }

    @Nested
    @DisplayName("parse — format detection")
    inner class ParseFormatDetectionTests {

        @Test
        fun `returns empty list when content does not start with OFX header or XML`() {
            val bytes = "plain,text,csv\nrow1".toByteArray(StandardCharsets.UTF_8)

            val result = OFXParser.parse(bytes)

            assertThat(result).isEmpty()
        }

        @Test
        fun `returns empty list when bytes are empty`() {
            val result = OFXParser.parse(ByteArray(0))

            assertThat(result).isEmpty()
        }

        @Test
        fun `detects SGML via OFXHEADER`() {
            val sgml = """
                OFXHEADER:100
                DATA:OFXSGML
                <OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKTRANLIST>
                <STMTTRN>
                <DTPOSTED>20240101
                <TRNAMT>-10.00
                <FITID>detect-sgml
                </STMTTRN>
                </BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>
            """.trimIndent()
            val result = OFXParser.parse(sgml.toByteArray(StandardCharsets.UTF_8))

            assertThat(result).hasSize(1)
            assertThat(result[0].providerTransactionId).isEqualTo("detect-sgml")
        }

        @Test
        fun `detects XML via xml declaration`() {
            val xml = """<?xml version="1.0"?>
<OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKTRANLIST>
<STMTTRN><DTPOSTED>20240101</DTPOSTED><TRNAMT>-5.00</TRNAMT><FITID>detect-xml</FITID></STMTTRN>
</BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>"""
            val result = OFXParser.parse(xml.toByteArray(StandardCharsets.UTF_8))

            assertThat(result).hasSize(1)
            assertThat(result[0].providerTransactionId).isEqualTo("detect-xml")
        }

        @Test
        fun `returns empty list when XML contains DOCTYPE external entity`() {
            val xml = """
                <?xml version="1.0"?>
                <!DOCTYPE foo [
                  <!ELEMENT foo ANY >
                  <!ENTITY xxe SYSTEM "file:///etc/passwd" >
                ]>
                <OFX>
                  <BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKTRANLIST>
                    <STMTTRN>
                      <DTPOSTED>20240101</DTPOSTED>
                      <TRNAMT>-5.00</TRNAMT>
                      <FITID>&xxe;</FITID>
                    </STMTTRN>
                  </BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1>
                </OFX>
            """.trimIndent()

            val result = OFXParser.parse(xml.toByteArray(StandardCharsets.UTF_8))

            assertThat(result).isEmpty()
        }
    }

    @Nested
    @DisplayName("parse — mapping")
    inner class ParseMappingTests {

        @Test
        fun `uses PAYEE when NAME absent and description defaults from MEMO`() {
            val sgml = """
                OFXHEADER:100
                DATA:OFXSGML
                <OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKTRANLIST>
                <STMTTRN>
                <DTPOSTED>20240101
                <TRNAMT>100.00
                <PAYEE>Acme Corp
                <MEMO>Invoice 123
                </STMTTRN>
                </BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>
            """.trimIndent()
            val result = OFXParser.parse(sgml.toByteArray(StandardCharsets.UTF_8))

            assertThat(result).hasSize(1)
            assertThat(result[0].description).isEqualTo("Acme Corp")
            assertThat(result[0].descriptionRaw).isEqualTo("Invoice 123")
        }

        @Test
        fun `negative TRNAMT is DEBIT positive is CREDIT`() {
            val sgml = """
                OFXHEADER:100
                DATA:OFXSGML
                <OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKTRANLIST>
                <STMTTRN><DTPOSTED>20240101<TRNAMT>-50.00<FITID>d
                </STMTTRN>
                <STMTTRN><DTPOSTED>20240102<TRNAMT>50.00<FITID>c
                </STMTTRN>
                </BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>
            """.trimIndent()
            val result = OFXParser.parse(sgml.toByteArray(StandardCharsets.UTF_8))

            assertThat(result[0].creditDebitType).isEqualTo(CreditDebitType.DEBIT)
            assertThat(result[0].amount).isEqualByComparingTo(BigDecimal("50.00"))
            assertThat(result[1].creditDebitType).isEqualTo(CreditDebitType.CREDIT)
            assertThat(result[1].amount).isEqualByComparingTo(BigDecimal("50.00"))
        }

        @Test
        fun `amount uses abs and description falls back to OFX when no NAME or PAYEE or MEMO`() {
            val sgml = """
                OFXHEADER:100
                DATA:OFXSGML
                <OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKTRANLIST>
                <STMTTRN>
                <DTPOSTED>20240101
                <TRNAMT>-99.99
                <FITID>minimal
                </STMTTRN>
                </BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>
            """.trimIndent()
            val result = OFXParser.parse(sgml.toByteArray(StandardCharsets.UTF_8))

            assertThat(result).hasSize(1)
            assertThat(result[0].amount).isEqualByComparingTo(BigDecimal("99.99"))
            assertThat(result[0].description).isEqualTo("OFX")
        }
    }
}
