package io.github.salomax.neotool.banking.repo.transaction

import io.github.salomax.neotool.banking.domain.transaction.McpChangeLogEntry
import io.github.salomax.neotool.banking.model.transaction.McpChangeLogEntity
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import java.util.UUID

@Singleton
class DefaultMcpChangeLogRepository(
    private val entityRepository: McpChangeLogEntityRepository,
    @PersistenceContext private val entityManager: EntityManager,
) : McpChangeLogRepository {
    override fun saveAll(entries: List<McpChangeLogEntry>) {
        entries.forEach { entry ->
            entityRepository.save(entry.toEntity())
        }
        if (entries.isNotEmpty()) {
            entityManager.flush()
        }
    }

    override fun listForAccount(
        accountId: UUID,
        clientId: String?,
        tokenId: String?,
        toolCallId: UUID?,
        limit: Int,
        offset: Int,
    ): List<McpChangeLogEntry> {
        val query =
            entityManager.createNativeQuery(
                """
                SELECT l.*
                FROM banking.mcp_change_log l
                LEFT JOIN banking.transactions t
                  ON l.resource_type = 'TRANSACTION'
                 AND t.id = l.resource_id
                LEFT JOIN banking.transaction_rules tr
                  ON l.resource_type <> 'TRANSACTION'
                 AND tr.id = l.resource_id
                WHERE (
                    (l.resource_type = 'TRANSACTION' AND t.account_id = CAST(:accountId AS uuid))
                    OR (l.resource_type <> 'TRANSACTION' AND tr.account_id = CAST(:accountId AS uuid))
                )
                  AND (CAST(:clientId AS text) IS NULL OR l.client_id = :clientId)
                  AND (CAST(:tokenId AS text) IS NULL OR l.token_id = :tokenId)
                  AND (CAST(:toolCallId AS uuid) IS NULL OR l.tool_call_id = CAST(:toolCallId AS uuid))
                ORDER BY l.created_at DESC, l.id DESC
                LIMIT :limit OFFSET :offset
                """.trimIndent(),
                McpChangeLogEntity::class.java,
            )
                .setParameter("accountId", accountId.toString())
                .setParameter("clientId", clientId)
                .setParameter("tokenId", tokenId)
                .setParameter("toolCallId", toolCallId?.toString())
                .setParameter("limit", limit)
                .setParameter("offset", offset)

        @Suppress("UNCHECKED_CAST")
        val rows = query.resultList as List<McpChangeLogEntity>
        return rows.map { it.toDomain() }
    }

    override fun findOriginalUpdatesForAccount(
        accountId: UUID,
        toolCallId: UUID,
    ): List<McpChangeLogEntry> {
        val query =
            entityManager.createNativeQuery(
                """
                SELECT l.*
                FROM banking.mcp_change_log l
                JOIN banking.transactions t
                  ON l.resource_type = 'TRANSACTION'
                 AND t.id = l.resource_id
                WHERE t.account_id = CAST(:accountId AS uuid)
                  AND l.tool_call_id = CAST(:toolCallId AS uuid)
                  AND l.action = 'UPDATE'
                ORDER BY l.created_at ASC, l.id ASC
                """.trimIndent(),
                McpChangeLogEntity::class.java,
            )
                .setParameter("accountId", accountId.toString())
                .setParameter("toolCallId", toolCallId.toString())

        @Suppress("UNCHECKED_CAST")
        val rows = query.resultList as List<McpChangeLogEntity>
        return rows.map { it.toDomain() }
    }

    private fun McpChangeLogEntry.toEntity(): McpChangeLogEntity =
        McpChangeLogEntity(
            id = id,
            toolCallId = toolCallId,
            clientId = clientId,
            tokenId = tokenId,
            resourceType = resourceType,
            resourceId = resourceId,
            action = action,
            before = before,
            after = after,
            createdAt = createdAt,
        )

    private fun McpChangeLogEntity.toDomain(): McpChangeLogEntry =
        McpChangeLogEntry(
            id = id,
            toolCallId = toolCallId,
            clientId = clientId,
            tokenId = tokenId,
            resourceType = resourceType,
            resourceId = resourceId,
            action = action,
            before = before,
            after = after,
            createdAt = createdAt,
        )
}
