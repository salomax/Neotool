package io.github.salomax.neotool.banking.service.investmentimport

import io.github.salomax.neotool.banking.service.investmentimport.b3.B3ParseException
import io.github.salomax.neotool.banking.service.investmentimport.b3.B3RFExcelParser
import org.apache.poi.ss.usermodel.CreationHelper
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Date

class B3RFExcelParserTest {
    private val parser = B3RFExcelParser()

    @Test
    fun `happy path parses b3 rows`() {
        val headers = listOf("Produto", "Instituição", "Emissor", "Código", "Indexador", "Data de Emissão", "Vencimento", "Quantidade", "Valor Atualizado CURVA")
        val rows =
            listOf(
                mapOf(
                    "Produto" to "CDB - BANCO ABC",
                    "Instituição" to "Banco XPTO",
                    "Emissor" to "BANCO ABC",
                    "Código" to "ABC-001",
                    "Indexador" to "CDI",
                    "Data de Emissão" to "10/01/2024",
                    "Vencimento" to "10/01/2027",
                    "Quantidade" to "1000",
                    "Valor Atualizado CURVA" to "R$ 1.189,65690597",
                ),
                mapOf(
                    "Produto" to "LCI / LCA - BANCO XYZ",
                    "Instituição" to "Banco XPTO",
                    "Emissor" to "BANCO XYZ",
                    "Quantidade" to "200",
                    "Valor Atualizado CURVA" to "-",
                    "Valor Atualizado MTM" to "100,10",
                ),
            )

        val bytes = buildWorkbook(headers = headers, rows = rows)

        val result = parser.parse(bytes, "b3.xlsx")

        assertThat(result.rows).hasSize(2)
        assertThat(result.rows[0].providerCode).isEqualTo("ABC-001")
        assertThat(result.rows[0].currentValue).isEqualTo(BigDecimal("1189.65690597"))
        assertThat(result.rows[1].currentValue).isNull()
    }

    @Test
    fun `parses with shuffled columns`() {
        val headers = listOf("Valor Atualizado CURVA", "Quantidade", "Produto", "Instituição", "Código")
        val rows =
            listOf(
                mapOf(
                    "Produto" to "CDB - BANCO ABC",
                    "Instituição" to "Banco XPTO",
                    "Código" to "ABC-001",
                    "Quantidade" to "10",
                    "Valor Atualizado CURVA" to "100,50",
                ),
            )

        val bytes = buildWorkbook(headers = headers, rows = rows)
        val result = parser.parse(bytes, "b3.xlsx")

        assertThat(result.rows).hasSize(1)
        assertThat(result.rows.first().productName).isEqualTo("CDB - BANCO ABC")
        assertThat(result.rows.first().providerCode).isEqualTo("ABC-001")
        assertThat(result.rows.first().currentValue).isEqualTo(BigDecimal("100.50"))
    }

    @Test
    fun `missing optional codigo does not fail`() {
        val headers = listOf("Produto", "Instituição", "Quantidade")
        val rows = listOf(mapOf("Produto" to "CDB - BANCO ABC", "Instituição" to "Banco XPTO", "Quantidade" to "10"))

        val bytes = buildWorkbook(headers = headers, rows = rows)
        val result = parser.parse(bytes, "b3.xlsx")

        assertThat(result.rows).hasSize(1)
        assertThat(result.rows.first().providerCode).isNull()
    }

    @Test
    fun `currentValue fallback mtm then fechamento`() {
        val headers = listOf("Produto", "Valor Atualizado MTM", "Valor Atualizado FECHAMENTO")
        val rows =
            listOf(
                mapOf("Produto" to "CDB - A", "Valor Atualizado MTM" to "120,40"),
                mapOf("Produto" to "CDB - B", "Valor Atualizado FECHAMENTO" to "130,50"),
            )
        val bytes = buildWorkbook(headers = headers, rows = rows)

        val result = parser.parse(bytes, "b3.xlsx")

        assertThat(result.rows).hasSize(2)
        assertThat(result.rows[0].currentValue).isEqualTo(BigDecimal("120.40"))
        assertThat(result.rows[1].currentValue).isEqualTo(BigDecimal("130.50"))
    }

    @Test
    fun `xls extension is rejected`() {
        val bytes = buildWorkbook(headers = listOf("Produto"), rows = listOf(mapOf("Produto" to "A")))

        assertThatThrownBy { parser.parse(bytes, "b3.xls") }
            .isInstanceOf(B3ParseException::class.java)
    }

    @Test
    fun `no rf sheet throws parse exception`() {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("Ações")
        sheet.createRow(0).createCell(0).setCellValue("Ticker")
        val out = ByteArrayOutputStream()
        wb.write(out)
        wb.close()

        assertThatThrownBy { parser.parse(out.toByteArray(), "b3.xlsx") }
            .isInstanceOf(B3ParseException::class.java)
    }

    @Test
    fun `missing produto column throws parse exception`() {
        val bytes = buildWorkbook(headers = listOf("Instituição", "Quantidade", "Vencimento"), rows = listOf(mapOf("Instituição" to "X")))

        assertThatThrownBy { parser.parse(bytes, "b3.xlsx") }
            .isInstanceOf(B3ParseException::class.java)
    }

    @Test
    fun `header row can be below first line`() {
        val bytes = buildWorkbook(headers = listOf("Produto", "Quantidade"), rows = listOf(mapOf("Produto" to "CDB - A", "Quantidade" to "1")), headerRowIndex = 3)
        val result = parser.parse(bytes, "b3.xlsx")
        assertThat(result.rows).hasSize(1)
    }

    @Test
    fun `date cell string format parses to local date`() {
        val bytes = buildWorkbook(headers = listOf("Produto", "Data de Emissão"), rows = listOf(mapOf("Produto" to "CDB - A", "Data de Emissão" to "10/01/2024")))
        val result = parser.parse(bytes, "b3.xlsx")
        assertThat(result.rows.first().issueDate).isEqualTo(LocalDate.of(2024, 1, 10))
    }

    @Test
    fun `date cell numeric format parses to local date`() {
        val wb = XSSFWorkbook()
        val helper: CreationHelper = wb.creationHelper
        val dateStyle: XSSFCellStyle = wb.createCellStyle()
        dateStyle.dataFormat = helper.createDataFormat().getFormat("dd/MM/yyyy")
        val sheet = wb.createSheet("Renda Fixa")
        val header = sheet.createRow(0)
        header.createCell(0).setCellValue("Produto")
        header.createCell(1).setCellValue("Data de Emissão")
        val row = sheet.createRow(1)
        row.createCell(0).setCellValue("CDB - A")
        row.createCell(1).apply {
            setCellValue(Date.from(LocalDate.of(2024, 1, 10).atStartOfDay(java.time.ZoneOffset.UTC).toInstant()))
            cellStyle = dateStyle
        }
        val out = ByteArrayOutputStream()
        wb.write(out)
        wb.close()

        val result = parser.parse(out.toByteArray(), "b3.xlsx")
        assertThat(result.rows.first().issueDate).isEqualTo(LocalDate.of(2024, 1, 10))
    }

    private fun buildWorkbook(
        headers: List<String>,
        rows: List<Map<String, String>>,
        sheetName: String = "Renda Fixa",
        headerRowIndex: Int = 0,
    ): ByteArray {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet(sheetName)

        repeat(headerRowIndex) { idx ->
            sheet.createRow(idx)
        }

        val headerRow = sheet.createRow(headerRowIndex)
        headers.forEachIndexed { index, header ->
            headerRow.createCell(index).setCellValue(header)
        }

        rows.forEachIndexed { rowIndex, data ->
            val row = sheet.createRow(headerRowIndex + rowIndex + 1)
            headers.forEachIndexed { cellIndex, header ->
                row.createCell(cellIndex).setCellValue(data[header] ?: "")
            }
        }

        val out = ByteArrayOutputStream()
        wb.write(out)
        wb.close()
        return out.toByteArray()
    }
}
