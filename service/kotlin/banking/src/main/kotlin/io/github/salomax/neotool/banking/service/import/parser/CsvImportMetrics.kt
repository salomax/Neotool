package io.github.salomax.neotool.banking.service.import.parser

import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.core.annotation.Nullable
import jakarta.inject.Singleton

/**
 * CSV import telemetry counters for reader selection and fallback behavior.
 */
@Singleton
class CsvImportMetrics(
    @param:Nullable
    private val meterRegistry: MeterRegistry?,
) {
    fun recordReaderMatch(
        readerId: String,
        source: String,
        mode: String,
    ) {
        meterRegistry?.counter(
            "csv_reader_match_total",
            "readerId",
            sanitizeTag(readerId),
            "source",
            sanitizeTag(source),
            "mode",
            sanitizeTag(mode),
        )?.increment()
    }

    fun recordFallback(
        source: String,
        reason: String,
        selectedReaderId: String?,
    ) {
        meterRegistry?.counter(
            "csv_reader_fallback_total",
            "source",
            sanitizeTag(source),
            "reason",
            sanitizeTag(reason),
            "selectedReaderId",
            sanitizeTag(selectedReaderId ?: "none"),
        )?.increment()
    }

    fun recordParseFailure(
        readerId: String,
        reason: String,
        source: String,
    ) {
        meterRegistry?.counter(
            "csv_parse_failure_total",
            "readerId",
            sanitizeTag(readerId),
            "reason",
            sanitizeTag(reason),
            "source",
            sanitizeTag(source),
        )?.increment()
    }

    private fun sanitizeTag(value: String): String =
        value.trim().ifBlank { "unknown" }.replace("[^a-zA-Z0-9._-]".toRegex(), "_").take(100)
}
