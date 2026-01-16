package io.github.salomax.neotool.common.security.service

import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import jakarta.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * HTTP client for services to obtain service tokens from Security Service.
 * Implements token caching with TTL to reduce token endpoint calls.
 */
@Singleton
class ServiceTokenClient(
    @Property(name = "security.service.url", defaultValue = "http://localhost:8080")
    private val securityServiceUrl: String,
    @Property(name = "security.service.id")
    private val serviceId: String?,
    @Property(name = "security.service.secret")
    private val clientSecret: String?,
    private val httpClient: HttpClient,
) {
    private val logger = KotlinLogging.logger {}

    private val tokenCache = ConcurrentHashMap<String, CachedToken>()
    private val cacheLock = ReentrantLock()

    // Track in-flight fetches: map from audience to deferred result
    // This allows concurrent requests to wait for the same fetch
    private val inFlightFetches = ConcurrentHashMap<String, CompletableDeferred<String>>()

    /**
     * Cached token with expiration time.
     */
    private data class CachedToken(
        val token: String,
        val expiresAt: Long,
    ) {
        fun isExpired(bufferSeconds: Long = 60): Boolean =
            System.currentTimeMillis() > (expiresAt - bufferSeconds * 1000)
    }

    /**
     * Token request DTO.
     */
    private data class TokenRequest(
        val grant_type: String = "client_credentials",
        val client_id: String,
        val client_secret: String,
        val audience: String? = null,
    )

    /**
     * Token response DTO.
     */
    private data class TokenResponse(
        val access_token: String,
        val token_type: String,
        val expires_in: Long,
    )

    /**
     * Get a service token for the specified target audience.
     * Caches tokens with TTL to reduce token endpoint calls.
     *
     * @param targetAudience The target service identifier (aud claim)
     * @return JWT service token
     * @throws IllegalStateException if service credentials are not configured
     * @throws HttpClientResponseException if token request fails
     */
    suspend fun getServiceToken(targetAudience: String): String =
        withContext(Dispatchers.IO) {
            val cacheKey = targetAudience

            // Fast path: check cache without lock
            val cached = tokenCache[cacheKey]
            if (cached != null && !cached.isExpired()) {
                logger.debug { "Returning cached token for audience: $targetAudience" }
                return@withContext cached.token
            }

            // Check if a fetch is already in progress for this audience
            val inFlightDeferred = inFlightFetches[cacheKey]
            if (inFlightDeferred != null) {
                // Another coroutine is fetching, wait for it to complete
                logger.debug { "Waiting for in-flight token fetch for audience: $targetAudience" }
                val token = inFlightDeferred.await()
                // Double-check cache after waiting (it should be populated now)
                val cachedAfterWait = tokenCache[cacheKey]
                if (cachedAfterWait != null && !cachedAfterWait.isExpired()) {
                    return@withContext cachedAfterWait.token
                }
                return@withContext token
            }

            // Create a deferred for this fetch
            val fetchDeferred = CompletableDeferred<String>()
            val existingDeferred = inFlightFetches.putIfAbsent(cacheKey, fetchDeferred)

            if (existingDeferred != null) {
                // Another coroutine started fetching between our check and put, wait for it
                logger.debug { "Another coroutine started fetching, waiting for audience: $targetAudience" }
                val token = existingDeferred.await()
                val cachedAfterWait = tokenCache[cacheKey]
                if (cachedAfterWait != null && !cachedAfterWait.isExpired()) {
                    return@withContext cachedAfterWait.token
                }
                return@withContext token
            }

            try {
                // Fetch new token (suspend function, outside blocking locks)
                logger.debug { "Fetching new token for audience: $targetAudience" }
                val (token, expiresIn) = fetchTokenFromSecurityService(targetAudience)

                // Cache token (use cache lock for thread-safe cache update)
                cacheLock.withLock {
                    // Use expires_in from response, with 60 second buffer
                    val expiresAt = System.currentTimeMillis() + (expiresIn * 1000)
                    tokenCache[cacheKey] = CachedToken(token, expiresAt)
                }

                // Complete the deferred so waiting coroutines get the token
                fetchDeferred.complete(token)
                // Remove from in-flight map after successful completion
                inFlightFetches.remove(cacheKey)
                token
            } catch (e: Exception) {
                // Complete with exception so waiting coroutines get the error
                fetchDeferred.completeExceptionally(e)
                // Remove from in-flight map
                inFlightFetches.remove(cacheKey)
                throw e
            }
        }

    /**
     * Get a service token with propagated user context.
     *
     * Note: This feature is not yet implemented. User context propagation requires
     * a separate OAuth2 endpoint that accepts both service credentials and user tokens.
     *
     * @param targetAudience The target service identifier
     * @param userId The user ID being propagated
     * @param userPermissions List of user permission names
     * @return JWT service token with user context
     * @throws UnsupportedOperationException until implementation is complete
     */
    suspend fun getServiceTokenWithUserContext(
        targetAudience: String,
        userId: UUID,
        userPermissions: List<String>,
    ): String {
        throw UnsupportedOperationException(
            "User context propagation is not yet implemented. " +
                "This requires a separate OAuth2 endpoint that accepts both service credentials and user tokens. " +
                "Use getServiceToken() for service-only tokens.",
        )
    }

    /**
     * Fetch token from Security Service.
     * @return Pair of (token, expiresInSeconds)
     */
    private suspend fun fetchTokenFromSecurityService(audience: String): Pair<String, Long> {
        val serviceId = this.serviceId ?: throw IllegalStateException("Service ID not configured")
        val clientSecret =
            this.clientSecret ?: throw IllegalStateException("Client secret not configured")

        val request =
            TokenRequest(
                client_id = serviceId,
                client_secret = clientSecret,
                audience = audience,
            )

        try {
            val httpRequest =
                HttpRequest.POST("$securityServiceUrl/oauth/token", request)
                    .contentType(MediaType.APPLICATION_JSON)

            val response = httpClient.toBlocking().exchange(httpRequest, TokenResponse::class.java)
            val tokenResponse = response.body()

            if (tokenResponse == null) {
                throw IllegalStateException("Empty token response from Security Service")
            }

            logger.debug { "Token obtained successfully for audience: $audience" }
            return Pair(tokenResponse.access_token, tokenResponse.expires_in)
        } catch (e: HttpClientResponseException) {
            logger.error(e) { "Failed to obtain token from Security Service: ${e.message}" }
            throw IllegalStateException("Failed to obtain service token: ${e.message}", e)
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error obtaining token: ${e.message}" }
            throw IllegalStateException("Failed to obtain service token: ${e.message}", e)
        }
    }

    /**
     * Clear token cache (useful for testing or credential rotation).
     */
    fun clearCache() {
        cacheLock.withLock {
            tokenCache.clear()
        }
    }
}
