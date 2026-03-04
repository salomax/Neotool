package io.github.salomax.neotool.banking.graphql.dto

import io.github.salomax.neotool.banking.domain.import.ImportJobStatus
import io.github.salomax.neotool.banking.domain.import.ImportSource
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Summary of one parsed row from an import file (incoming side of a duplicate candidate).
 */
data class ParsedImportRowDTO(
    val creditDebitType: CreditDebitType,
    val amount: Double,
    val date: LocalDate,
    val description: String,
    val currencyCode: String,
    val counterpartyName: String?,
    val counterpartyDocument: String?,
)

/**
 * A row that may be a duplicate of existing transaction(s); used when status is REVIEW_REQUIRED.
 */
data class ImportDuplicateCandidateDTO(
    val rowIndex: Int,
    val parsed: ParsedImportRowDTO,
    val candidates: List<TransactionDTO>,
)

/**
 * Transaction import job (file or NFC-e) for GraphQL.
 */
data class TransactionImportDTO(
    val id: UUID,
    val financialAccountId: UUID,
    val source: ImportSource,
    val fileName: String?,
    val status: ImportJobStatus,
    val totalRows: Int?,
    val importedCount: Int?,
    val skippedCount: Int?,
    val duplicateReviewCount: Int?,
    val errorCount: Int?,
    val errorDetails: String?,
    val duplicateCandidates: List<ImportDuplicateCandidateDTO>,
    val dateFrom: LocalDate?,
    val dateTo: LocalDate?,
    val completedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * Result of starting a transaction import (file or NFC-e).
 */
data class ImportResultDTO(
    val import: TransactionImportDTO,
)
