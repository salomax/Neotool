package io.github.salomax.neotool.banking.service.import.excel

import io.github.salomax.neotool.banking.model.dto.imports.excel.ExcelExtractionLimits
import io.github.salomax.neotool.banking.service.import.excel.*
import io.github.salomax.neotool.banking.service.import.*
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

@DisplayName("ExcelExtractionService")
class ExcelExtractionServiceTest {
    private val service = ExcelExtractionService()

    @Test
    fun `extract handles xlsx and produces deterministic text and structure`() {
        val bytes = buildSampleWorkbookBytes(xlsx = true)

        val first = service.extract(bytes, "fatura.xlsx")
        val second = service.extract(bytes, "fatura.xlsx")

        assertThat(first).isEqualTo(second)
        assertThat(first.metadata.workbookType).isEqualTo("XLSX")
        assertThat(first.metadata.totalSheetCount).isEqualTo(2)
        assertThat(first.metadata.sheetNames).containsExactly("Resumo", "Parcelas")
        assertThat(first.sheets).hasSize(2)
        assertThat(first.sheets[0].rows).containsExactly(
            listOf("Data", "Descricao", "Valor"),
            listOf("15/01/2026", "Mercado Extra", "-45,90"),
            listOf("16/01/2026", "Farmacia Sao Joao", "-32,10"),
        )
        assertThat(first.extractedText).contains("workbook_type=XLSX")
        assertThat(first.extractedText).contains("## sheet[0] Resumo")
        assertThat(first.extractedText).contains("Mercado Extra")
        assertThat(first.metadata.guardrailHits.outputLimitReached).isFalse()
    }

    @Test
    fun `extract handles xls workbook`() {
        val bytes = buildSampleWorkbookBytes(xlsx = false)

        val result = service.extract(bytes, "fatura.xls")

        assertThat(result.metadata.workbookType).isEqualTo("XLS")
        assertThat(result.metadata.totalSheetCount).isEqualTo(2)
        assertThat(result.sheets[0].rows.first()).containsExactly("Data", "Descricao", "Valor")
        val metadataMap = result.toExtractionMetadataMap()
        assertThat(metadataMap["workbook_type"]).isEqualTo("XLS")
        assertThat(metadataMap["sheet_names"]).isEqualTo(listOf("Resumo", "Parcelas"))
    }

    @Test
    fun `extract enforces guardrails for sheets rows columns cell values and output size`() {
        val bytes = buildGuardrailWorkbookBytes()
        val limits =
            ExcelExtractionLimits(
                maxSheets = 1,
                maxRowsPerSheet = 1,
                maxColumnsPerRow = 2,
                maxCellChars = 8,
                maxOutputChars = 80,
            )

        val result = service.extract(bytes = bytes, fileName = "guardrails.xlsx", limits = limits)

        assertThat(result.metadata.extractedSheetCount).isEqualTo(1)
        assertThat(result.metadata.guardrailHits.sheetLimitReached).isTrue()
        assertThat(result.metadata.guardrailHits.rowLimitReached).isTrue()
        assertThat(result.metadata.guardrailHits.columnLimitReached).isTrue()
        assertThat(result.metadata.guardrailHits.cellValueTruncationCount).isGreaterThan(0)
        assertThat(result.metadata.guardrailHits.outputLimitReached).isTrue()
        assertThat(result.extractedText).contains("...[truncated_by_output_guardrail]")
        assertThat(result.sheets.single().rows.single()).containsExactly("coluna_l...", "coluna2")
    }

    private fun buildSampleWorkbookBytes(xlsx: Boolean): ByteArray {
        val workbook: Workbook = if (xlsx) XSSFWorkbook() else HSSFWorkbook()
        workbook.use { wb ->
            val resumo = wb.createSheet("Resumo")
            resumo.createRow(0).apply {
                createCell(0).setCellValue("Data")
                createCell(1).setCellValue("Descricao")
                createCell(2).setCellValue("Valor")
            }
            resumo.createRow(1).apply {
                createCell(0).setCellValue("15/01/2026")
                createCell(1).setCellValue("Mercado Extra")
                createCell(2).setCellValue("-45,90")
            }
            resumo.createRow(2).apply {
                createCell(0).setCellValue("16/01/2026")
                createCell(1).setCellValue("Farmacia Sao Joao")
                createCell(2).setCellValue("-32,10")
            }

            val parcelas = wb.createSheet("Parcelas")
            parcelas.createRow(0).apply {
                createCell(0).setCellValue("Compra")
                createCell(1).setCellValue("Parcela")
                createCell(2).setCellValue("Valor")
            }
            parcelas.createRow(1).apply {
                createCell(0).setCellValue("Notebook")
                createCell(1).setCellValue("3/12")
                createCell(2).setCellValue("499,99")
            }

            return workbookToBytes(wb)
        }
    }

    private fun buildGuardrailWorkbookBytes(): ByteArray {
        val workbook = XSSFWorkbook()
        workbook.use { wb ->
            val main = wb.createSheet("Extrato")
            main.createRow(0).apply {
                createCell(0).setCellValue("coluna_longa_demais")
                createCell(1).setCellValue("coluna2")
                createCell(2).setCellValue("coluna3")
            }
            main.createRow(1).apply {
                createCell(0).setCellValue("segunda_linha")
                createCell(1).setCellValue("valor")
            }
            wb.createSheet("Ignorada-2").createRow(0).createCell(0).setCellValue("nao deve entrar")
            wb.createSheet("Ignorada-3").createRow(0).createCell(0).setCellValue("nao deve entrar")
            return workbookToBytes(wb)
        }
    }

    private fun workbookToBytes(workbook: Workbook): ByteArray {
        val out = ByteArrayOutputStream()
        workbook.write(out)
        return out.toByteArray()
    }
}
