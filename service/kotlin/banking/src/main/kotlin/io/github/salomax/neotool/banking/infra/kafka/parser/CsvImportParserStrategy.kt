package io.github.salomax.neotool.banking.infra.kafka.parser

import io.github.salomax.neotool.banking.domain.import.ImportSource
import io.github.salomax.neotool.banking.domain.import.ImportTextDecoder
import io.github.salomax.neotool.banking.domain.import.ParsedTransaction
import io.github.salomax.neotool.banking.domain.import.isCsvSource
import io.github.salomax.neotool.banking.domain.import.parser.CsvBancoDoBrasilParser
import io.github.salomax.neotool.banking.domain.import.parser.CsvBradescoParser
import io.github.salomax.neotool.banking.domain.import.parser.CsvItauParser
import io.github.salomax.neotool.banking.domain.import.parser.CsvNubankParser
import io.github.salomax.neotool.banking.domain.import.parser.CsvSantanderParser
import io.github.salomax.neotool.banking.domain.import.parser.csv.CsvFingerprintBuilder
import io.github.salomax.neotool.banking.domain.import.parser.csv.CsvReaderRegistry
import io.github.salomax.neotool.banking.domain.import.parser.csv.CsvReaderSelectionMode
import io.github.salomax.neotool.banking.domain.import.parser.csv.GenericCsvReader
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.banking.infra.kafka.CsvImportMetrics
import io.github.salomax.neotool.common.error.BusinessRuleException
import org.slf4j.LoggerFactory

class CsvImportParserStrategy(
    private val csvImportMetrics: CsvImportMetrics,
) : BaseImportParserStrategy(
    id = "csv-parser",
    priority = 90,
) {
    private val dateHeaderHints = setOf("data", "date", "lancamento", "dt_lancamento", "data_lancamento")
    private val amountHeaderHints = setOf("valor", "amount", "credito", "debito", "value", "valor_rs")
    private val log = LoggerFactory.getLogger(javaClass)
    private val csvReaderRegistry = CsvReaderRegistry(
        readers = listOf(
            CsvNubankParser,
            CsvBancoDoBrasilParser,
            CsvItauParser,
            CsvBradescoParser,
            CsvSantanderParser,
        ),
    )

    override fun supports(context: ImportParsingContext): Boolean {
        if (context.requestedSource.isCsvSource()) {
            // If caller says "CSV" but content signature says OFX/XLSX, reject as unsupported format.
            return context.discovery.format != ImportFileFormat.OFX &&
                context.discovery.format != ImportFileFormat.XLSX
        }
        return context.requestedSource == ImportSource.AUTO &&
            context.discovery.format == ImportFileFormat.CSV
    }

    override fun parse(context: ImportParsingContext): ImportParseOutcome {
        val sourceName = when {
            context.requestedSource.isCsvSource() -> context.requestedSource.name
            context.discovery.detectedSource?.isCsvSource() == true -> context.discovery.detectedSource.name
            else -> ImportSource.CSV_AUTO.name
        }
        return parseCsvWithFallback(context.bytes, sourceName, context.fileName)
    }

    private fun parseCsvWithFallback(bytes: ByteArray, sourceName: String, fileName: String?): ImportParseOutcome {
        val fingerprint = CsvFingerprintBuilder.build(bytes)
        val selection = csvReaderRegistry.select(fingerprint)
        selection.selectedMatch?.let { match ->
            csvImportMetrics.recordReaderMatch(
                readerId = match.readerId,
                source = sourceName,
                mode = selection.mode.name,
            )
        }

        log.info(
            "CSV reader selection: source={}, mode={}, selectedReader={}, score={}, reasons={}, topMatches={}",
            sourceName,
            selection.mode,
            selection.selectedReader?.id(),
            selection.selectedMatch?.score,
            selection.selectedMatch?.reasons,
            selection.rankedMatches.take(3).map { "${it.readerId}:${"%.2f".format(it.score)}" },
        )

        data class ParseAttempt(
            val rows: List<ParsedTransaction>,
            val failureReason: String? = null,
        )

        fun parseSafely(readerId: String, parser: () -> List<ParsedTransaction>): ParseAttempt {
            return try {
                val rows = parser()
                if (rows.isEmpty()) {
                    csvImportMetrics.recordParseFailure(
                        readerId = readerId,
                        reason = "empty_result",
                        source = sourceName,
                    )
                    ParseAttempt(rows = emptyList(), failureReason = "empty_result")
                } else {
                    ParseAttempt(rows = rows)
                }
            } catch (e: Exception) {
                csvImportMetrics.recordParseFailure(
                    readerId = readerId,
                    reason = "exception",
                    source = sourceName,
                )
                log.warn("CSV parser failed: source={}, reader={}, reason={}", sourceName, readerId, e.message)
                ParseAttempt(rows = emptyList(), failureReason = "exception")
            }
        }

        var genericAlreadyAttempted = false
        var parserUsedReaderId: String? = null
        val primaryAttempt = when (selection.mode) {
            CsvReaderSelectionMode.SPECIFIC_ONLY -> {
                parserUsedReaderId = selection.selectedReader?.id()
                parseSafely(selection.selectedReader?.id() ?: "unknown") { selection.selectedReader?.parse(bytes).orEmpty() }
            }

            CsvReaderSelectionMode.SPECIFIC_THEN_GENERIC_FALLBACK -> {
                val specificAttempt = parseSafely(selection.selectedReader?.id() ?: "unknown") {
                    selection.selectedReader?.parse(bytes).orEmpty()
                }
                if (specificAttempt.rows.isNotEmpty()) {
                    parserUsedReaderId = selection.selectedReader?.id()
                    specificAttempt
                } else {
                    csvImportMetrics.recordFallback(
                        source = sourceName,
                        reason = "specific_then_generic",
                        selectedReaderId = selection.selectedReader?.id(),
                    )
                    genericAlreadyAttempted = true
                    parserUsedReaderId = GenericCsvReader.id()
                    parseSafely(GenericCsvReader.id()) { GenericCsvReader.parse(bytes) }
                }
            }

            CsvReaderSelectionMode.GENERIC_ONLY -> {
                csvImportMetrics.recordFallback(
                    source = sourceName,
                    reason = "generic_only_mode",
                    selectedReaderId = selection.selectedReader?.id(),
                )
                genericAlreadyAttempted = true
                parserUsedReaderId = GenericCsvReader.id()
                parseSafely(GenericCsvReader.id()) { GenericCsvReader.parse(bytes) }
            }
        }

        if (primaryAttempt.rows.isNotEmpty()) {
            log.info(
                "CSV parse success: source={}, mode={}, selectedReader={}, rows={}, score={}",
                sourceName,
                selection.mode,
                selection.selectedReader?.id() ?: GenericCsvReader.id(),
                primaryAttempt.rows.size,
                selection.selectedMatch?.score,
            )
            return ImportParseOutcome(
                rows = primaryAttempt.rows,
                resolvedSource = readerIdToImportSource(parserUsedReaderId),
            )
        }

        if (selection.selectedReader != null && !genericAlreadyAttempted) {
            csvImportMetrics.recordFallback(
                source = sourceName,
                reason = "last_safeguard",
                selectedReaderId = selection.selectedReader.id(),
            )
            val genericAttempt = parseSafely(GenericCsvReader.id()) { GenericCsvReader.parse(bytes) }
            if (genericAttempt.rows.isNotEmpty()) {
                log.info(
                    "CSV generic fallback recovered parse: source={}, selectedReader={}, rows={}",
                    sourceName,
                    selection.selectedReader.id(),
                    genericAttempt.rows.size,
                )
                return ImportParseOutcome(
                    rows = genericAttempt.rows,
                    resolvedSource = null,
                )
            }
        }

        if (primaryAttempt.failureReason != null) {
            log.warn(
                "CSV parse produced no rows: source={}, mode={}, selectedReader={}, reason={}",
                sourceName,
                selection.mode,
                selection.selectedReader?.id(),
                primaryAttempt.failureReason,
            )
        }

        if (!isHeaderOnlyEmptyStatementAllowed(bytes, fingerprint, selection.selectedReader != null)) {
            throw BusinessRuleException(
                errorCode = BankingErrorCode.IMPORT_FILE_FORMAT_NOT_SUPPORTED,
                parameters = mapOf(
                    "fileName" to (fileName ?: ""),
                    "source" to sourceName,
                    "reason" to "csv_no_parseable_rows",
                    "mode" to selection.mode.name,
                    "selectedReaderId" to (selection.selectedReader?.id() ?: ""),
                ),
            )
        }

        val hintedSource = readerIdToImportSource(selection.selectedReader?.id())
        return ImportParseOutcome(
            rows = emptyList(),
            resolvedSource = hintedSource,
        )
    }

    private fun isHeaderOnlyEmptyStatementAllowed(
        bytes: ByteArray,
        fingerprint: io.github.salomax.neotool.banking.domain.import.parser.csv.CsvFingerprint,
        hasSpecificReaderHint: Boolean,
    ): Boolean {
        val nonBlankLineCount = ImportTextDecoder.decode(bytes).text.lineSequence().count { it.trim().isNotBlank() }
        if (nonBlankLineCount == 0) return false
        if (!fingerprint.hasHeader) return false
        if (nonBlankLineCount > 1) return false
        if (hasSpecificReaderHint) return true

        val columns = fingerprint.normalizedColumns
        val hasDate = columns.any { c -> dateHeaderHints.any(c::contains) }
        val hasAmount = columns.any { c -> amountHeaderHints.any(c::contains) }
        return hasDate && hasAmount
    }

    private fun readerIdToImportSource(readerId: String?): ImportSource? = when (readerId) {
        CsvNubankParser.id() -> ImportSource.CSV_NUBANK
        CsvItauParser.id() -> ImportSource.CSV_ITAU
        CsvBradescoParser.id() -> ImportSource.CSV_BRADESCO
        CsvSantanderParser.id() -> ImportSource.CSV_SANTANDER
        else -> null
    }
}
