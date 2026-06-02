package io.github.salomax.neotool.banking.domain.import

import io.github.salomax.neotool.banking.model.domain.imports.ImportSourceDetector
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.charset.Charset

@DisplayName("ImportSourceDetector")
class ImportSourceDetectorTest {
    private val nubankCardHeader = "date,transaction_type,title,amount,category,charges,Identificador"
    private val nubankAccountHeader = "Data,Valor,Identificador,Descrição"
    private val itauHeader = "Data lançamento;Histórico;Documento;Valor;Saldo"
    private val bradescoHeader = "Data;Histórico;Docto.;Crédito (R$);Débito (R$);Saldo (R$)"
    private val santanderHeader = "Data;Hora;Descricao;Valor;Saldo"

    @Nested
    @DisplayName("detect — OFX")
    inner class DetectOfxTests {
        @Test
        fun `returns OFX when first bytes contain OFXHEADER marker`() {
            val bytes = "OFXHEADER:100\nDATA:OFXSGML\n".toByteArray(Charsets.UTF_8)

            val result = ImportSourceDetector.detect(bytes, "file.ofx")

            assertThat(result).isEqualTo(ImportSource.OFX)
        }

        @Test
        fun `returns OFX when first bytes contain OFXHEADER lowercase`() {
            val bytes = "ofxheader:100\n".toByteArray(Charsets.UTF_8)

            val result = ImportSourceDetector.detect(bytes, null)

            assertThat(result).isEqualTo(ImportSource.OFX)
        }

        @Test
        fun `returns OFX when first bytes contain OFX tag marker`() {
            val bytes = "<?xml?>\n<OFX>\n<BANKMSGSRSV1>".toByteArray(Charsets.UTF_8)

            val result = ImportSourceDetector.detect(bytes, "export.xml")

            assertThat(result).isEqualTo(ImportSource.OFX)
        }

        @Test
        fun `returns OFX when first bytes contain ofx tag lowercase`() {
            val bytes = "<ofx>".toByteArray(Charsets.UTF_8)

            val result = ImportSourceDetector.detect(bytes, null)

            assertThat(result).isEqualTo(ImportSource.OFX)
        }

        @Test
        fun `returns OFX when OFX marker appears within first 256 bytes`() {
            val prefix = "x".repeat(200)
            val bytes = (prefix + "OFXHEADER:100").toByteArray(Charsets.UTF_8)

            val result = ImportSourceDetector.detect(bytes, "file.ofx")

            assertThat(result).isEqualTo(ImportSource.OFX)
        }
    }

    @Nested
    @DisplayName("detect — CSV Nubank")
    inner class DetectCsvNubankTests {
        @Test
        fun `returns CSV_NUBANK when first line is Nubank header`() {
            val content = "$nubankCardHeader\n2024-01-15,debit,Store,-50.00,Category,,id-1"
            val bytes = content.toByteArray(Charsets.UTF_8)

            val result = ImportSourceDetector.detect(bytes, "nubank.csv")

            assertThat(result).isEqualTo(ImportSource.CSV_NUBANK)
        }

        @Test
        fun `returns CSV_NUBANK when first line is Nubank header case insensitive`() {
            val content = "DATE,TRANSACTION_TYPE,TITLE,AMOUNT,CATEGORY,CHARGES,IDENTIFICADOR\n"
            val bytes = content.toByteArray(Charsets.UTF_8)

            val result = ImportSourceDetector.detect(bytes, null)

            assertThat(result).isEqualTo(ImportSource.CSV_NUBANK)
        }

        @Test
        fun `returns CSV_NUBANK when first line has leading and trailing whitespace`() {
            val content = "  $nubankCardHeader  \n2024-01-15,debit,Store,-50.00,Category,,id-1"
            val bytes = content.toByteArray(Charsets.UTF_8)

            val result = ImportSourceDetector.detect(bytes, "nubank.csv")

            assertThat(result).isEqualTo(ImportSource.CSV_NUBANK)
        }

        @Test
        fun `returns CSV_NUBANK when first line is Nubank account header`() {
            val content = "$nubankAccountHeader\n01/11/2025,-10.00,id-1,Pix enviado"
            val bytes = content.toByteArray(Charsets.UTF_8)

            val result = ImportSourceDetector.detect(bytes, "nubank-account.csv")

            assertThat(result).isEqualTo(ImportSource.CSV_NUBANK)
        }

        @Test
        fun `returns CSV_NUBANK for account header without accent`() {
            val content = "DATA,VALOR,IDENTIFICADOR,DESCRICAO\n01/11/2025,-10.00,id-1,Pix enviado"
            val bytes = content.toByteArray(Charsets.UTF_8)

            val result = ImportSourceDetector.detect(bytes, null)

            assertThat(result).isEqualTo(ImportSource.CSV_NUBANK)
        }

        @Test
        fun `returns CSV_NUBANK when header contains UTF-8 BOM`() {
            val content = "\uFEFF$nubankAccountHeader\n01/11/2025,-10.00,id-1,Pix enviado"
            val bytes = content.toByteArray(Charsets.UTF_8)

            val result = ImportSourceDetector.detect(bytes, "nubank-account.csv")

            assertThat(result).isEqualTo(ImportSource.CSV_NUBANK)
        }

        @Test
        fun `returns CSV_NUBANK for latin1 encoded account header`() {
            val content = "$nubankAccountHeader\n01/11/2025,-10.00,id-1,Pix enviado"
            val bytes = content.toByteArray(Charset.forName("ISO-8859-1"))

            val result = ImportSourceDetector.detect(bytes, "nubank-account.csv")

            assertThat(result).isEqualTo(ImportSource.CSV_NUBANK)
        }
    }

    @Nested
    @DisplayName("detect — CSV Itaú")
    inner class DetectCsvItauTests {
        @Test
        fun `returns CSV_ITAU when first line matches common Itau header`() {
            val content = "$itauHeader\n01/12/2025;PIX RECEBIDO;123;100,00;100,00"
            val bytes = content.toByteArray(Charsets.UTF_8)

            val result = ImportSourceDetector.detect(bytes, "itau.csv")

            assertThat(result).isEqualTo(ImportSource.CSV_ITAU)
        }
    }

    @Nested
    @DisplayName("detect — CSV Bradesco")
    inner class DetectCsvBradescoTests {
        @Test
        fun `returns CSV_BRADESCO when first line matches common Bradesco header`() {
            val content = "$bradescoHeader\n01/11/2025;PIX RECEBIDO;998877;100,00;0,00;100,00"
            val bytes = content.toByteArray(Charsets.UTF_8)

            val result = ImportSourceDetector.detect(bytes, "bradesco.csv")

            assertThat(result).isEqualTo(ImportSource.CSV_BRADESCO)
        }
    }

    @Nested
    @DisplayName("detect — CSV Santander")
    inner class DetectCsvSantanderTests {
        @Test
        fun `returns CSV_SANTANDER when first line matches common Santander header`() {
            val content = "$santanderHeader\n01/11/2025;10:34;PIX RECEBIDO;100,00;100,00"
            val bytes = content.toByteArray(Charsets.UTF_8)

            val result = ImportSourceDetector.detect(bytes, "santander.csv")

            assertThat(result).isEqualTo(ImportSource.CSV_SANTANDER)
        }
    }

    @Nested
    @DisplayName("detect — unsupported")
    inner class DetectUnsupportedTests {
        @Test
        fun `throws UnsupportedImportSourceException when content is unrecognized`() {
            val bytes = "random,csv,columns\nrow1,row2".toByteArray(Charsets.UTF_8)

            val ex =
                assertThrows<UnsupportedImportSourceException> {
                    ImportSourceDetector.detect(bytes, "unknown.csv")
                }

            assertThat(ex.message).contains("Cannot determine import source")
            assertThat(ex.message).contains("unknown.csv")
        }

        @Test
        fun `throws UnsupportedImportSourceException when file is empty`() {
            val bytes = ByteArray(0)

            val ex =
                assertThrows<UnsupportedImportSourceException> {
                    ImportSourceDetector.detect(bytes, "empty.txt")
                }

            assertThat(ex.message).contains("empty.txt")
        }

        @Test
        fun `throws UnsupportedImportSourceException when fileName is null and content unrecognized`() {
            val bytes = "not,ofx,nor,nubank".toByteArray(Charsets.UTF_8)

            val ex =
                assertThrows<UnsupportedImportSourceException> {
                    ImportSourceDetector.detect(bytes, null)
                }

            assertThat(ex.message).contains("Cannot determine import source")
            assertThat(ex.message).contains("null")
        }
    }

    @Nested
    @DisplayName("detect — order of checks")
    inner class DetectOrderTests {
        @Test
        fun `OFX takes precedence when OFX marker in first 256 bytes even if first line looks like CSV`() {
            val content = "OFXHEADER:100\n$nubankCardHeader\n"
            val bytes = content.toByteArray(Charsets.UTF_8)

            val result = ImportSourceDetector.detect(bytes, "mixed")

            assertThat(result).isEqualTo(ImportSource.OFX)
        }
    }
}
