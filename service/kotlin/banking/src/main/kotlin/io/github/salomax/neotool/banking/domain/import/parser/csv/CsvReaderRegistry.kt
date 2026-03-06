package io.github.salomax.neotool.banking.domain.import.parser.csv

/**
 * Scores registered readers and selects the best strategy for parsing.
 */
class CsvReaderRegistry(
    readers: List<BankCsvReader>,
    private val policy: CsvReaderSelectionPolicy = CsvReaderSelectionPolicy(),
) {
    private val readersById: Map<String, BankCsvReader> =
        readers.associateBy { it.id() }.also { mapped ->
            require(mapped.size == readers.size) { "Reader ids must be unique" }
        }

    fun rank(fingerprint: CsvFingerprint): List<CsvReaderMatch> =
        readersById.values
            .map { reader ->
                val raw = reader.supports(fingerprint)
                if (raw.readerId == reader.id()) raw else raw.copy(readerId = reader.id())
            }
            .sortedWith(compareByDescending<CsvReaderMatch> { it.score }.thenBy { it.readerId })

    fun select(fingerprint: CsvFingerprint): CsvReaderSelectionResult {
        val ranked = rank(fingerprint)
        val best =
            ranked.firstOrNull()
                ?: return CsvReaderSelectionResult(
                    mode = CsvReaderSelectionMode.GENERIC_ONLY,
                    selectedReader = null,
                    selectedMatch = null,
                    rankedMatches = emptyList(),
                )

        val mode = policy.modeFor(best.score)
        val selectedReader = if (mode == CsvReaderSelectionMode.GENERIC_ONLY) null else readersById[best.readerId]
        val selectedMatch = if (mode == CsvReaderSelectionMode.GENERIC_ONLY) null else best

        return CsvReaderSelectionResult(
            mode = mode,
            selectedReader = selectedReader,
            selectedMatch = selectedMatch,
            rankedMatches = ranked,
        )
    }
}

data class CsvReaderSelectionResult(
    val mode: CsvReaderSelectionMode,
    val selectedReader: BankCsvReader?,
    val selectedMatch: CsvReaderMatch?,
    val rankedMatches: List<CsvReaderMatch>,
)
