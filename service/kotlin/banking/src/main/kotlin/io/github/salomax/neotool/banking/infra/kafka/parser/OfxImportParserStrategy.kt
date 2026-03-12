package io.github.salomax.neotool.banking.infra.kafka.parser

import io.github.salomax.neotool.banking.domain.import.CanonicalReaderOutputMapper
import io.github.salomax.neotool.banking.domain.import.ImportSource
import io.github.salomax.neotool.banking.domain.import.parser.OFXParser

class OfxImportParserStrategy : BaseImportParserStrategy(
    id = "ofx-parser",
    priority = 100,
) {
    override fun supports(context: ImportParsingContext): Boolean {
        if (context.requestedSource == ImportSource.OFX) return true
        return context.requestedSource == ImportSource.AUTO &&
            context.discovery.format == ImportFileFormat.OFX
    }

    override fun parse(context: ImportParsingContext): ImportParseOutcome {
        val parseResult = OFXParser.parseWithIntent(context.bytes)
        val rows = parseResult.rows
        return ImportParseOutcome(
            rows = rows,
            resolvedSource = ImportSource.OFX,
            canonicalOutput =
                CanonicalReaderOutputMapper.fromOfx(
                    rows = rows,
                    rawMetadata =
                        mapOf(
                            "strategy_id" to id,
                            "detected_format" to context.discovery.format.name,
                            "detected_source" to context.discovery.detectedSource?.name,
                            "discovery_reason" to context.discovery.reason,
                            "ofx_message_set_intent" to parseResult.statementIntent.name,
                            "ofx_document_format" to parseResult.format.name,
                        ),
                ),
        )
    }
}
