package io.github.salomax.neotool.assistant.graphql

import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.net.URL

@Singleton
class GraphQLClient(
    @Property(name = "graphql.endpoint", defaultValue = "http://localhost:4000/graphql") private val endpoint: String,
    private val objectMapper: ObjectMapper
) {
    
    private val logger = LoggerFactory.getLogger(GraphQLClient::class.java)
    
    // Parse and normalize the endpoint URL - separate base URL from path
    private val baseUrl: String by lazy {
        val normalized = endpoint.trim()
        // If the endpoint doesn't start with http:// or https://, assume it's a partial URL
        val fullUrl = if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            // Check if it starts with digits (likely a port number)
            val portMatch = Regex("^(\\d+)([:/].*)?$").find(normalized)
            if (portMatch != null) {
                // It starts with digits - treat as port number
                val port = portMatch.groupValues[1]
                "http://localhost:$port"
            } else if (normalized.startsWith(":") || normalized.startsWith("/")) {
                // If it starts with : or /, prepend http://localhost
                if (normalized.startsWith(":")) {
                    "http://localhost$normalized"
                } else {
                    "http://localhost"
                }
            } else {
                // Otherwise, assume it's a hostname and prepend http://
                "http://$normalized"
            }
        } else {
            normalized
        }
        
        // Extract base URL (without path)
        @Suppress("DEPRECATION")
        val url = URL(fullUrl)
        val base = "${url.protocol}://${url.host}${if (url.port != -1 && url.port != url.defaultPort) ":${url.port}" else ""}"
        logger.info("GraphQL base URL configured: $base (from: $endpoint)")
        base
    }
    
    private val graphqlPath: String by lazy {
        val normalized = endpoint.trim()
        // Extract path from the endpoint
        val fullUrl = if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            val portMatch = Regex("^(\\d+)([:/].*)?$").find(normalized)
            if (portMatch != null) {
                val path = portMatch.groupValues[2] ?: ""
                when {
                    path.isEmpty() -> "/graphql"
                    path.startsWith(":") -> "/${path.substring(1)}"
                    path.startsWith("/") -> path
                    else -> "/$path"
                }
            } else if (normalized.startsWith("/")) {
                normalized
            } else if (normalized.startsWith(":")) {
                "/${normalized.substring(1)}"
            } else {
                "/graphql"
            }
        } else {
            @Suppress("DEPRECATION")
            val url = URL(normalized)
            url.path.ifEmpty { "/graphql" }
        }
        
        // Ensure it ends with /graphql if it doesn't already
        val finalPath = if (!fullUrl.endsWith("/graphql") && !fullUrl.endsWith("/graphql/")) {
            if (fullUrl.endsWith("/")) {
                "${fullUrl}graphql"
            } else {
                "$fullUrl/graphql"
            }
        } else {
            fullUrl
        }
        
        logger.info("GraphQL path configured: $finalPath")
        finalPath
    }
    
    // Create HTTP client from the base URL (without path)
    private val httpClient: HttpClient by lazy {
        try {
            @Suppress("DEPRECATION")
            val url = URL(baseUrl)
            HttpClient.create(url)
        } catch (e: Exception) {
            logger.error("Failed to create HTTP client for GraphQL base URL: $baseUrl", e)
            throw IllegalStateException("Invalid GraphQL base URL: $baseUrl", e)
        }
    }
    
    suspend fun execute(request: GraphQLRequest): GraphQLResponse {
        return withContext(Dispatchers.IO) {
            try {
                // Debug logging: log the GraphQL query and variables
                val fullEndpoint = "$baseUrl$graphqlPath"
                logger.debug("Executing GraphQL request to: $fullEndpoint")
                logger.debug("GraphQL Query: ${request.query}")
                logger.debug("GraphQL Variables: ${objectMapper.writeValueAsString(request.variables)}")
                if (request.operationName != null) {
                    logger.debug("GraphQL Operation Name: ${request.operationName}")
                }
                
                // Log the full request as JSON for debugging
                val requestJson = objectMapper.writeValueAsString(request)
                logger.debug("Full GraphQL Request JSON: $requestJson")
                
                // POST to the graphql path (not empty string)
                val httpRequest = HttpRequest.POST(graphqlPath, request)
                    .contentType(MediaType.APPLICATION_JSON)
                
                val response = httpClient.toBlocking().exchange(httpRequest, Map::class.java)
                val body = response.body() as? Map<*, *> ?: emptyMap<Any, Any>()
                
                // Log response for debugging
                logger.debug("GraphQL Response Status: ${response.status()}")
                logger.debug("GraphQL Response Body: ${objectMapper.writeValueAsString(body)}")
                
                GraphQLResponse(
                    data = body["data"] as? Map<*, *>,
                    errors = (body["errors"] as? List<*>)?.mapNotNull { it as? Map<*, *> }
                )
            } catch (e: HttpClientResponseException) {
                // Handle HTTP client response exceptions (like 404, 500, etc.)
                val status = e.status
                val errorBody = try {
                    e.response.body()?.toString() ?: "No response body"
                } catch (ex: Exception) {
                    "Could not read error response body: ${ex.message}"
                }
                
                val fullEndpoint = "$baseUrl$graphqlPath"
                logger.error("HTTP error executing GraphQL request to $fullEndpoint: ${status.code} ${status.name}")
                logger.error("Failed GraphQL Query: ${request.query}")
                logger.error("Failed GraphQL Variables: ${objectMapper.writeValueAsString(request.variables)}")
                logger.error("Error Response Body: $errorBody")
                
                GraphQLResponse(
                    data = null,
                    errors = listOf(mapOf("message" to "HTTP ${status.code}: ${status.name}", "details" to errorBody))
                )
            } catch (e: Exception) {
                val fullEndpoint = "$baseUrl$graphqlPath"
                logger.error("Error executing GraphQL request to $fullEndpoint", e)
                // Log the request details even on error for debugging
                logger.error("Failed GraphQL Query: ${request.query}")
                logger.error("Failed GraphQL Variables: ${objectMapper.writeValueAsString(request.variables)}")
                GraphQLResponse(
                    data = null,
                    errors = listOf(mapOf("message" to (e.message ?: "Unknown error")))
                )
            }
        }
    }
}

