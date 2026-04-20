package io.github.salomax.neotool.banking.service.import.excel

import io.github.salomax.neotool.banking.domain.import.*
import io.github.salomax.neotool.banking.model.dto.imports.excel.ExcelExtractionGuardrailHits
import io.github.salomax.neotool.banking.model.dto.imports.excel.ExcelExtractionLimits
import io.github.salomax.neotool.banking.model.dto.imports.excel.ExcelExtractionMetadata
import io.github.salomax.neotool.banking.model.dto.imports.excel.ExcelExtractionResult
import io.github.salomax.neotool.banking.model.dto.imports.excel.ExcelSheetSnapshot
import io.github.salomax.neotool.banking.model.dto.imports.excel.ExcelSheetSummary
import jakarta.inject.Singleton
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayInputStream
import java.util.Locale
import kotlin.math.min

@Singleton
open class ExcelExtractionService(
    private val defaultLimits: ExcelExtractionLimits = ExcelExtractionLimits(),
) {
    open fun extract(
        bytes: ByteArray,
        fileName: String? = null,
    ): ExcelExtractionResult = extract(bytes = bytes, fileName = fileName, limits = defaultLimits)

    open fun extract(
        bytes: ByteArray,
        fileName: String?,
        limits: ExcelExtractionLimits,
    ): ExcelExtractionResult {
        validateLimits(limits)
        ByteArrayInputStream(bytes).use { input ->
            WorkbookFactory.create(input).use { workbook ->
                return extractWorkbook(workbook, fileName, limits)
            }
        }
    }

    private fun extractWorkbook(
        workbook: Workbook,
        fileName: String?,
        limits: ExcelExtractionLimits,
    ): ExcelExtractionResult {
        val formatter = DataFormatter(Locale.US, true)
        val evaluator = workbook.creationHelper.createFormulaEvaluator()
        val totalSheetCount = workbook.numberOfSheets
        val extractedSheetCount = min(totalSheetCount, limits.maxSheets)
        val sheetNames = (0 until totalSheetCount).map { workbook.getSheetName(it) }

        var rowLimitReached = false
        var columnLimitReached = false
        var cellValueTruncationCount = 0

        val snapshots =
            (0 until extractedSheetCount).map { index ->
                val capture =
                    captureSheet(
                        workbook = workbook,
                        index = index,
                        formatter = formatter,
                        evaluator = evaluator,
                        limits = limits,
                    )
                rowLimitReached = rowLimitReached || capture.rowLimitReached
                columnLimitReached = columnLimitReached || capture.columnLimitReached
                cellValueTruncationCount += capture.cellValueTruncationCount
                capture.snapshot
            }

        val rawText = buildTextSnapshot(resolveWorkbookType(workbook), snapshots)
        val outputLimitReached = rawText.length > limits.maxOutputChars
        val extractedText =
            if (!outputLimitReached) {
                rawText
            } else {
                rawText.take(limits.maxOutputChars) + "\n...[truncated_by_output_guardrail]"
            }

        val metadata =
            ExcelExtractionMetadata(
                workbookType = resolveWorkbookType(workbook),
                fileName = fileName,
                totalSheetCount = totalSheetCount,
                extractedSheetCount = extractedSheetCount,
                sheetNames = sheetNames,
                sheetSummaries =
                    snapshots.map { snapshot ->
                        ExcelSheetSummary(
                            index = snapshot.index,
                            name = snapshot.name,
                            extractedRowCount = snapshot.rows.size,
                            maxExtractedColumnCount = snapshot.maxExtractedColumnCount,
                        )
                    },
                limits = limits,
                guardrailHits =
                    ExcelExtractionGuardrailHits(
                        sheetLimitReached = totalSheetCount > limits.maxSheets,
                        rowLimitReached = rowLimitReached,
                        columnLimitReached = columnLimitReached,
                        cellValueTruncationCount = cellValueTruncationCount,
                        outputLimitReached = outputLimitReached,
                    ),
            )
        return ExcelExtractionResult(
            extractedText = extractedText,
            metadata = metadata,
            sheets = snapshots,
        )
    }

    private fun captureSheet(
        workbook: Workbook,
        index: Int,
        formatter: DataFormatter,
        evaluator: org.apache.poi.ss.usermodel.FormulaEvaluator,
        limits: ExcelExtractionLimits,
    ): SheetCapture {
        val sheet = workbook.getSheetAt(index)
        val rows = mutableListOf<List<String>>()
        var rowLimitReached = false
        var columnLimitReached = false
        var cellValueTruncationCount = 0
        var maxExtractedColumnCount = 0

        if (sheet.physicalNumberOfRows > 0) {
            for (rowIndex in sheet.firstRowNum..sheet.lastRowNum) {
                val row = sheet.getRow(rowIndex) ?: continue
                if (rows.size >= limits.maxRowsPerSheet) {
                    rowLimitReached = true
                    break
                }
                val values =
                    extractRowValues(
                        row = row,
                        formatter = formatter,
                        evaluator = evaluator,
                        maxColumnsPerRow = limits.maxColumnsPerRow,
                        maxCellChars = limits.maxCellChars,
                    )
                columnLimitReached = columnLimitReached || values.columnLimitReached
                cellValueTruncationCount += values.cellValueTruncationCount
                if (values.values.isEmpty()) continue
                maxExtractedColumnCount = maxOf(maxExtractedColumnCount, values.values.size)
                rows += values.values
            }
        }

        val snapshot =
            ExcelSheetSnapshot(
                index = index,
                name = sheet.sheetName,
                rows = rows,
                maxExtractedColumnCount = maxExtractedColumnCount,
            )
        return SheetCapture(
            snapshot = snapshot,
            rowLimitReached = rowLimitReached,
            columnLimitReached = columnLimitReached,
            cellValueTruncationCount = cellValueTruncationCount,
        )
    }

    private fun extractRowValues(
        row: Row,
        formatter: DataFormatter,
        evaluator: org.apache.poi.ss.usermodel.FormulaEvaluator,
        maxColumnsPerRow: Int,
        maxCellChars: Int,
    ): RowExtraction {
        val availableColumns = maxOf(row.lastCellNum.toInt(), 0)
        if (availableColumns == 0) return RowExtraction(emptyList(), false, 0)

        val extractedColumns = min(availableColumns, maxColumnsPerRow)
        val rawValues = ArrayList<String>(extractedColumns)
        var cellValueTruncationCount = 0

        for (columnIndex in 0 until extractedColumns) {
            val cell = row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)
            val normalized =
                if (cell == null) {
                    ""
                } else {
                    normalizeCellValue(
                        formatter.formatCellValue(cell, evaluator),
                    )
                }
            if (normalized.length > maxCellChars) {
                cellValueTruncationCount++
                rawValues += normalized.take(maxCellChars) + "..."
            } else {
                rawValues += normalized
            }
        }

        while (rawValues.isNotEmpty() && rawValues.last().isBlank()) {
            rawValues.removeAt(rawValues.lastIndex)
        }

        if (rawValues.none {
                it.isNotBlank()
            }
        ) {
            return RowExtraction(emptyList(), availableColumns > maxColumnsPerRow, cellValueTruncationCount)
        }
        return RowExtraction(rawValues, availableColumns > maxColumnsPerRow, cellValueTruncationCount)
    }

    private fun buildTextSnapshot(
        workbookType: String,
        sheets: List<ExcelSheetSnapshot>,
    ): String {
        val builder = StringBuilder()
        builder.append("workbook_type=").append(workbookType).append('\n')
        builder.append("sheet_count=").append(sheets.size).append('\n')
        sheets.forEach { sheet ->
            builder.append('\n')
            builder.append("## sheet[").append(sheet.index).append("] ").append(sheet.name).append('\n')
            if (sheet.rows.isEmpty()) {
                builder.append("(empty)").append('\n')
                return@forEach
            }
            val columnCount = maxOf(sheet.maxExtractedColumnCount, 1)
            builder.append("| row ")
            for (columnIndex in 1..columnCount) {
                builder.append("| c").append(columnIndex).append(' ')
            }
            builder.append('|').append('\n')
            builder.append("| --- ")
            for (columnIndex in 1..columnCount) {
                builder.append("| --- ")
            }
            builder.append('|').append('\n')
            sheet.rows.forEachIndexed { rowIndex, row ->
                builder.append("| ").append(rowIndex + 1).append(' ')
                for (columnIndex in 0 until columnCount) {
                    builder.append("| ")
                    builder.append(escapeMarkdown(row.getOrNull(columnIndex).orEmpty()))
                    builder.append(' ')
                }
                builder.append('|').append('\n')
            }
        }
        return builder.toString()
    }

    private fun resolveWorkbookType(workbook: Workbook): String =
        when (workbook) {
            is XSSFWorkbook -> "XLSX"
            is HSSFWorkbook -> "XLS"
            else -> "UNKNOWN"
        }

    private fun normalizeCellValue(value: String): String =
        value
            .replace('\r', ' ')
            .replace('\n', ' ')
            .replace(CELL_WHITESPACE_REGEX, " ")
            .trim()

    private fun escapeMarkdown(value: String): String = value.replace("|", "\\|")

    private fun validateLimits(limits: ExcelExtractionLimits) {
        require(limits.maxSheets > 0) { "maxSheets must be greater than zero" }
        require(limits.maxRowsPerSheet > 0) { "maxRowsPerSheet must be greater than zero" }
        require(limits.maxColumnsPerRow > 0) { "maxColumnsPerRow must be greater than zero" }
        require(limits.maxCellChars > 0) { "maxCellChars must be greater than zero" }
        require(limits.maxOutputChars > 0) { "maxOutputChars must be greater than zero" }
    }

    private data class SheetCapture(
        val snapshot: ExcelSheetSnapshot,
        val rowLimitReached: Boolean,
        val columnLimitReached: Boolean,
        val cellValueTruncationCount: Int,
    )

    private data class RowExtraction(
        val values: List<String>,
        val columnLimitReached: Boolean,
        val cellValueTruncationCount: Int,
    )

    private companion object {
        private val CELL_WHITESPACE_REGEX = Regex("\\s+")
    }
}
