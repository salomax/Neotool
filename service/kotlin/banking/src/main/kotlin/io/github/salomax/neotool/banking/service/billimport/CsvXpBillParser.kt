package io.github.salomax.neotool.banking.service.billimport

import io.github.salomax.neotool.banking.domain.billimport.InstallmentInfo
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.model.domain.imports.ImportTextDecoder
import io.github.salomax.neotool.banking.service.import.parser.csv.CsvParserSupport
import java.math.BigDecimal
import java.time.LocalDate

internal enum class XpBillCsvLayout {
    WITH_HOLDER,
    WITHOUT_HOLDER,
}

internal class XpBillCsvRowParseException(
    val lineNumber: Int,
) : RuntimeException("Malformed XP bill CSV row at line $lineNumber")

internal data class XpBillCsvRow(
    val date: LocalDate,
    val merchant: String,
    val holderName: String? = null,
    val signedAmount: BigDecimal,
    val installment: InstallmentInfo? = null,
)

internal object CsvXpBillParser {
    private const val DELIMITER = ';'

    private val HEADER_WITH_HOLDER =
        listOf("data", "estabelecimento", "portador", "valor", "parcela")
    private val HEADER_WITHOUT_HOLDER =
        listOf("data", "estabelecimento", "valor", "parcela")

    private val INSTALLMENT_REGEX = Regex("""(?i)(\d{1,2})\s+de\s+(\d{1,2})""")

    fun detectLayout(bytes: ByteArray): XpBillCsvLayout? {
        val headerLine = firstNonEmptyLine(bytes) ?: return null
        if (CsvParserSupport.detectDelimiter(headerLine) != DELIMITER) return null

        val columns =
            CsvParserSupport.parseCsvLine(headerLine, DELIMITER)
                .map { CsvParserSupport.normalizeColumnName(it) }

        return when (columns) {
            HEADER_WITH_HOLDER -> XpBillCsvLayout.WITH_HOLDER
            HEADER_WITHOUT_HOLDER -> XpBillCsvLayout.WITHOUT_HOLDER
            else -> null
        }
    }

    fun parse(
        bytes: ByteArray,
        layout: XpBillCsvLayout,
    ): List<XpBillCsvRow> {
        val lines =
            ImportTextDecoder.decode(bytes).text
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toList()
        if (lines.size <= 1) return emptyList()

        val dataLines = lines.drop(1)
        return dataLines.mapIndexed { index, line ->
            parseRow(line, layout)
                ?: throw XpBillCsvRowParseException(lineNumber = index + 2)
        }
    }

    fun parseInstallment(value: String): InstallmentInfo? {
        val trimmed = value.trim()
        if (trimmed.isBlank() || trimmed == "-") return null

        val match = INSTALLMENT_REGEX.find(trimmed.replace("\\s+".toRegex(), " ")) ?: return null
        val current = match.groupValues[1].toIntOrNull() ?: return null
        val total = match.groupValues[2].toIntOrNull() ?: return null
        if (current < 1 || total < 1 || current > total) return null
        return InstallmentInfo(current = current, total = total)
    }

    fun toCreditDebitType(signedAmount: BigDecimal): Pair<CreditDebitType, BigDecimal> =
        if (signedAmount.signum() < 0) {
            CreditDebitType.CREDIT to signedAmount.abs()
        } else {
            CreditDebitType.DEBIT to signedAmount.abs()
        }

    private fun parseRow(
        line: String,
        layout: XpBillCsvLayout,
    ): XpBillCsvRow? {
        val fields = CsvParserSupport.parseCsvLine(line, DELIMITER)
        val date = CsvParserSupport.parseDate(fields.getOrNull(0).orEmpty()) ?: return null
        val merchant = fields.getOrNull(1)?.trim().orEmpty()
        if (merchant.isBlank()) return null

        return when (layout) {
            XpBillCsvLayout.WITH_HOLDER -> {
                if (fields.size < 5) return null
                val holderName = fields.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() }
                val amount = CsvParserSupport.parseAmount(fields.getOrNull(3).orEmpty()) ?: return null
                val installment = parseInstallment(fields.getOrNull(4).orEmpty())
                XpBillCsvRow(
                    date = date,
                    merchant = merchant,
                    holderName = holderName,
                    signedAmount = amount,
                    installment = installment,
                )
            }
            XpBillCsvLayout.WITHOUT_HOLDER -> {
                if (fields.size < 4) return null
                val amount = CsvParserSupport.parseAmount(fields.getOrNull(2).orEmpty()) ?: return null
                val installment = parseInstallment(fields.getOrNull(3).orEmpty())
                XpBillCsvRow(
                    date = date,
                    merchant = merchant,
                    signedAmount = amount,
                    installment = installment,
                )
            }
        }
    }

    private fun firstNonEmptyLine(bytes: ByteArray): String? =
        ImportTextDecoder.decode(bytes).text
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?.removePrefix("\uFEFF")
}
