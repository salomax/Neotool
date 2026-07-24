package io.github.salomax.neotool.common.service

import io.github.salomax.neotool.common.tracing.TraceExecution
import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.serde.annotation.Serdeable
import jakarta.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Token request DTO for OAuth2 client credentials flow.
 */
@Serdeable
internal data class TokenRequest(
    val grant_type: String = "client_credentials",
    val client_id: String,
    val client_secret: String,
    val audience: String? = null,
    val delegated_by: String? = null,
    val subject_token: String? = null,
    val subject_token_type: String? = null,
)

/**
 * Token response DTO from Security Service.
 * Public for testing; production code uses it only via ServiceTokenClient.
 */
@Serdeable
data class TokenResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Long,
)

/**
 * HTTP client for services to obtain service tokens from Security Service.
 * Implements token caching with TTL to reduce token endpoint calls.
 */
@TraceExecution
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
     * Get a service token for the specified target audience.
     * Caches tokens with TTL to reduce token endpoint calls.
     *
     * @param targetAudience The target service identifier (aud claim)
     * @return JWT service token
     * @throws IllegalStateException if service credentials are not configured
     * @throws HttpClientResponseException if token request fails
     */
    suspend fun getServiceToken(
        targetAudience: String,
        delegatedBy: String? = null,
    ): String =
        withContext(Dispatchers.IO) {
            val cacheKey = "$targetAudience|${delegatedBy.orEmpty()}"

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
                val (token, expiresIn) = fetchTokenFromSecurityService(targetAudience, delegatedBy)

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
     * @param targetAudience The target service identifier
     * @param subjectToken The user's OAuth access token being exchanged
     * @return JWT service token with user context
     */
    suspend fun getServiceTokenWithUserContext(
        targetAudience: String,
        subjectToken: String,
        delegatedBy: String? = null,
    ): String =
        withContext(Dispatchers.IO) {
            val cacheKey = "obo|$targetAudience|${delegatedBy.orEmpty()}|${sha256Hex(subjectToken)}"
            val cached = tokenCache[cacheKey]
            if (cached != null && !cached.isExpired()) {
                return@withContext cached.token
            }
            evictExpiredTokens()

            val inFlightDeferred = inFlightFetches[cacheKey]
            if (inFlightDeferred != null) {
                val token = inFlightDeferred.await()
                val cachedAfterWait = tokenCache[cacheKey]
                if (cachedAfterWait != null && !cachedAfterWait.isExpired()) {
                    return@withContext cachedAfterWait.token
                }
                return@withContext token
            }

            val fetchDeferred = CompletableDeferred<String>()
            val existingDeferred = inFlightFetches.putIfAbsent(cacheKey, fetchDeferred)
            if (existingDeferred != null) {
                val token = existingDeferred.await()
                val cachedAfterWait = tokenCache[cacheKey]
                if (cachedAfterWait != null && !cachedAfterWait.isExpired()) {
                    return@withContext cachedAfterWait.token
                }
                return@withContext token
            }

            try {
                val (token, expiresIn) =
                    fetchUserContextTokenFromSecurityService(
                        audience = targetAudience,
                        subjectToken = subjectToken,
                        delegatedBy = delegatedBy,
                    )
                cacheLock.withLock {
                    tokenCache[cacheKey] = CachedToken(token, System.currentTimeMillis() + expiresIn * 1000)
                }
                fetchDeferred.complete(token)
                inFlightFetches.remove(cacheKey)
                token
            } catch (e: Exception) {
                fetchDeferred.completeExceptionally(e)
                inFlightFetches.remove(cacheKey)
                throw e
            }
        }

    /**
     * Fetch token from Security Service.
     * @return Pair of (token, expiresInSeconds)
     */
    private suspend fun fetchTokenFromSecurityService(
        audience: String,
        delegatedBy: String? = null,
    ): Pair<String, Long> {
        val serviceId = this.serviceId ?: throw IllegalStateException("Service ID not configured")
        val clientSecret =
            this.clientSecret ?: throw IllegalStateException("Client secret not configured")

        val request =
            TokenRequest(
                client_id = serviceId,
                client_secret = clientSecret,
                audience = audience,
                delegated_by = delegatedBy,
            )

        try {
            val httpRequest =
                HttpRequest
                    .POST("$securityServiceUrl/oauth/token", request)
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

    private suspend fun fetchUserContextTokenFromSecurityService(
        audience: String,
        subjectToken: String,
        delegatedBy: String? = null,
    ): Pair<String, Long> {
        val serviceId = this.serviceId ?: throw IllegalStateException("Service ID not configured")
        val clientSecret =
            this.clientSecret ?: throw IllegalStateException("Client secret not configured")

        val request =
            TokenRequest(
                grant_type = "urn:ietf:params:oauth:grant-type:token-exchange",
                client_id = serviceId,
                client_secret = clientSecret,
                audience = audience,
                subject_token = subjectToken,
                subject_token_type = "urn:ietf:params:oauth:token-type:access_token",
                delegated_by = delegatedBy,
            )

        try {
            val httpRequest =
                HttpRequest
                    .POST("$securityServiceUrl/oauth2/token", request)
                    .contentType(MediaType.APPLICATION_JSON)

            val response = httpClient.toBlocking().exchange(httpRequest, TokenResponse::class.java)
            val tokenResponse =
                response.body()
                    ?: throw IllegalStateException("Empty token response from Security Service")

            logger.debug { "User-context token obtained successfully for audience: $audience" }
            return Pair(tokenResponse.access_token, tokenResponse.expires_in)
        } catch (e: HttpClientResponseException) {
            logger.error(e) { "Failed to exchange user-context token: ${e.message}" }
            throw IllegalStateException("Failed to obtain user-context service token: ${e.message}", e)
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error exchanging user-context token: ${e.message}" }
            throw IllegalStateException("Failed to obtain user-context service token: ${e.message}", e)
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

    private fun evictExpiredTokens() {
        cacheLock.withLock {
            tokenCache.entries.removeIf { (_, cachedToken) -> cachedToken.isExpired(bufferSeconds = 0) }
        }
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
