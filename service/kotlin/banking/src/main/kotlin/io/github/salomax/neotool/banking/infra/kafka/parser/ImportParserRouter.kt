package io.github.salomax.neotool.banking.infra.kafka.parser

import io.github.salomax.neotool.banking.domain.import.ImportSource
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.common.error.BusinessRuleException

class ImportParserRouter(
    strategies: List<ImportParserStrategy>,
    private val discoveryService: ImportFormatDiscoveryService = ImportFormatDiscoveryService(),
) {
    private val orderedStrategies = strategies.sortedByDescending { it.priority }

    fun parse(
        bytes: ByteArray,
        fileName: String?,
        requestedSource: ImportSource?,
    ): ImportParseOutcome {
        val source = requestedSource ?: ImportSource.AUTO
        val discovery = discoveryService.discover(bytes, fileName)
        val context = ImportParsingContext(
            bytes = bytes,
            fileName = fileName,
            requestedSource = source,
            discovery = discovery,
        )
        val strategy = orderedStrategies.firstOrNull { it.supports(context) }
            ?: throw BusinessRuleException(
                errorCode = BankingErrorCode.IMPORT_FILE_FORMAT_NOT_SUPPORTED,
                parameters = mapOf(
                    "fileName" to (fileName ?: ""),
                    "requestedSource" to source.name,
                    "detectedFormat" to discovery.format.name,
                    "detectedSource" to (discovery.detectedSource?.name ?: ""),
                    "reason" to discovery.reason,
                ),
            )
        return strategy.parse(context)
    }
}
