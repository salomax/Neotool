package io.github.salomax.neotool.banking.service.import.parser.csv

import io.github.salomax.neotool.banking.domain.import.*
/**
 * Selection thresholds for deciding how to route CSV parsing.
 */
class CsvReaderSelectionPolicy(
    val specificThreshold: Double = 0.85,
    val trySpecificThenFallbackThreshold: Double = 0.60,
) {
    init {
        require(specificThreshold in 0.0..1.0) { "specificThreshold must be between 0.0 and 1.0" }
        require(trySpecificThenFallbackThreshold in 0.0..1.0) {
            "trySpecificThenFallbackThreshold must be between 0.0 and 1.0"
        }
        require(specificThreshold >= trySpecificThenFallbackThreshold) {
            "specificThreshold must be greater than or equal to trySpecificThenFallbackThreshold"
        }
    }

    fun modeFor(score: Double): CsvReaderSelectionMode =
        when {
            score >= specificThreshold -> CsvReaderSelectionMode.SPECIFIC_ONLY
            score >= trySpecificThenFallbackThreshold -> CsvReaderSelectionMode.SPECIFIC_THEN_GENERIC_FALLBACK
            else -> CsvReaderSelectionMode.GENERIC_ONLY
        }
}

enum class CsvReaderSelectionMode {
    SPECIFIC_ONLY,
    SPECIFIC_THEN_GENERIC_FALLBACK,
    GENERIC_ONLY,
}
