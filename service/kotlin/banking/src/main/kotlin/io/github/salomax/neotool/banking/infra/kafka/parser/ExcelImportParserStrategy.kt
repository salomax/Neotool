package io.github.salomax.neotool.banking.infra.kafka.parser

import io.github.salomax.neotool.banking.domain.import.CanonicalReaderOutputMapper
import io.github.salomax.neotool.banking.domain.import.ImportSource
import io.github.salomax.neotool.banking.domain.import.excel.ExcelExtractionService
import io.github.salomax.neotool.banking.domain.import.excel.reader.ExcelDeterministicRoutingService
import io.github.salomax.neotool.banking.domain.import.fingerprint.FingerprintFileType

private const val ASSISTANT_DOCUMENT_TYPE_BANK_STATEMENT = "BANK_STATEMENT"
private const val ASSISTANT_ACCOUNT_TYPE_BANK = "BANK"
private const val ASSISTANT_SOURCE_TOPIC_IMPORT_FILE = "banking.import.file-requested"

class ExcelImportParserStrategy(
    private val excelExtractionService: ExcelExtractionService = ExcelExtractionService(),
    private val excelDeterministicRoutingService: ExcelDeterministicRoutingService = ExcelDeterministicRoutingService(),
) : BaseImportParserStrategy(
        id = "excel-parser",
        priority = 95,
    ) {
    override fun supports(context: ImportParsingContext): Boolean {
        if (context.requestedSource != ImportSource.AUTO) {
            return false
        }
        return context.discovery.format == ImportFileFormat.XLSX
    }

    override fun parse(context: ImportParsingContext): ImportParseOutcome {
        val extraction =
            excelExtractionService.extract(
                bytes = context.bytes,
                fileName = context.fileName,
            )
        val extractedText = extraction.extractedText
        val extractionMetadata = extraction.toExtractionMetadataMap()
        val fileType = resolveFileType(extractionMetadata)

        val deterministicOutput =
            excelDeterministicRoutingService.route(
                fileType = fileType,
                documentType = ASSISTANT_DOCUMENT_TYPE_BANK_STATEMENT,
                extractedText = extractedText,
                extractionMetadata = extractionMetadata,
            )
        if (deterministicOutput != null) {
            val rows = CanonicalReaderOutputMapper.toParsedTransactions(deterministicOutput)
            return ImportParseOutcome(
                rows = rows,
                resolvedSource = null,
                canonicalOutput = deterministicOutput,
                assistantRequest = null,
            )
        }

        return ImportParseOutcome(
            rows = emptyList(),
            resolvedSource = null,
            canonicalOutput = null,
            assistantRequest =
                ImportAssistantRequest(
                    documentType = ASSISTANT_DOCUMENT_TYPE_BANK_STATEMENT,
                    fileType = fileType.toAssistantFileType(),
                    accountType = ASSISTANT_ACCOUNT_TYPE_BANK,
                    sourceTopic = ASSISTANT_SOURCE_TOPIC_IMPORT_FILE,
                    extractedText = extractedText,
                    extractionMetadata = extractionMetadata,
                ),
        )
    }

    private fun resolveFileType(extractionMetadata: Map<String, Any?>): FingerprintFileType {
        val workbookType = extractionMetadata["workbook_type"]?.toString()?.uppercase()
        return if (workbookType == "XLS") {
            FingerprintFileType.XLS
        } else {
            FingerprintFileType.XLSX
        }
    }

    private fun FingerprintFileType.toAssistantFileType(): String =
        when (this) {
            FingerprintFileType.XLSX -> "XLSX"
            FingerprintFileType.XLS -> "XLS"
            FingerprintFileType.PDF -> "PDF"
        }
}
