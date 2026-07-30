package io.github.salomax.neotool.security.graphql.dto

import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable
import java.time.Instant
import java.util.UUID

@Introspected
@Serdeable
data class ConnectedAgentScopeDTO(
    val name: String,
    val description: String,
)

enum class ConnectedAgentActivityOutcome {
    SUCCESS,
    ERROR,
    REJECTED,
}

@Introspected
@Serdeable
data class ConnectedAgentRecentActivityDTO(
    val id: UUID,
    val occurredAt: Instant,
    val toolName: String?,
    val outcome: ConnectedAgentActivityOutcome,
    val errorCode: String?,
    val errorMessage: String?,
    val latencyMs: Long,
)

@Introspected
@Serdeable
data class ConnectedAgentDTO(
    val connectionId: String,
    val clientId: String,
    val clientName: String,
    val connectedAt: Instant,
    val lastGrantedAt: Instant,
    val scopes: List<ConnectedAgentScopeDTO>,
    val userId: UUID,
    val accountId: UUID,
)

@Introspected
@Serdeable
data class RevokeConnectedAgentPayloadDTO(
    val connectionId: String,
    val clientId: String,
    val accountId: String,
    val revokedAt: Instant,
)
