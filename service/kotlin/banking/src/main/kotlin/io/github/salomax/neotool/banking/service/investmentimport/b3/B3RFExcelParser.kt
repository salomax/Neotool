package io.github.salomax.neotool.banking.service.investmentimport.b3

import jakarta.inject.Singleton
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Singleton
class B3RFExcelParser {
    fun parse(
        fileBytes: ByteArray,
        fileName: String,
    ): B3ParseResult {
        if (!fileName.endsWith(".xlsx", ignoreCase = true)) {
            throw B3ParseException("Formato não suportado. Envie um arquivo .xlsx exportado da B3.")
        }

        val workbook =
            runCatching { WorkbookFactory.create(fileBytes.inputStream()) }.getOrElse { ex ->
                throw B3ParseException("Arquivo Excel inválido ou corrompido.", ex)
            }

        workbook.use { wb ->
            val sheetAndName =
                findRFSheet(wb)
                    ?: throw B3ParseException("Planilha de Renda Fixa não encontrada. Verifique se o arquivo foi exportado da B3.")
            val (sheet, sheetName) = sheetAndName
            val headerRowIndex =
                findHeaderRowIndex(sheet)
                    ?: throw B3ParseException("Cabeçalho da planilha não encontrado. Verifique se a coluna 'Produto' está presente.")

            val columnIndex = buildColumnIndex(sheet.getRow(headerRowIndex))
            if ("productName" !in columnIndex) {
                throw B3ParseException("Coluna obrigatória 'Produto' não encontrada no cabeçalho.")
            }

            val formatter = DataFormatter()
            val parsedRows = mutableListOf<B3RFRow>()
            for (rowNum in (headerRowIndex + 1)..sheet.lastRowNum) {
                val row = sheet.getRow(rowNum) ?: continue
                if (isEmptyRow(row, formatter)) continue
                parseRow(row, columnIndex, formatter)?.let(parsedRows::add)
            }

            return B3ParseResult(
                rows = parsedRows,
                detectedSheetName = sheetName,
                columnCount = columnIndex.size,
            )
        }
    }

    private fun findRFSheet(workbook: Workbook): Pair<Sheet, String>? {
        for (index in 0 until workbook.numberOfSheets) {
            val name = workbook.getSheetName(index)
            val normalized = name.trim()
            if (normalized.equals("RF", ignoreCase = true) || normalized.contains("Renda Fixa", ignoreCase = true)) {
                return workbook.getSheetAt(index) to name
            }
        }

        for (index in 0 until workbook.numberOfSheets) {
            val sheet = workbook.getSheetAt(index)
            if (sheetHasProdutoHeader(sheet)) {
                return sheet to workbook.getSheetName(index)
            }
        }

        return null
    }

    private fun sheetHasProdutoHeader(sheet: Sheet): Boolean {
        val formatter = DataFormatter()
        for (rowNum in 0..minOf(29, sheet.lastRowNum)) {
            val row = sheet.getRow(rowNum) ?: continue
            val hasHeader =
                row.any { cell ->
                    formatter.formatCellValue(cell).trim().equals("Produto", ignoreCase = true)
                }
            if (hasHeader) return true
        }
        return false
    }

    private fun findHeaderRowIndex(sheet: Sheet): Int? {
        val formatter = DataFormatter()
        for (rowNum in 0..minOf(29, sheet.lastRowNum)) {
            val row = sheet.getRow(rowNum) ?: continue
            val matchedColumns =
                row.count { cell ->
                    val value = formatter.formatCellValue(cell).trim()
                    COLUMN_ALIASES.values.any { aliases -> aliases.any { it.equals(value, ignoreCase = true) } }
                }
            if (matchedColumns >= 3) {
                return rowNum
            }
        }
        return null
    }

    private fun buildColumnIndex(headerRow: Row): Map<String, Int> {
        val formatter = DataFormatter()
        val result = mutableMapOf<String, Int>()
        for (cell in headerRow) {
            val value = formatter.formatCellValue(cell).trim()
            for ((field, aliases) in COLUMN_ALIASES) {
                if (aliases.any { it.equals(value, ignoreCase = true) }) {
                    result.putIfAbsent(field, cell.columnIndex)
                    break
                }
            }
        }
        return result
    }

    private fun parseRow(
        row: Row,
        columnIndex: Map<String, Int>,
        formatter: DataFormatter,
    ): B3RFRow? {
        val productName =
            columnIndex["productName"]
                ?.let { formatter.formatCellValue(row.getCell(it)).trim() }
                ?.takeIf { it.isNotBlank() }
                ?: return null

        val currentValue =
            columnIndex["valueCurva"]?.let { parseMoneyCell(row.getCell(it), formatter) }
                ?: columnIndex["valueMtm"]?.let { parseMoneyCell(row.getCell(it), formatter) }
                ?: columnIndex["valueFechamento"]?.let { parseMoneyCell(row.getCell(it), formatter) }

        return B3RFRow(
            rowIndex = row.rowNum,
            productName = productName,
            institutionName = columnIndex["institutionName"]?.let { cellString(row.getCell(it), formatter) },
            issuerName = columnIndex["issuerName"]?.let { cellString(row.getCell(it), formatter) },
            providerCode = columnIndex["providerCode"]?.let { cellString(row.getCell(it), formatter) },
            indexerLabel = columnIndex["indexer"]?.let { cellString(row.getCell(it), formatter) },
            issueDate = columnIndex["issueDate"]?.let { parseDateCell(row.getCell(it), formatter) },
            maturityDate = columnIndex["maturityDate"]?.let { parseDateCell(row.getCell(it), formatter) },
            quantity = columnIndex["quantity"]?.let { parseNumberCell(row.getCell(it), formatter) },
            availableQuantity = columnIndex["availableQuantity"]?.let { parseNumberCell(row.getCell(it), formatter) },
            currentValue = currentValue,
        )
    }

    private fun isEmptyRow(
        row: Row,
        formatter: DataFormatter,
    ): Boolean =
        row.all { formatter.formatCellValue(it).isBlank() }

    private fun cellString(
        cell: Cell?,
        formatter: DataFormatter,
    ): String? {
        if (cell == null) return null
        val value = formatter.formatCellValue(cell).trim()
        return if (value.isBlank() || value == "-") null else value
    }

    private fun parseMoneyCell(
        cell: Cell?,
        formatter: DataFormatter,
    ): BigDecimal? {
        if (cell == null) return null
        if (cell.cellType == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.numericCellValue).setScale(8, RoundingMode.HALF_UP)
        }

        val raw = formatter.formatCellValue(cell).trim()
        if (raw.isBlank() || raw == "-") return null

        val cleaned =
            raw
                .replace(Regex("^[R$\\s]+"), "")
                .replace(".", "")
                .replace(",", ".")
                .trim()

        return cleaned.toBigDecimalOrNull()
    }

    private fun parseNumberCell(
        cell: Cell?,
        formatter: DataFormatter,
    ): BigDecimal? {
        if (cell == null) return null
        if (cell.cellType == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.numericCellValue)
        }

        val raw = formatter.formatCellValue(cell).trim()
        if (raw.isBlank() || raw == "-") return null

        return raw.replace(".", "").replace(",", ".").toBigDecimalOrNull()
    }

    private fun parseDateCell(
        cell: Cell?,
        formatter: DataFormatter,
    ): LocalDate? {
        if (cell == null) return null
        if (cell.cellType == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.localDateTimeCellValue?.toLocalDate()
        }

        val raw = formatter.formatCellValue(cell).trim()
        if (raw.isBlank() || raw == "-") return null

        return runCatching {
            LocalDate.parse(raw, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        }.getOrElse {
            runCatching { LocalDate.parse(raw) }.getOrNull()
        }
    }

    companion object {
        val COLUMN_ALIASES: Map<String, List<String>> =
            mapOf(
                "productName" to listOf("Produto"),
                "institutionName" to listOf("Instituição", "Instituicao"),
                "issuerName" to listOf("Emissor"),
                "providerCode" to listOf("Código", "Codigo"),
                "indexer" to listOf("Indexador"),
                "regimeType" to listOf("Tipo de regime", "Tipo de Regime"),
                "issueDate" to listOf("Data de Emissão", "Data de Emissao", "Data Emissão"),
                "maturityDate" to listOf("Vencimento"),
                "quantity" to listOf("Quantidade"),
                "availableQuantity" to listOf("Quantidade Disponível", "Quantidade Disponivel"),
                "unavailableQuantity" to listOf("Quantidade Indisponível", "Quantidade Indisponivel"),
                "reason" to listOf("Motivo"),
                "counterparty" to listOf("Contraparte"),
                "priceMtm" to listOf("Preço Atualizado MTM", "Preco Atualizado MTM"),
                "valueMtm" to listOf("Valor Atualizado MTM"),
                "priceCurva" to listOf("Preço Atualizado CURVA", "Preco Atualizado CURVA"),
                "valueCurva" to listOf("Valor Atualizado CURVA"),
                "priceFechamento" to listOf("Preço Atualizado FECHAMENTO", "Preco Atualizado FECHAMENTO"),
                "valueFechamento" to listOf("Valor Atualizado FECHAMENTO"),
            )
    }
}

data class B3ParseResult(
    val rows: List<B3RFRow>,
    val detectedSheetName: String?,
    val columnCount: Int,
)

data class B3RFRow(
    val rowIndex: Int,
    val productName: String,
    val institutionName: String?,
    val issuerName: String?,
    val providerCode: String?,
    val indexerLabel: String?,
    val issueDate: LocalDate?,
    val maturityDate: LocalDate?,
    val quantity: BigDecimal?,
    val availableQuantity: BigDecimal?,
    val currentValue: BigDecimal?,
)

class B3ParseException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
