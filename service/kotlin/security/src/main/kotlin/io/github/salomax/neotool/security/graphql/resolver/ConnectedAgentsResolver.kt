package io.github.salomax.neotool.security.graphql.resolver

import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.github.salomax.neotool.security.graphql.dto.ConnectedAgentDTO
import io.github.salomax.neotool.security.graphql.dto.ConnectedAgentRecentActivityDTO
import io.github.salomax.neotool.security.graphql.dto.RevokeConnectedAgentPayloadDTO
import io.github.salomax.neotool.security.service.connectedagents.ConnectedAgentsService
import jakarta.inject.Singleton

@Singleton
class ConnectedAgentsResolver(
    private val connectedAgentsService: ConnectedAgentsService,
) {
    fun connectedAgents(
        accountId: String,
        principal: RequestPrincipal,
    ): List<ConnectedAgentDTO> {
        val actorUserId = requireNotNull(principal.userId) { "User ID required" }
        return connectedAgentsService.connectedAgents(accountId, actorUserId)
    }

    fun revokeConnectedAgent(
        connectionId: String,
        principal: RequestPrincipal,
    ): RevokeConnectedAgentPayloadDTO {
        return connectedAgentsService.revokeConnectedAgent(connectionId, principal)
    }

    fun recentActivity(
        connection: ConnectedAgentDTO,
        first: Int,
        principal: RequestPrincipal,
    ): List<ConnectedAgentRecentActivityDTO> {
        return connectedAgentsService.recentActivity(connection, first, principal)
    }
}
