package io.github.salomax.neotool.mcp.security

import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.mcp.test.McpJwtTestFixture
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

@MicronautTest(startApplication = true, transactional = false)
@DisplayName("MCP connection revocation integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("mcp")
open class ConnectionRevocationIntegrationTest :
    BaseIntegrationTest(),
    PostgresIntegrationTest,
    TestPropertyProvider {
    @Inject
    lateinit var repository: ConnectionRevocationRepository

    override fun getProperties(): MutableMap<String, String> {
        val props = super.getProperties()
        props["jwt.issuer"] = McpJwtTestFixture.ISSUER
        props["jwt.public-key"] = McpJwtTestFixture.publicKeyPem
        props["mcp.resource.audience"] = McpJwtTestFixture.AUDIENCE
        props["mcp.resource.metadata-url"] = "https://mcp.example.test/.well-known/oauth-protected-resource"
        return props
    }

    @BeforeEach
    override fun setUp() {
        super.setUp()
        repository.deleteAll()
    }

    @AfterEach
    fun cleanUp() {
        repository.deleteAll()
    }

    @Test
    fun `revoked connection rejects token on next call including tokens minted after revocation`() {
        val userId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val clientId = "agent-${UUID.randomUUID()}"
        repository.save(
            ConnectionRevocationEntity(
                id = ConnectionRevocationId(userId, clientId, accountId),
                revokedAt = Instant.now(),
                revokedUntil = null,
                reason = "test revocation",
            ),
        )
        val tokenMintedAfterRevocation =
            McpJwtTestFixture.accessToken(
                userId = userId,
                accountId = accountId,
                clientId = clientId,
                issuedAt = Instant.now().plusSeconds(1),
            )

        val exception =
            assertThrows<HttpClientResponseException> {
                httpClient.toBlocking().exchange(probeRequest(tokenMintedAfterRevocation), OAuthError::class.java)
            }

        assertThat(exception.status).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(exception.response.getBody(OAuthError::class.java)).hasValueSatisfying {
            assertThat(it.error).isEqualTo("invalid_token")
            assertThat(it.error_description).isEqualTo("The connection has been revoked")
        }
    }

    @Test
    fun `eviction removes expired revocations and leaves live revocations`() {
        val expired =
            ConnectionRevocationEntity(
                id = ConnectionRevocationId(UUID.randomUUID(), "expired-agent", UUID.randomUUID()),
                revokedAt = Instant.now().minusSeconds(3600),
                revokedUntil = Instant.now().minusSeconds(60),
            )
        val live =
            ConnectionRevocationEntity(
                id = ConnectionRevocationId(UUID.randomUUID(), "live-agent", UUID.randomUUID()),
                revokedAt = Instant.now(),
                revokedUntil = Instant.now().plusSeconds(3600),
            )
        val permanent =
            ConnectionRevocationEntity(
                id = ConnectionRevocationId(UUID.randomUUID(), "permanent-agent", UUID.randomUUID()),
                revokedAt = Instant.now(),
                revokedUntil = null,
            )
        repository.saveAll(listOf(expired, live, permanent))

        val deleted = repository.deleteExpired(Instant.now())

        assertThat(deleted).isEqualTo(1)
        assertThat(repository.existsById(expired.id)).isFalse()
        assertThat(repository.existsById(live.id)).isTrue()
        assertThat(repository.existsById(permanent.id)).isTrue()
    }

    @Test
    fun `graphQL revocation rejects the next probe call`() {
        val userId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val clientId = "agent-${UUID.randomUUID()}"
        val serviceToken =
            McpJwtTestFixture.serviceToken(
                userId = userId,
                currentAccountId = accountId,
            )

        httpClient
            .toBlocking()
            .retrieve(
                HttpRequest
                    .POST(
                        "/graphql",
                        mapOf(
                            "query" to
                                """
                                mutation RevokeConnection(${'$'}userId: ID!, ${'$'}clientId: String!, ${'$'}accountId: ID!) {
                                  revokeConnection(userId: ${'$'}userId, clientId: ${'$'}clientId, accountId: ${'$'}accountId) {
                                    userId
                                  }
                                }
                                """.trimIndent(),
                            "variables" to
                                mapOf(
                                    "userId" to userId.toString(),
                                    "clientId" to clientId,
                                    "accountId" to accountId.toString(),
                                ),
                        ),
                    ).header(HttpHeaders.AUTHORIZATION, "Bearer $serviceToken"),
            )

        val exception =
            assertThrows<HttpClientResponseException> {
                httpClient
                    .toBlocking()
                    .exchange(
                        probeRequest(
                            McpJwtTestFixture.accessToken(
                                userId = userId,
                                accountId = accountId,
                                clientId = clientId,
                                issuedAt = Instant.now().plusSeconds(1),
                            ),
                        ),
                        OAuthError::class.java,
                    )
            }

        assertThat(exception.status).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(exception.response.getBody(OAuthError::class.java)).hasValueSatisfying {
            assertThat(it.error).isEqualTo("invalid_token")
            assertThat(it.error_description).isEqualTo("The connection has been revoked")
        }
    }

    private fun probeRequest(token: String): HttpRequest<Any> =
        HttpRequest.GET<Any>("/mcp/probe").header(HttpHeaders.AUTHORIZATION, "Bearer $token")
}
