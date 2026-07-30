package io.github.salomax.neotool.security.service.connectedagents

import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.NotFoundException
import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.common.service.GraphQLResponse
import io.github.salomax.neotool.common.service.GraphQLServiceClient
import io.github.salomax.neotool.security.error.SecurityErrorCode
import io.github.salomax.neotool.security.graphql.dto.ConnectedAgentActivityOutcome
import io.github.salomax.neotool.security.graphql.dto.ConnectedAgentDTO
import io.github.salomax.neotool.security.graphql.dto.ConnectedAgentRecentActivityDTO
import io.github.salomax.neotool.security.graphql.dto.ConnectedAgentScopeDTO
import io.github.salomax.neotool.security.graphql.dto.RevokeConnectedAgentPayloadDTO
import io.github.salomax.neotool.security.model.account.AccountRole
import io.github.salomax.neotool.security.model.account.MembershipStatus
import io.github.salomax.neotool.security.repo.AccountMembershipRepository
import io.github.salomax.neotool.security.repo.OAuth2ConsentRepository
import io.github.salomax.neotool.security.service.oauth2.OAuth2ClientRegistry
import io.github.salomax.neotool.security.service.oauth2.OAuth2ScopeDescriptionService
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.util.UUID

@Singleton
open class ConnectedAgentsService(
    private val consentRepository: OAuth2ConsentRepository,
    private val accountMembershipRepository: AccountMembershipRepository,
    private val clientRegistry: OAuth2ClientRegistry,
    private val scopeDescriptionService: OAuth2ScopeDescriptionService,
    private val graphQLServiceClient: GraphQLServiceClient,
) {
    fun connectedAgents(
        accountId: String,
        actorUserId: UUID,
        locale: String? = null,
    ): List<ConnectedAgentDTO> {
        val parsedAccountId = parseUuid(accountId, "accountId")
        requireOwner(parsedAccountId, actorUserId)

        return consentRepository
            .findByAccountIdAndRevokedAtIsNullOrderByLastGrantedAtDesc(parsedAccountId)
            .map { consent ->
                val registeredClient = clientRegistry.find(consent.clientId)
                ConnectedAgentDTO(
                    connectionId = requireNotNull(consent.id).toString(),
                    clientId = consent.clientId,
                    clientName = registeredClient?.clientName ?: consent.clientId,
                    connectedAt = consent.firstGrantedAt,
                    lastGrantedAt = consent.lastGrantedAt,
                    scopes =
                        scopeDescriptionService
                            .describe(consent.grantedScopes, locale)
                            .map { ConnectedAgentScopeDTO(name = it.name, description = it.description) },
                    userId = consent.userId,
                    accountId = consent.accountId,
                )
            }
    }

    @Transactional
    open fun revokeConnectedAgent(
        connectionId: String,
        actorUserId: UUID,
    ): RevokeConnectedAgentPayloadDTO {
        val parsedConnectionId = parseUuid(connectionId, "connectionId")
        val consent =
            consentRepository.findOneByIdAndRevokedAtIsNull(parsedConnectionId)
                .orElseThrow {
                    NotFoundException(
                        errorCode = CommonErrorCode.RESOURCE_NOT_FOUND,
                        field = "connectionId",
                    )
                }
        requireOwner(consent.accountId, actorUserId)

        val revocation = revokeInMcp(consent.userId, consent.clientId, consent.accountId)
        consent.revokedAt = revocation.revokedAt
        consent.updatedAt = revocation.revokedAt
        consentRepository.update(consent)

        return RevokeConnectedAgentPayloadDTO(
            connectionId = parsedConnectionId.toString(),
            clientId = consent.clientId,
            accountId = consent.accountId.toString(),
            revokedAt = revocation.revokedAt,
        )
    }

    fun recentActivity(
        connection: ConnectedAgentDTO,
        first: Int,
        actorUserId: UUID,
    ): List<ConnectedAgentRecentActivityDTO> {
        requireOwner(connection.accountId, actorUserId)
        if (first <= 0) {
            throw ValidationException(
                errorCode = CommonErrorCode.INVALID_INPUT,
                field = "first",
                parameters = mapOf("reason" to "must_be_positive"),
            )
        }

        val response =
            runBlocking {
                graphQLServiceClient.query(
                    query = RECENT_ACTIVITY_QUERY,
                    variables =
                        mapOf(
                            "userId" to connection.userId.toString(),
                            "clientId" to connection.clientId,
                            "first" to first,
                        ),
                    targetAudience = "apollo-router",
                )
            }

        if (!response.errors.isNullOrEmpty()) {
            throw IllegalStateException("MCP recentActivity returned GraphQL errors")
        }

        val entries =
            response.data
                ?.get("recentActivity") as? List<*>
                ?: return emptyList()

        return entries.map { raw ->
            val entry = raw as? Map<*, *> ?: throw IllegalStateException("Invalid recentActivity payload")
            ConnectedAgentRecentActivityDTO(
                id = parseUuid(entry["id"]?.toString(), "recentActivity.id"),
                occurredAt = parseInstant(entry["occurredAt"]?.toString(), "recentActivity.occurredAt"),
                toolName = entry["toolName"]?.toString(),
                outcome =
                    ConnectedAgentActivityOutcome.valueOf(
                        entry["outcome"]?.toString()
                            ?: throw IllegalStateException("recentActivity.outcome is required"),
                    ),
                errorCode = entry["errorCode"]?.toString(),
                errorMessage = entry["errorMessage"]?.toString(),
                latencyMs =
                    (entry["latencyMs"] as? Number)?.toLong()
                        ?: throw IllegalStateException("recentActivity.latencyMs is required"),
            )
        }
    }

    private fun revokeInMcp(
        userId: UUID,
        clientId: String,
        accountId: UUID,
    ): RevokeConnectedAgentPayloadDTO {
        val response =
            runBlocking {
                graphQLServiceClient.mutation(
                    mutation = REVOKE_CONNECTION_MUTATION,
                    variables =
                        mapOf(
                            "userId" to userId.toString(),
                            "clientId" to clientId,
                            "accountId" to accountId.toString(),
                        ),
                    targetAudience = "apollo-router",
                )
            }

        if (!response.errors.isNullOrEmpty()) {
            throw IllegalStateException("MCP revokeConnection returned GraphQL errors")
        }

        val payload =
            response.requiredData("revokeConnection")

        return RevokeConnectedAgentPayloadDTO(
            connectionId = "",
            clientId =
                payload["clientId"]?.toString()
                    ?: throw IllegalStateException("revokeConnection.clientId is required"),
            accountId =
                payload["accountId"]?.toString()
                    ?: throw IllegalStateException("revokeConnection.accountId is required"),
            revokedAt = parseInstant(payload["revokedAt"]?.toString(), "revokeConnection.revokedAt"),
        )
    }

    private fun requireOwner(
        accountId: UUID,
        actorUserId: UUID,
    ) {
        val membership =
            accountMembershipRepository.findOneByAccountIdAndUserId(accountId, actorUserId)
                .orElseThrow { ValidationException(SecurityErrorCode.NOT_ACCOUNT_OWNER, field = "accountId") }
        if (membership.membershipStatus != MembershipStatus.ACTIVE || membership.accountRole != AccountRole.OWNER) {
            throw ValidationException(SecurityErrorCode.NOT_ACCOUNT_OWNER, field = "accountId")
        }
    }

    private fun parseUuid(
        raw: String?,
        field: String,
    ): UUID =
        try {
            UUID.fromString(raw)
        } catch (_: Exception) {
            throw ValidationException(
                errorCode = CommonErrorCode.INVALID_INPUT,
                field = field,
                parameters = mapOf("reason" to "invalid_uuid"),
            )
        }

    private fun parseInstant(
        raw: String?,
        field: String,
    ): Instant =
        try {
            Instant.parse(raw)
        } catch (_: Exception) {
            throw IllegalStateException("Invalid instant returned for $field")
        }

    private fun GraphQLResponse.requiredData(field: String): Map<*, *> =
        data?.get(field) as? Map<*, *>
            ?: throw IllegalStateException("GraphQL response missing data.$field")

    companion object {
        private val REVOKE_CONNECTION_MUTATION =
            """
            mutation RevokeConnection(${'$'}userId: ID!, ${'$'}clientId: String!, ${'$'}accountId: ID!) {
              revokeConnection(userId: ${'$'}userId, clientId: ${'$'}clientId, accountId: ${'$'}accountId) {
                clientId
                accountId
                revokedAt
              }
            }
            """.trimIndent()

        private val RECENT_ACTIVITY_QUERY =
            """
            query RecentActivity(${'$'}userId: ID!, ${'$'}clientId: String!, ${'$'}first: Int!) {
              recentActivity(userId: ${'$'}userId, clientId: ${'$'}clientId, first: ${'$'}first) {
                id
                occurredAt
                toolName
                outcome
                errorCode
                errorMessage
                latencyMs
              }
            }
            """.trimIndent()
    }
}
