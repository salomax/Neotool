package io.github.salomax.neotool.banking.infra.kafka

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("CsvImportMetrics")
class CsvImportMetricsTest {

    @Test
    fun `increments reader match counter with expected tags`() {
        val registry = SimpleMeterRegistry()
        val metrics = CsvImportMetrics(registry)

        metrics.recordReaderMatch(
            readerId = "csv-nubank",
            source = "CSV_AUTO",
            mode = "SPECIFIC_ONLY",
        )

        val counter = registry.find("csv_reader_match_total")
            .tags("readerId", "csv-nubank", "source", "CSV_AUTO", "mode", "SPECIFIC_ONLY")
            .counter()

        assertThat(counter).isNotNull
        assertThat(counter!!.count()).isEqualTo(1.0)
    }

    @Test
    fun `increments fallback and parse failure counters`() {
        val registry = SimpleMeterRegistry()
        val metrics = CsvImportMetrics(registry)

        metrics.recordFallback(
            source = "CSV_AUTO",
            reason = "specific_then_generic",
            selectedReaderId = "csv-itau",
        )
        metrics.recordParseFailure(
            readerId = "csv-itau",
            reason = "empty_result",
            source = "CSV_AUTO",
        )

        val fallbackCounter = registry.find("csv_reader_fallback_total")
            .tags("source", "CSV_AUTO", "reason", "specific_then_generic", "selectedReaderId", "csv-itau")
            .counter()
        val failureCounter = registry.find("csv_parse_failure_total")
            .tags("readerId", "csv-itau", "reason", "empty_result", "source", "CSV_AUTO")
            .counter()

        assertThat(fallbackCounter).isNotNull
        assertThat(fallbackCounter!!.count()).isEqualTo(1.0)
        assertThat(failureCounter).isNotNull
        assertThat(failureCounter!!.count()).isEqualTo(1.0)
    }

    @Test
    fun `does not throw when meter registry is absent`() {
        val metrics = CsvImportMetrics(null)

        metrics.recordReaderMatch("csv-nubank", "CSV_AUTO", "SPECIFIC_ONLY")
        metrics.recordFallback("CSV_AUTO", "generic_only_mode", null)
        metrics.recordParseFailure("csv-generic", "exception", "CSV_AUTO")
    }
}
