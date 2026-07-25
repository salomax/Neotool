package io.github.salomax.neotool.mcp.dispatch

import io.github.salomax.neotool.common.security.agent.AgentCallContext
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.github.salomax.neotool.common.service.ServiceTokenClient
import io.github.salomax.neotool.mcp.security.McpPrincipal
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class McpBankingGraphQLClientTest {
    private lateinit var httpClient: HttpClient
    private lateinit var blockingHttpClient: BlockingHttpClient
    private lateinit var serviceTokenClient: ServiceTokenClient
    private lateinit var client: DefaultMcpBankingGraphQLClient

    @BeforeEach
    fun setUp() {
        httpClient = mock()
        blockingHttpClient = mock()
        serviceTokenClient = mock()
        whenever(httpClient.toBlocking()).thenReturn(blockingHttpClient)
        client =
            DefaultMcpBankingGraphQLClient(
                bankingGraphQlUrl = "http://localhost:8092/graphql",
                targetAudience = "banking",
                delegatingServiceId = "mcp-service",
                serviceTokenClient = serviceTokenClient,
                httpClient = httpClient,
            )
    }

    @Test
    fun `dispatches directly to banking with trusted provenance headers`() {
        val toolCallId = UUID.randomUUID()
        val principal =
            McpPrincipal(
                principal =
                    RequestPrincipal(
                        principalType = PrincipalType.USER,
                        userId = UUID.randomUUID(),
                        serviceId = null,
                        token = "subject-token",
                        tokenId = "origin-token-id",
                        permissionsFromToken = emptyList(),
                        currentAccountId = UUID.randomUUID(),
                    ),
                clientId = "mcp-client",
                scopes = setOf("finance:transactions:read"),
                agentCallContext =
                    AgentCallContext(
                        toolCallId = toolCallId,
                        originClientId = "mcp-client",
                        originTokenId = "origin-token-id",
                    ),
            )
        runBlocking {
            whenever(
                serviceTokenClient.getServiceTokenWithUserContext(
                    targetAudience = "banking",
                    subjectToken = "subject-token",
                    delegatedBy = "mcp-service",
                ),
            )
                .thenReturn("delegated-token")
        }
        val response: HttpResponse<Any> = mock()
        whenever(response.body()).thenReturn(mapOf("data" to emptyMap<String, Any>()))
        whenever(blockingHttpClient.exchange(any<HttpRequest<Any>>(), any<Class<*>>())).thenReturn(response)

        runBlocking {
            client.query(
                query = "query { transactions { totalCount } }",
                variables = emptyMap(),
                principal = principal,
            )
        }

        val requestCaptor = argumentCaptor<HttpRequest<Any>>()
        verify(blockingHttpClient).exchange(requestCaptor.capture(), any<Class<*>>())
        val request = requestCaptor.firstValue
        assertThat(request.uri.toString()).isEqualTo("http://localhost:8092/graphql")
        assertThat(request.headers.get(AgentCallContext.TOOL_CALL_ID_HEADER)).isEqualTo(toolCallId.toString())
        assertThat(request.headers.get(AgentCallContext.CLIENT_ID_HEADER)).isEqualTo("mcp-client")
        assertThat(request.headers.get(AgentCallContext.TOKEN_ID_HEADER)).isEqualTo("origin-token-id")
    }
}
