package io.github.salomax.neotool.banking.infra.kafka.parser

import io.github.salomax.neotool.banking.domain.import.ImportSource
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

@DisplayName("ExcelImportParserStrategy")
class ExcelImportParserStrategyTest {
    private val strategy = ExcelImportParserStrategy()

    @Test
    fun `known tabular workbook routes to deterministic rows`() {
        val bytes = buildKnownWorkbookBytes()
        val context =
            ImportParsingContext(
                bytes = bytes,
                fileName = "extrato.xlsx",
                requestedSource = ImportSource.AUTO,
                discovery = ImportFormatDiscoveryResult(ImportFileFormat.XLSX, null, "extension_xlsx"),
            )

        val outcome = strategy.parse(context)

        assertThat(outcome.assistantRequest).isNull()
        assertThat(outcome.canonicalOutput).isNotNull
        assertThat(outcome.rows).hasSize(2)
        assertThat(outcome.rows[0].description).isEqualTo("Salario Empresa XYZ")
        assertThat(outcome.rows[0].creditDebitType.name).isEqualTo("CREDIT")
        assertThat(outcome.rows[1].creditDebitType.name).isEqualTo("DEBIT")
    }

    @Test
    fun `unknown workbook creates assistant request payload with xls file type when needed`() {
        val bytes = buildUnknownWorkbookBytes(xlsx = false)
        val context =
            ImportParsingContext(
                bytes = bytes,
                fileName = "planilha.xls",
                requestedSource = ImportSource.AUTO,
                discovery = ImportFormatDiscoveryResult(ImportFileFormat.XLSX, null, "extension_xlsx"),
            )

        val outcome = strategy.parse(context)

        assertThat(outcome.rows).isEmpty()
        assertThat(outcome.canonicalOutput).isNull()
        assertThat(outcome.assistantRequest).isNotNull
        assertThat(outcome.assistantRequest!!.documentType).isEqualTo("BANK_STATEMENT")
        assertThat(outcome.assistantRequest!!.accountType).isEqualTo("BANK")
        assertThat(outcome.assistantRequest!!.fileType).isEqualTo("XLS")
        assertThat(outcome.assistantRequest!!.sourceTopic).isEqualTo("banking.import.file-requested")
        assertThat(outcome.assistantRequest!!.extractedText).contains("workbook_type=XLS")
    }

    private fun buildKnownWorkbookBytes(): ByteArray {
        val workbook = XSSFWorkbook()
        workbook.use { wb ->
            val sheet = wb.createSheet("Extrato")
            sheet.createRow(0).apply {
                createCell(0).setCellValue("Data")
                createCell(1).setCellValue("Descricao")
                createCell(2).setCellValue("Valor")
            }
            sheet.createRow(1).apply {
                createCell(0).setCellValue("03/02/2026")
                createCell(1).setCellValue("Salario Empresa XYZ")
                createCell(2).setCellValue("5600,00")
            }
            sheet.createRow(2).apply {
                createCell(0).setCellValue("04/02/2026")
                createCell(1).setCellValue("Mercado Central")
                createCell(2).setCellValue("-120,45")
            }
            return workbookToBytes(wb)
        }
    }

    private fun buildUnknownWorkbookBytes(xlsx: Boolean): ByteArray {
        val workbook: Workbook = if (xlsx) XSSFWorkbook() else HSSFWorkbook()
        workbook.use { wb ->
            val sheet = wb.createSheet("Planilha")
            sheet.createRow(0).apply {
                createCell(0).setCellValue("ColunaA")
                createCell(1).setCellValue("ColunaB")
            }
            sheet.createRow(1).apply {
                createCell(0).setCellValue("valor-a")
                createCell(1).setCellValue("valor-b")
            }
            return workbookToBytes(wb)
        }
    }

    private fun workbookToBytes(workbook: Workbook): ByteArray {
        val out = ByteArrayOutputStream()
        workbook.write(out)
        return out.toByteArray()
    }
}
