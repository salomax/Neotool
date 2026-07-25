package io.github.salomax.neotool.mcp.audit

import jakarta.inject.Singleton
import java.time.Instant
import java.util.UUID

data class ToolCallAuditRecord(
    val id: UUID = UUID.randomUUID(),
    val occurredAt: Instant = Instant.now(),
    val userId: UUID? = null,
    val clientId: String? = null,
    val tokenId: String? = null,
    val toolName: String? = null,
    val arguments: Map<String, Any?> = emptyMap(),
    val sensitiveInputFields: List<String> = emptyList(),
    val redactAllArguments: Boolean = false,
    val outcome: ToolCallAuditOutcome,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val latencyMs: Long,
)

@Singleton
open class ToolCallAuditService(
    private val repository: ToolCallAuditLogRepository,
    private val redactor: ToolInputRedactor,
) {
    open fun record(record: ToolCallAuditRecord): ToolCallAuditLogEntity {
        val occurredAt = record.occurredAt
        val redactedArguments =
            if (record.redactAllArguments) {
                redactor.redactAll(record.arguments)
            } else {
                redactor.redact(record.arguments, record.sensitiveInputFields)
            }
        return repository.save(
            ToolCallAuditLogEntity(
                id = record.id,
                occurredAt = occurredAt,
                userId = record.userId,
                clientId = record.clientId?.trim()?.takeIf { it.isNotEmpty() },
                tokenId = record.tokenId?.trim()?.takeIf { it.isNotEmpty() },
                toolName = record.toolName?.trim()?.takeIf { it.isNotEmpty() },
                arguments = redactedArguments,
                outcome = record.outcome,
                errorCode = record.errorCode?.trim()?.takeIf { it.isNotEmpty() },
                errorMessage = record.errorMessage?.trim()?.takeIf { it.isNotEmpty() },
                latencyMs = record.latencyMs.coerceAtLeast(0),
                createdAt = occurredAt,
            ),
        )
    }
}
