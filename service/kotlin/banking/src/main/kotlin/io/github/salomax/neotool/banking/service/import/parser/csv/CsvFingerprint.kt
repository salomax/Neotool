package io.github.salomax.neotool.banking.service.import.parser.csv

import io.github.salomax.neotool.banking.domain.import.*
/**
 * Structural signature extracted from a CSV sample.
 * Used for reader matching and confidence scoring.
 */
data class CsvFingerprint(
    val delimiter: Char,
    val encoding: String,
    val hasHeader: Boolean,
    val normalizedColumns: List<String>,
    val datePatterns: Set<CsvDatePattern>,
    val amountPatterns: Set<CsvAmountPattern>,
    val descriptionTokens: Set<String>,
)

enum class CsvDatePattern {
    ISO_YYYY_MM_DD,
    BR_DD_MM_YYYY_SLASH,
    BR_DD_MM_YYYY_DASH,
    BR_DD_MM_YY_SLASH,
}

enum class CsvAmountPattern {
    DOT_DECIMAL,
    COMMA_DECIMAL,
    THOUSANDS_DOT_DECIMAL_COMMA,
    THOUSANDS_COMMA_DECIMAL_DOT,
    INTEGER,
}
