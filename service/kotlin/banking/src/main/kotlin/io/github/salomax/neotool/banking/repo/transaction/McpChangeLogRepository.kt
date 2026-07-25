package io.github.salomax.neotool.banking.repo.transaction

import io.github.salomax.neotool.banking.domain.transaction.McpChangeLogEntry
import java.util.UUID

interface McpChangeLogRepository {
    fun saveAll(entries: List<McpChangeLogEntry>)

    fun listForAccount(
        accountId: UUID,
        clientId: String?,
        tokenId: String?,
        toolCallId: UUID?,
        limit: Int,
        offset: Int,
    ): List<McpChangeLogEntry>

    fun findOriginalUpdatesForAccount(
        accountId: UUID,
        toolCallId: UUID,
    ): List<McpChangeLogEntry>
}
