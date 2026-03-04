package io.github.salomax.neotool.banking.infra.kafka.parser

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

    override fun parse(context: ImportParsingContext): ImportParseOutcome =
        ImportParseOutcome(
            rows = OFXParser.parse(context.bytes),
            resolvedSource = ImportSource.OFX,
        )
}
