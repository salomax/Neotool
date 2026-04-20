package io.github.salomax.neotool.banking.model.dto.imports

import io.github.salomax.neotool.banking.domain.import.ParsedTransaction
import io.github.salomax.neotool.banking.domain.transaction.Transaction

/**
 * Result of processing a batch of parsed rows for an import job.
 */
data class ImportProcessingResult(
    val imported: List<Transaction>,
    val skipped: Int,
    val duplicateCandidates: List<ImportDuplicateCandidate>,
    val errors: List<ImportRowError>,
)

/**
 * A row that may be a duplicate of existing transaction(s) from another source.
 */
data class ImportDuplicateCandidate(
    val rowIndex: Int,
    val parsed: ParsedTransaction,
    val candidates: List<Transaction>,
)

/**
 * Parse/validation failure for a single row.
 */
data class ImportRowError(
    val rowIndex: Int,
    val reason: String,
)
