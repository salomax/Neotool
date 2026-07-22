package io.github.salomax.neotool.mcp.dispatch

import io.github.salomax.neotool.common.service.GraphQLRequest
import io.github.salomax.neotool.common.service.GraphQLResponse
import io.github.salomax.neotool.common.service.ServiceTokenClient
import io.github.salomax.neotool.mcp.protocol.McpToolExecutionException
import io.github.salomax.neotool.mcp.security.McpPrincipal
import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface McpBankingGraphQLClient {
    suspend fun query(
        query: String,
        variables: Map<String, Any>,
        principal: McpPrincipal,
    ): GraphQLResponse
}

@Singleton
class DefaultMcpBankingGraphQLClient(
    @Property(name = "graphql.router.url", defaultValue = "http://localhost:4000/graphql")
    private val routerUrl: String,
    @Property(name = "mcp.dispatch.banking-target-audience", defaultValue = "apollo-router")
    private val targetAudience: String,
    private val serviceTokenClient: ServiceTokenClient,
    private val httpClient: HttpClient,
) : McpBankingGraphQLClient {
    override suspend fun query(
        query: String,
        variables: Map<String, Any>,
        principal: McpPrincipal,
    ): GraphQLResponse =
        withContext(Dispatchers.IO) {
            requireNotNull(principal.principal.currentAccountId) { "MCP token has no active account context" }
            val token = serviceTokenClient.getServiceTokenWithUserContext(targetAudience, principal.principal.token)
            val request = GraphQLRequest(query = query, variables = variables)
            val response =
                try {
                    httpClient.toBlocking().exchange(
                        HttpRequest
                            .POST(routerUrl, request)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bearerAuth(token),
                        Map::class.java,
                    )
                } catch (e: HttpClientResponseException) {
                    throw e.toToolException()
                }
            val body = response.body() as? Map<*, *> ?: emptyMap<Any, Any>()
            GraphQLResponse(
                data = (body["data"] as? Map<*, *>)?.normalizeMap(),
                errors = (body["errors"] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.normalizeMap() },
            )
        }

    private fun Map<*, *>.normalizeMap(): Map<String, Any?> =
        entries.associate { (key, value) -> key.toString() to value }

    private fun HttpClientResponseException.toToolException(): McpToolExecutionException {
        val code =
            when (status.code) {
                401 -> "BANKING_AUTHENTICATION_REQUIRED"
                403 -> "BANKING_AUTHORIZATION_DENIED"
                else -> "BANKING_GRAPHQL_HTTP_ERROR"
            }
        return McpToolExecutionException(
            code = code,
            service = "banking",
            parameters = mapOf("status" to status.code),
            cause = this,
        )
    }
}
