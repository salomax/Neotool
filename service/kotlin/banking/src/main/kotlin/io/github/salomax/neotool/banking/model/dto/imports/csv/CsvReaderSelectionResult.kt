package io.github.salomax.neotool.banking.model.dto.imports.csv

import io.github.salomax.neotool.banking.service.import.parser.csv.BankCsvReader
import io.github.salomax.neotool.banking.service.import.parser.csv.CsvReaderMatch
import io.github.salomax.neotool.banking.service.import.parser.csv.CsvReaderSelectionMode

data class CsvReaderSelectionResult(
    val mode: CsvReaderSelectionMode,
    val selectedReader: BankCsvReader?,
    val selectedMatch: CsvReaderMatch?,
    val rankedMatches: List<CsvReaderMatch>,
)
