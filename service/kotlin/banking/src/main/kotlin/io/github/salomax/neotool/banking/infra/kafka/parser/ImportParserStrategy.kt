package io.github.salomax.neotool.banking.infra.kafka.parser

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
