package io.github.salomax.neotool.security.test.integration

import io.github.salomax.neotool.common.service.GraphQLResponse
import io.github.salomax.neotool.common.service.GraphQLServiceClient
import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.security.model.OAuth2ConsentEntity
import io.github.salomax.neotool.security.model.UserEntity
import io.github.salomax.neotool.security.model.account.AccountEntity
import io.github.salomax.neotool.security.model.account.AccountMembershipEntity
import io.github.salomax.neotool.security.model.account.AccountRole
import io.github.salomax.neotool.security.model.account.AccountStatus
import io.github.salomax.neotool.security.model.account.AccountType
import io.github.salomax.neotool.security.model.account.MembershipStatus
import io.github.salomax.neotool.security.repo.AccountMembershipRepository
import io.github.salomax.neotool.security.repo.AccountRepository
import io.github.salomax.neotool.security.repo.OAuth2ConsentRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.authentication.AuthContextFactory
import io.github.salomax.neotool.security.service.authentication.AuthenticationService
import io.github.salomax.neotool.security.test.SecurityTestDataBuilders
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.time.Instant
import java.util.UUID

class RecordingConnectedAgentsGraphQLServiceClient(
    routerUrl: String = "",
    serviceTokenClient: io.github.salomax.neotool.common.service.ServiceTokenClient = mock(),
    httpClient: io.micronaut.http.client.HttpClient = mock(),
    private val mutationVariablesRef: MutableList<Map<String, Any>?> = mutableListOf(),
    private val subjectTokensRef: MutableList<String?> = mutableListOf(),
) : GraphQLServiceClient(routerUrl, serviceTokenClient, httpClient) {
    override suspend fun mutation(
        mutation: String,
        variables: Map<String, Any>?,
        targetAudience: String,
        delegatedBy: String?,
        subjectToken: String?,
    ): GraphQLResponse {
        mutationVariablesRef.add(variables)
        subjectTokensRef.add(subjectToken)
        return GraphQLResponse(
            data =
                mapOf(
                    "revokeConnection" to
                        mapOf(
                            "clientId" to variables?.get("clientId"),
                            "accountId" to variables?.get("accountId"),
                            "revokedAt" to "2026-07-30T18:00:00Z",
                        ),
                ),
        )
    }
}

@MicronautTest(startApplication = true, transactional = false)
@Tag("integration")
open class ConnectedAgentsGraphQLIntegrationTest :
    BaseIntegrationTest(),
    PostgresIntegrationTest {
    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var accountRepository: AccountRepository

    @Inject
    lateinit var accountMembershipRepository: AccountMembershipRepository

    @Inject
    lateinit var consentRepository: OAuth2ConsentRepository

    @Inject
    lateinit var authenticationService: AuthenticationService

    @Inject
    lateinit var authContextFactory: AuthContextFactory

    private val mutationVariables = mutableListOf<Map<String, Any>?>()
    private val subjectTokens = mutableListOf<String?>()

    @MockBean(GraphQLServiceClient::class)
    fun graphQLServiceClient(): GraphQLServiceClient =
        RecordingConnectedAgentsGraphQLServiceClient(
            mutationVariablesRef = mutationVariables,
            subjectTokensRef = subjectTokens,
        )

    @AfterEach
    fun cleanUp() {
        consentRepository.deleteAll()
        accountMembershipRepository.deleteAll()
        accountRepository.deleteAll()
        userRepository.deleteAll()
        mutationVariables.clear()
        subjectTokens.clear()
    }

    @Test
    fun `connectedAgents query returns only requested account consents`() {
        val owner = createUser()
        val targetAccount = createAccount(owner.id!!)
        val otherAccount = createAccount(owner.id!!)
        grantMembership(targetAccount.id!!, owner.id!!, AccountRole.OWNER)
        grantMembership(otherAccount.id!!, owner.id!!, AccountRole.OWNER, isDefault = false)
        consentRepository.save(
            OAuth2ConsentEntity(
                userId = owner.id!!,
                clientId = "agent-a",
                accountId = targetAccount.id!!,
                grantedScopes = "finance:transactions:read",
                firstGrantedAt = Instant.parse("2026-07-30T12:00:00Z"),
                lastGrantedAt = Instant.parse("2026-07-30T13:00:00Z"),
            ),
        )
        consentRepository.save(
            OAuth2ConsentEntity(
                userId = owner.id!!,
                clientId = "agent-a",
                accountId = otherAccount.id!!,
                grantedScopes = "finance:transactions:read",
                firstGrantedAt = Instant.parse("2026-07-30T12:00:00Z"),
                lastGrantedAt = Instant.parse("2026-07-30T14:00:00Z"),
            ),
        )

        val response =
            graphQL(
                tokenFor(owner),
                """
                query ConnectedAgents(${'$'}accountId: ID!) {
                  connectedAgents(accountId: ${'$'}accountId) {
                    connectionId
                    clientId
                  }
                }
                """.trimIndent(),
                mapOf("accountId" to targetAccount.id.toString()),
            )

        assertThat(response["errors"]).isNull()
        val agents = ((response["data"] as Map<*, *>)["connectedAgents"] as List<Map<String, Any?>>)
        val agent = agents.single()
        assertThat(agent["clientId"]).isEqualTo("agent-a")
    }

    @Test
    fun `revokeConnectedAgent revokes local consent and forwards derived tuple`() {
        val owner = createUser()
        val account = createAccount(owner.id!!)
        grantMembership(account.id!!, owner.id!!, AccountRole.OWNER)
        val ownerToken = tokenFor(owner)
        val consent =
            consentRepository.save(
                OAuth2ConsentEntity(
                    userId = owner.id!!,
                    clientId = "agent-a",
                    accountId = account.id!!,
                    grantedScopes = "finance:transactions:read",
                ),
            )

        val response =
            graphQL(
                ownerToken,
                """
                mutation RevokeConnectedAgent(${'$'}connectionId: ID!) {
                  revokeConnectedAgent(connectionId: ${'$'}connectionId) {
                    connectionId
                    clientId
                    accountId
                    revokedAt
                  }
                }
                """.trimIndent(),
                mapOf("connectionId" to consent.id.toString()),
            )

        assertThat(response["errors"]).isNull()
        val payload = (response["data"] as Map<*, *>)["revokeConnectedAgent"] as Map<String, Any?>
        assertThat(payload["connectionId"]).isEqualTo(consent.id.toString())
        assertThat(payload["clientId"]).isEqualTo("agent-a")
        assertThat(
            consentRepository.findById(consent.id!!).orElseThrow().revokedAt,
        ).isEqualTo(
            Instant.parse("2026-07-30T18:00:00Z"),
        )
        assertThat(mutationVariables).singleElement().isEqualTo(
            mapOf(
                "userId" to owner.id.toString(),
                "clientId" to "agent-a",
                "accountId" to account.id.toString(),
            ),
        )
        assertThat(subjectTokens).singleElement().isEqualTo(ownerToken)
    }

    @Test
    fun `revokeConnectedAgent rejects non owner and does not call downstream mcp`() {
        val owner = createUser()
        val member = createUser()
        val account = createAccount(owner.id!!)
        grantMembership(account.id!!, owner.id!!, AccountRole.OWNER)
        grantMembership(account.id!!, member.id!!, AccountRole.MEMBER, isDefault = true)
        val consent =
            consentRepository.save(
                OAuth2ConsentEntity(
                    userId = owner.id!!,
                    clientId = "agent-a",
                    accountId = account.id!!,
                    grantedScopes = "finance:transactions:read",
                ),
            )

        val response =
            graphQL(
                tokenFor(member),
                """
                mutation RevokeConnectedAgent(${'$'}connectionId: ID!) {
                  revokeConnectedAgent(connectionId: ${'$'}connectionId) {
                    connectionId
                  }
                }
                """.trimIndent(),
                mapOf("connectionId" to consent.id.toString()),
            )

        val errors = response["errors"] as List<Map<String, Any?>>
        val extensions = errors.single()["extensions"] as Map<*, *>
        assertThat(extensions["code"]).isEqualTo("NOT_ACCOUNT_OWNER")
        assertThat(consentRepository.findById(consent.id!!).orElseThrow().revokedAt).isNull()
        assertThat(mutationVariables).isEmpty()
    }

    private fun createUser(): UserEntity {
        val user =
            SecurityTestDataBuilders.userWithPassword(
                authenticationService = authenticationService,
                email = SecurityTestDataBuilders.uniqueEmail("connected-agents"),
                password = "TestPassword123!",
            )
        return authenticationService.saveUser(user)
    }

    private fun createAccount(ownerUserId: UUID): AccountEntity =
        accountRepository.save(
            AccountEntity(
                accountName = "Connected Agents",
                accountType = AccountType.BUSINESS,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = ownerUserId,
            ),
        )

    private fun grantMembership(
        accountId: UUID,
        userId: UUID,
        role: AccountRole,
        isDefault: Boolean = true,
    ) {
        accountMembershipRepository.save(
            AccountMembershipEntity(
                accountId = accountId,
                userId = userId,
                accountRole = role,
                membershipStatus = MembershipStatus.ACTIVE,
                isDefault = isDefault,
            ),
        )
    }

    private fun tokenFor(user: UserEntity): String =
        authenticationService.generateAccessToken(
            authContextFactory.build(user),
        )

    private fun graphQL(
        token: String,
        query: String,
        variables: Map<String, Any?>,
    ): Map<String, Any?> {
        val response =
            httpClient.toBlocking().retrieve(
                HttpRequest
                    .POST(
                        "/graphql",
                        mapOf(
                            "query" to query,
                            "variables" to variables,
                        ),
                    ).header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            )
        @Suppress("UNCHECKED_CAST")
        return json.readValue(response, Map::class.java) as Map<String, Any?>
    }
}
