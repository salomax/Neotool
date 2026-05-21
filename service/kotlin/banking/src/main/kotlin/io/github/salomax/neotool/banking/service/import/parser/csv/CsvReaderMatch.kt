package io.github.salomax.neotool.banking.service.import.parser.csv

/**
 * Support score produced by a [BankCsvReader] for a given [CsvFingerprint].
 */
data class CsvReaderMatch(
    val readerId: String,
    val score: Double,
    val reasons: List<String> = emptyList(),
) {
    init {
        require(readerId.isNotBlank()) { "readerId must not be blank" }
        require(score in 0.0..1.0) { "score must be between 0.0 and 1.0" }
    }
}
