package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.common.service.GraphQLResponse
import io.github.salomax.neotool.common.service.GraphQLServiceClient
import io.github.salomax.neotool.security.error.SecurityErrorCode
import io.github.salomax.neotool.security.model.OAuth2ConsentEntity
import io.github.salomax.neotool.security.model.account.AccountMembershipEntity
import io.github.salomax.neotool.security.model.account.AccountRole
import io.github.salomax.neotool.security.model.account.MembershipStatus
import io.github.salomax.neotool.security.repo.AccountMembershipRepository
import io.github.salomax.neotool.security.repo.OAuth2ConsentRepository
import io.github.salomax.neotool.security.service.connectedagents.ConnectedAgentsService
import io.github.salomax.neotool.security.service.oauth2.OAuth2ClientRegistry
import io.github.salomax.neotool.security.service.oauth2.OAuth2RegisteredClient
import io.github.salomax.neotool.security.service.oauth2.OAuth2ScopeDescription
import io.github.salomax.neotool.security.service.oauth2.OAuth2ScopeDescriptionService
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.Optional
import java.util.UUID

class ConnectedAgentsServiceTest {
    private lateinit var consentRepository: OAuth2ConsentRepository
    private lateinit var accountMembershipRepository: AccountMembershipRepository
    private lateinit var clientRegistry: OAuth2ClientRegistry
    private lateinit var scopeDescriptionService: OAuth2ScopeDescriptionService
    private lateinit var graphQLServiceClient: GraphQLServiceClient
    private lateinit var service: ConnectedAgentsService

    @BeforeEach
    fun setUp() {
        consentRepository = mock()
        accountMembershipRepository = mock()
        clientRegistry = mock()
        scopeDescriptionService = mock()
        graphQLServiceClient = mock()
        service =
            ConnectedAgentsService(
                consentRepository,
                accountMembershipRepository,
                clientRegistry,
                scopeDescriptionService,
                graphQLServiceClient,
            )
    }

    @Test
    fun `connectedAgents returns localized scopes for owner only`() {
        val actorUserId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val consent =
            OAuth2ConsentEntity(
                id = UUID.randomUUID(),
                userId = UUID.randomUUID(),
                clientId = "agent-a",
                accountId = accountId,
                grantedScopes = "finance:transactions:read",
                firstGrantedAt = Instant.parse("2026-07-30T12:00:00Z"),
                lastGrantedAt = Instant.parse("2026-07-30T13:00:00Z"),
            )
        stubOwner(actorUserId, accountId)
        whenever(consentRepository.findByAccountIdAndRevokedAtIsNullOrderByLastGrantedAtDesc(accountId))
            .thenReturn(listOf(consent))
        whenever(clientRegistry.find("agent-a"))
            .thenReturn(
                OAuth2RegisteredClient(
                    clientId = "agent-a",
                    clientName = "Agent A",
                    redirectUris = emptySet(),
                    scopes = emptySet(),
                    resources = emptySet(),
                ),
            )
        whenever(scopeDescriptionService.describe("finance:transactions:read", null))
            .thenReturn(listOf(OAuth2ScopeDescription("finance:transactions:read", "Read transactions")))

        val result = service.connectedAgents(accountId.toString(), actorUserId)
        val agent = result.single()

        assertThat(agent.clientId).isEqualTo("agent-a")
        assertThat(agent.clientName).isEqualTo("Agent A")
        assertThat(agent.scopes.single().description).isEqualTo("Read transactions")
    }

    @Test
    fun `connectedAgents rejects non owner and missing membership`() {
        val actorUserId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        whenever(accountMembershipRepository.findOneByAccountIdAndUserId(accountId, actorUserId))
            .thenReturn(
                Optional.of(
                    AccountMembershipEntity(
                        accountId = accountId,
                        userId = actorUserId,
                        accountRole = AccountRole.MEMBER,
                        membershipStatus = MembershipStatus.ACTIVE,
                    ),
                ),
            )

        val nonOwner =
            assertThrows<ValidationException> {
                service.connectedAgents(accountId.toString(), actorUserId)
            }
        assertThat(nonOwner.errorCode).isEqualTo(SecurityErrorCode.NOT_ACCOUNT_OWNER)

        whenever(accountMembershipRepository.findOneByAccountIdAndUserId(accountId, actorUserId))
            .thenReturn(Optional.empty())

        val missing =
            assertThrows<ValidationException> {
                service.connectedAgents(accountId.toString(), actorUserId)
            }
        assertThat(missing.errorCode).isEqualTo(SecurityErrorCode.NOT_ACCOUNT_OWNER)
    }

    @Test
    fun `revokeConnectedAgent derives revocation triple from persisted consent row`() =
        runBlocking {
            val actorUserId = UUID.randomUUID()
            val accountId = UUID.randomUUID()
            val consentUserId = UUID.randomUUID()
            val connectionId = UUID.randomUUID()
            val consent =
                OAuth2ConsentEntity(
                    id = connectionId,
                    userId = consentUserId,
                    clientId = "agent-a",
                    accountId = accountId,
                    grantedScopes = "finance:transactions:read",
                )
            stubOwner(actorUserId, accountId)
            whenever(consentRepository.findOneByIdAndRevokedAtIsNull(connectionId)).thenReturn(Optional.of(consent))
            whenever(
                graphQLServiceClient.mutation(any(), any(), any(), anyOrNull()),
            ).thenReturn(
                GraphQLResponse(
                    data =
                        mapOf(
                            "revokeConnection" to
                                mapOf(
                                    "clientId" to "agent-a",
                                    "accountId" to accountId.toString(),
                                    "revokedAt" to "2026-07-30T15:00:00Z",
                                ),
                        ),
                ),
            )
            whenever(consentRepository.update(any())).thenAnswer { it.arguments[0] }

            val payload = service.revokeConnectedAgent(connectionId.toString(), actorUserId)

            val variablesCaptor = argumentCaptor<Map<String, Any>>()
            verify(graphQLServiceClient).mutation(any(), variablesCaptor.capture(), eq("apollo-router"), anyOrNull())
            assertThat(variablesCaptor.firstValue).isEqualTo(
                mapOf(
                    "userId" to consentUserId.toString(),
                    "clientId" to "agent-a",
                    "accountId" to accountId.toString(),
                ),
            )
            assertThat(payload.connectionId).isEqualTo(connectionId.toString())
            assertThat(payload.clientId).isEqualTo("agent-a")
            assertThat(payload.revokedAt).isEqualTo(Instant.parse("2026-07-30T15:00:00Z"))
            assertThat(consent.revokedAt).isEqualTo(Instant.parse("2026-07-30T15:00:00Z"))
        }

    private fun stubOwner(
        actorUserId: UUID,
        accountId: UUID,
    ) {
        whenever(accountMembershipRepository.findOneByAccountIdAndUserId(accountId, actorUserId))
            .thenReturn(
                Optional.of(
                    AccountMembershipEntity(
                        accountId = accountId,
                        userId = actorUserId,
                        accountRole = AccountRole.OWNER,
                        membershipStatus = MembershipStatus.ACTIVE,
                    ),
                ),
            )
    }
}
