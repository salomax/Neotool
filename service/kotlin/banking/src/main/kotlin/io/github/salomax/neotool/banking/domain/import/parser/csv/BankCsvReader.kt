package io.github.salomax.neotool.banking.domain.import.parser.csv

import io.github.salomax.neotool.banking.domain.import.ParsedTransaction

/**
 * Contract for a bank/layout-specific CSV reader.
 */
interface BankCsvReader {
    fun id(): String

    /**
     * Returns how well this reader supports the provided [fingerprint].
     * Score is normalized in [0.0, 1.0].
     */
    fun supports(fingerprint: CsvFingerprint): CsvReaderMatch

    fun parse(bytes: ByteArray): List<ParsedTransaction>
}
