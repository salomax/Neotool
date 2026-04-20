package io.github.salomax.neotool.banking.model.dto.imports.excel

data class ExcelExtractionLimits(
    val maxSheets: Int = 8,
    val maxRowsPerSheet: Int = 200,
    val maxColumnsPerRow: Int = 24,
    val maxCellChars: Int = 256,
    val maxOutputChars: Int = 24_000,
)

data class ExcelExtractionGuardrailHits(
    val sheetLimitReached: Boolean = false,
    val rowLimitReached: Boolean = false,
    val columnLimitReached: Boolean = false,
    val cellValueTruncationCount: Int = 0,
    val outputLimitReached: Boolean = false,
)

data class ExcelSheetSnapshot(
    val index: Int,
    val name: String,
    val rows: List<List<String>>,
    val maxExtractedColumnCount: Int,
)

data class ExcelSheetSummary(
    val index: Int,
    val name: String,
    val extractedRowCount: Int,
    val maxExtractedColumnCount: Int,
)

data class ExcelExtractionMetadata(
    val workbookType: String,
    val fileName: String?,
    val totalSheetCount: Int,
    val extractedSheetCount: Int,
    val sheetNames: List<String>,
    val sheetSummaries: List<ExcelSheetSummary>,
    val limits: ExcelExtractionLimits,
    val guardrailHits: ExcelExtractionGuardrailHits,
)

data class ExcelExtractionResult(
    val extractedText: String,
    val metadata: ExcelExtractionMetadata,
    val sheets: List<ExcelSheetSnapshot>,
) {
    fun toExtractionMetadataMap(): Map<String, Any?> =
        mapOf(
            "workbook_type" to metadata.workbookType,
            "file_name" to metadata.fileName,
            "total_sheet_count" to metadata.totalSheetCount,
            "extracted_sheet_count" to metadata.extractedSheetCount,
            "sheet_names" to metadata.sheetNames,
            "sheet_summaries" to
                metadata.sheetSummaries.map { summary ->
                    mapOf(
                        "index" to summary.index,
                        "name" to summary.name,
                        "extracted_row_count" to summary.extractedRowCount,
                        "max_extracted_column_count" to summary.maxExtractedColumnCount,
                    )
                },
            "limits" to
                mapOf(
                    "max_sheets" to metadata.limits.maxSheets,
                    "max_rows_per_sheet" to metadata.limits.maxRowsPerSheet,
                    "max_columns_per_row" to metadata.limits.maxColumnsPerRow,
                    "max_cell_chars" to metadata.limits.maxCellChars,
                    "max_output_chars" to metadata.limits.maxOutputChars,
                ),
            "guardrail_hits" to
                mapOf(
                    "sheet_limit_reached" to metadata.guardrailHits.sheetLimitReached,
                    "row_limit_reached" to metadata.guardrailHits.rowLimitReached,
                    "column_limit_reached" to metadata.guardrailHits.columnLimitReached,
                    "cell_value_truncation_count" to metadata.guardrailHits.cellValueTruncationCount,
                    "output_limit_reached" to metadata.guardrailHits.outputLimitReached,
                ),
            "sheet_snapshots" to
                sheets.map { sheet ->
                    mapOf(
                        "index" to sheet.index,
                        "name" to sheet.name,
                        "max_extracted_column_count" to sheet.maxExtractedColumnCount,
                        "rows" to sheet.rows,
                    )
                },
        )
}
