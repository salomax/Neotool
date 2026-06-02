package io.github.salomax.neotool.banking.model.dto.imports

import io.github.salomax.neotool.banking.domain.import.ImportSource
import io.github.salomax.neotool.banking.domain.import.ParsedTransaction
import io.github.salomax.neotool.banking.service.import.parser.ImportFileFormat
import io.github.salomax.neotool.banking.service.import.parser.OfxDocumentFormat
import io.github.salomax.neotool.banking.service.import.parser.OfxStatementIntent
import java.time.LocalDate

data class ImportFormatDiscoveryResult(
    val format: ImportFileFormat,
    val detectedSource: ImportSource?,
    val reason: String,
)

data class AssistantStatementExtractionResult(
    val rows: List<ParsedTransaction>,
    val warnings: List<String> = emptyList(),
)

data class OfxParseResult(
    val rows: List<ParsedTransaction>,
    val statementIntent: OfxStatementIntent,
    val format: OfxDocumentFormat,
    val accountIdentifier: String? = null,
    val statementFrom: LocalDate? = null,
    val statementTo: LocalDate? = null,
)
