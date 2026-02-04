package io.github.salomax.neotool.common.security.service

import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.serde.annotation.Serdeable
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging

/**
 * GraphQL request DTO.
 */
@Serdeable
data class GraphQLRequest(
    val query: String,
    val variables: Map<String, Any>? = null,
    val operationName: String? = null,
)

/**
 * GraphQL response DTO.
 */
@Serdeable
data class GraphQLResponse(
    val data: Map<String, Any>? = null,
    val errors: List<Map<String, Any>>? = null,
)

/**
 * HTTP client for services to call Apollo Router with service tokens.
 * Automatically injects Authorization header with service token.
 *
 * Class is open to allow mocking in tests (e.g. @MockBean in Micronaut tests).
 */
@Singleton
open class GraphQLServiceClient(
    @Property(name = "graphql.router.url", defaultValue = "http://localhost:4000/graphql")
    private val routerUrl: String,
    private val serviceTokenClient: ServiceTokenClient,
    private val httpClient: HttpClient,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Execute a GraphQL query with automatic service token injection.
     *
     * @param query GraphQL query string
     * @param variables Optional query variables
     * @param targetAudience Target service audience for token (default: "apollo-router")
     * @return GraphQL response
     */
    suspend fun query(
        query: String,
        variables: Map<String, Any>? = null,
        targetAudience: String = "apollo-router",
    ): GraphQLResponse =
        withContext(Dispatchers.IO) {
            executeGraphQL(
                query = query,
                variables = variables,
                targetAudience = targetAudience,
            )
        }

    /**
     * Execute a GraphQL mutation with automatic service token injection.
     *
     * @param mutation GraphQL mutation string
     * @param variables Optional mutation variables
     * @param targetAudience Target service audience for token (default: "apollo-router")
     * @return GraphQL response
     */
    open suspend fun mutation(
        mutation: String,
        variables: Map<String, Any>? = null,
        targetAudience: String = "apollo-router",
    ): GraphQLResponse =
        withContext(Dispatchers.IO) {
            executeGraphQL(
                query = mutation,
                variables = variables,
                targetAudience = targetAudience,
            )
        }

    /**
     * Execute GraphQL request with service token.
     */
    private suspend fun executeGraphQL(
        query: String,
        variables: Map<String, Any>?,
        targetAudience: String,
    ): GraphQLResponse {
        // Get service token
        val token = serviceTokenClient.getServiceToken(targetAudience)

        // Build GraphQL request
        val request = GraphQLRequest(query = query, variables = variables)

        try {
            val httpRequest =
                HttpRequest
                    .POST(routerUrl, request)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bearerAuth(token)

            val response = httpClient.toBlocking().exchange(httpRequest, Map::class.java)
            val body = response.body() as? Map<*, *> ?: emptyMap<Any, Any>()

            // Parse response
            val data = body["data"] as? Map<*, *>
            val errors = body["errors"] as? List<*>

            return GraphQLResponse(
                data =
                    data
                        ?.mapKeys {
                            it.key.toString()
                        }?.mapValues {
                            it.value as Any
                        },
                errors =
                    errors
                        ?.mapNotNull {
                            it as? Map<*, *>
                        }?.map {
                            it
                                .mapKeys {
                                    it.key.toString()
                                }.mapValues {
                                    it.value as Any
                                }
                        },
            )
        } catch (e: HttpClientResponseException) {
            // Handle 401 by clearing cache and retrying once
            if (e.status.code == 401) {
                logger.warn { "Received 401, clearing token cache and retrying" }
                serviceTokenClient.clearCache()
                val newToken = serviceTokenClient.getServiceToken(targetAudience)

                val httpRequest =
                    HttpRequest
                        .POST(routerUrl, request)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bearerAuth(newToken)

                val response = httpClient.toBlocking().exchange(httpRequest, Map::class.java)
                val body = response.body() as? Map<*, *> ?: emptyMap<Any, Any>()

                val data = body["data"] as? Map<*, *>
                val errors = body["errors"] as? List<*>

                return GraphQLResponse(
                    data =
                        data
                            ?.mapKeys {
                                it.key.toString()
                            }?.mapValues {
                                it.value as Any
                            },
                    errors =
                        errors
                            ?.mapNotNull {
                                it as? Map<*, *>
                            }?.map {
                                it
                                    .mapKeys {
                                        it.key.toString()
                                    }.mapValues {
                                        it.value as Any
                                    }
                            },
                )
            }

            logger.error(e) { "GraphQL request failed: ${e.message}" }
            throw IllegalStateException("GraphQL request failed: ${e.message}", e)
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error in GraphQL request: ${e.message}" }
            throw IllegalStateException("GraphQL request failed: ${e.message}", e)
        }
    }
}
