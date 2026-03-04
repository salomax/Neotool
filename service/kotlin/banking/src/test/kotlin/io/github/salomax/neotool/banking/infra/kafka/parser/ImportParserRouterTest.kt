package io.github.salomax.neotool.banking.infra.kafka.parser

import io.github.salomax.neotool.banking.domain.import.ImportSource
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.banking.infra.kafka.CsvImportMetrics
import io.github.salomax.neotool.common.error.BusinessRuleException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.nio.charset.Charset

@DisplayName("ImportParserRouter")
class ImportParserRouterTest {

    private fun router(): ImportParserRouter =
        ImportParserRouter(
            strategies = listOf(
                OfxImportParserStrategy(),
                CsvImportParserStrategy(CsvImportMetrics(null)),
            ),
        )

    @Test
    fun `given xlsx bytes when source auto then throws format not supported`() {
        val router = router()
        val xlsxBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00)

        val thrown = catchThrowable {
            router.parse(
                bytes = xlsxBytes,
                fileName = "statement.xlsx",
                requestedSource = ImportSource.AUTO,
            )
        }
        assertThat(thrown).isInstanceOf(BusinessRuleException::class.java)
        val domain = thrown as BusinessRuleException
        assertThat(domain.errorCode).isEqualTo(BankingErrorCode.IMPORT_FILE_FORMAT_NOT_SUPPORTED)
    }

    @Test
    fun `given invalid csv content with data rows when source auto then throws format not supported`() {
        val router = router()
        val bytes = "foo,bar\nhello,world".toByteArray()

        val thrown = catchThrowable {
            router.parse(
                bytes = bytes,
                fileName = "invalid.csv",
                requestedSource = ImportSource.AUTO,
            )
        }
        assertThat(thrown).isInstanceOf(BusinessRuleException::class.java)
        val domain = thrown as BusinessRuleException
        assertThat(domain.errorCode).isEqualTo(BankingErrorCode.IMPORT_FILE_FORMAT_NOT_SUPPORTED)
    }

    @Test
    fun `given csv source but ofx content then throws format not supported`() {
        val router = router()
        val bytes = "OFXHEADER:100\n<OFX>\n</OFX>".toByteArray()

        val thrown = catchThrowable {
            router.parse(
                bytes = bytes,
                fileName = "statement.ofx",
                requestedSource = ImportSource.CSV_AUTO,
            )
        }
        assertThat(thrown).isInstanceOf(BusinessRuleException::class.java)
        val domain = thrown as BusinessRuleException
        assertThat(domain.errorCode).isEqualTo(BankingErrorCode.IMPORT_FILE_FORMAT_NOT_SUPPORTED)
    }

    @Test
    fun `given header only valid csv when source auto then allows empty outcome`() {
        val router = router()
        val bytes = "Data,Valor,Identificador,Descricao".toByteArray()

        val outcome = router.parse(
            bytes = bytes,
            fileName = "empty.csv",
            requestedSource = ImportSource.AUTO,
        )

        assertThat(outcome.rows).isEmpty()
    }

    @Test
    fun `given banco do brasil latin1 csv when source auto then parses rows without replacement chars`() {
        val router = router()
        val csv = """
            "Data","Lançamento","Detalhes","Nº documento","Valor","Tipo Lançamento"
            "03/02/2026","BB Rende Fácil","Rende Facil","9903","50,00","Entrada"
        """.trimIndent()
        val bytes = csv.toByteArray(Charset.forName("ISO-8859-1"))

        val outcome = router.parse(
            bytes = bytes,
            fileName = "extrato-bb.csv",
            requestedSource = ImportSource.AUTO,
        )

        assertThat(outcome.rows).hasSize(1)
        assertThat(outcome.rows[0].description).contains("Fácil")
        assertThat(outcome.rows[0].description).doesNotContain("�")
        assertThat(outcome.rows[0].providerTransactionId).isNull()
    }
}
