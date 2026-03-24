package io.github.salomax.neotool.banking.service.import.parser

import io.github.salomax.neotool.banking.domain.import.*
import io.github.salomax.neotool.banking.domain.import.CanonicalReaderOutput
import io.github.salomax.neotool.banking.domain.import.ImportSource
import io.github.salomax.neotool.banking.domain.import.ParsedTransaction

data class ImportParsingContext(
    val bytes: ByteArray,
    val fileName: String?,
    val requestedSource: ImportSource,
    val discovery: ImportFormatDiscoveryResult,
)

data class ImportParseOutcome(
    val rows: List<ParsedTransaction>,
    val resolvedSource: ImportSource?,
    val canonicalOutput: CanonicalReaderOutput? = null,
    val assistantRequest: ImportAssistantRequest? = null,
)

data class ImportAssistantRequest(
    val documentType: String,
    val fileType: String,
    val accountType: String,
    val sourceTopic: String,
    val extractedText: String,
    val extractionMetadata: Map<String, Any?>,
)

interface ImportParserStrategy {
    val id: String
    val priority: Int get() = 0

    fun supports(context: ImportParsingContext): Boolean

    fun parse(context: ImportParsingContext): ImportParseOutcome
}

abstract class BaseImportParserStrategy(
    override val id: String,
    override val priority: Int = 0,
) : ImportParserStrategy
