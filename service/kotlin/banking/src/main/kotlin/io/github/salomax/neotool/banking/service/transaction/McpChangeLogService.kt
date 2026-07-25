package io.github.salomax.neotool.banking.service.transaction

import io.github.salomax.neotool.banking.domain.transaction.McpChangeLogAction
import io.github.salomax.neotool.banking.domain.transaction.McpChangeLogEntry
import io.github.salomax.neotool.banking.domain.transaction.McpChangeLogResourceType
import io.github.salomax.neotool.banking.domain.transaction.TransactionChangeSnapshot
import io.github.salomax.neotool.banking.model.dto.transaction.BulkCategorizeResult
import io.github.salomax.neotool.banking.repo.transaction.McpChangeLogRepository
import io.github.salomax.neotool.banking.repo.transaction.MonthlyBalanceSnapshotRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ValidationException
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

@Singleton
open class McpChangeLogService(
    private val repository: McpChangeLogRepository,
    private val transactionRepository: TransactionRepository,
    private val snapshotRepository: MonthlyBalanceSnapshotRepository,
) {
    @Transactional(readOnly = true)
    open fun listForAccount(
        accountId: UUID,
        clientId: String?,
        tokenId: String?,
        toolCallId: UUID?,
        limit: Int,
        offset: Int,
    ): List<McpChangeLogEntry> {
        require(clientId != null || tokenId != null || toolCallId != null) {
            throw ValidationException(
                errorCode = CommonErrorCode.INVALID_INPUT,
                field = "filters",
                parameters = mapOf("reason" to "At least one of clientId, tokenId, or toolCallId is required"),
            )
        }
        require(limit > 0) {
            throw ValidationException(
                errorCode = CommonErrorCode.INVALID_INPUT,
                field = "limit",
                parameters = mapOf("reason" to "limit must be greater than zero"),
            )
        }
        require(offset >= 0) {
            throw ValidationException(
                errorCode = CommonErrorCode.INVALID_INPUT,
                field = "offset",
                parameters = mapOf("reason" to "offset must be zero or greater"),
            )
        }
        return repository.listForAccount(accountId, clientId, tokenId, toolCallId, limit, offset)
    }

    @Transactional
    open fun revertToolCallChanges(
        accountId: UUID,
        toolCallId: UUID,
    ): BulkCategorizeResult {
        val originalEntries = repository.findOriginalUpdatesForAccount(accountId, toolCallId)
        if (originalEntries.isEmpty()) {
            return BulkCategorizeResult(affectedCount = 0, errors = emptyList())
        }

        val currentTransactions =
            transactionRepository.findByIds(originalEntries.map { it.resourceId }, accountId)
                .associateBy { requireNotNull(it.id) }
        val revertedAt = Instant.now()
        val revertEntries = mutableListOf<McpChangeLogEntry>()
        val errors = mutableListOf<String>()
        var affectedCount = 0
        val earliestAffectedMonthByAccount = mutableMapOf<UUID, YearMonth>()

        originalEntries.forEach { entry ->
            val current = currentTransactions[entry.resourceId]
            if (current == null) {
                errors += "Transaction ${entry.resourceId} not found for revert"
                return@forEach
            }
            if (current.updatedAt.isAfter(entry.createdAt)) {
                errors += "Transaction ${entry.resourceId} was edited after tool call $toolCallId"
                return@forEach
            }

            val beforeSnapshot = TransactionChangeSnapshot.fromMap(entry.before)
            val reverted = beforeSnapshot.applyTo(current).copy(updatedAt = revertedAt)
            val saved = transactionRepository.update(reverted)

            revertEntries +=
                McpChangeLogEntry(
                    toolCallId = entry.toolCallId,
                    clientId = entry.clientId,
                    tokenId = entry.tokenId,
                    resourceType = McpChangeLogResourceType.TRANSACTION,
                    resourceId = entry.resourceId,
                    action = McpChangeLogAction.REVERT,
                    before = TransactionChangeSnapshot.from(current).toMap(),
                    after = TransactionChangeSnapshot.from(saved).toMap(),
                    createdAt = revertedAt,
                )
            affectedCount += 1
            rememberEarliestAffectedMonth(earliestAffectedMonthByAccount, saved.financialAccountId, saved.date)
        }

        if (revertEntries.isNotEmpty()) {
            repository.saveAll(revertEntries)
        }
        earliestAffectedMonthByAccount.forEach { (financialAccountId, earliestMonth) ->
            snapshotRepository.markDirtyFromMonth(financialAccountId, earliestMonth.year, earliestMonth.monthValue)
        }

        return BulkCategorizeResult(affectedCount = affectedCount, errors = errors)
    }

    private fun rememberEarliestAffectedMonth(
        earliestAffectedMonthByAccount: MutableMap<UUID, YearMonth>,
        financialAccountId: UUID,
        transactionDate: java.time.LocalDate,
    ) {
        val affectedMonth = YearMonth.from(transactionDate)
        val currentEarliest = earliestAffectedMonthByAccount[financialAccountId]
        if (currentEarliest == null || affectedMonth.isBefore(currentEarliest)) {
            earliestAffectedMonthByAccount[financialAccountId] = affectedMonth
        }
    }
}
