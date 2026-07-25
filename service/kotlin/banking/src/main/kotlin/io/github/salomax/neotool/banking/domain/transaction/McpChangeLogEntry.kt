package io.github.salomax.neotool.banking.domain.transaction

import java.time.Instant
import java.util.UUID

data class McpChangeLogEntry(
    val id: UUID = UUID.randomUUID(),
    val toolCallId: UUID,
    val clientId: String? = null,
    val tokenId: String? = null,
    val resourceType: McpChangeLogResourceType,
    val resourceId: UUID,
    val action: McpChangeLogAction,
    val before: Map<String, Any?>,
    val after: Map<String, Any?>,
    val createdAt: Instant,
)
