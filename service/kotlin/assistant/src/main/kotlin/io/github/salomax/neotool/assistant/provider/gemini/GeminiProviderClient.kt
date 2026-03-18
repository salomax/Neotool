package io.github.salomax.neotool.assistant.provider.gemini

import io.github.salomax.neotool.assistant.provider.LLMProviderClient
import io.github.salomax.neotool.assistant.provider.error.ErrorType
import io.github.salomax.neotool.assistant.provider.error.ProviderClientException
import io.github.salomax.neotool.assistant.provider.model.ProviderCompletionChunk
import io.github.salomax.neotool.assistant.provider.model.ProviderCompletionRequest
import io.github.salomax.neotool.assistant.provider.model.ProviderCompletionResponse
import io.github.salomax.neotool.assistant.provider.model.ProviderFinishReason
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.ReadTimeoutException
import io.micronaut.http.client.exceptions.HttpClientResponseException
import jakarta.annotation.PreDestroy
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.TimeoutException

@Singleton
@Requires(property = "llm.provider", value = "gemini")
class GeminiProviderClient(
    private val config: GeminiProviderConfig,
    private val geminiMapper: GeminiMapper,
) : LLMProviderClient {
    private val baseUrl: String = normalizeBaseUrl(config.baseUrl)
    private val generateContentPathTemplate: String = normalizePath(config.generateContentPath)

    private val httpClient: HttpClient by lazy {
        try {
            val requestTimeout = Duration.ofMillis(config.requestTimeoutMs.coerceAtLeast(1))
            val connectTimeoutMs = config.requestTimeoutMs.coerceAtMost(DEFAULT_CONNECT_TIMEOUT_MS).coerceAtLeast(1)
            val clientConfig =
                DefaultHttpClientConfiguration().apply {
                    setReadTimeout(requestTimeout)
                    setRequestTimeout(requestTimeout)
                    setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                }
            @Suppress("DEPRECATION")
            HttpClient.create(URL(baseUrl), clientConfig)
        } catch (exception: Exception) {
            throw IllegalStateException("Invalid Gemini base URL: $baseUrl", exception)
        }
    }

    override suspend fun complete(request: ProviderCompletionRequest): ProviderCompletionResponse {
        val apiKey = resolveApiKey(config.apiKey)
        if (apiKey.isBlank()) {
            throw ProviderClientException(
                errorType = ErrorType.AUTH_ERROR,
                message = "Gemini API key is not configured",
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                val payload = geminiMapper.toGeminiRequest(request)
                val requestPath = resolveGenerateContentPath(request.model)

                val httpRequest =
                    HttpRequest.POST(requestPath, payload)
                        .contentType(MediaType.APPLICATION_JSON_TYPE)
                        .accept(MediaType.APPLICATION_JSON_TYPE)
                        .header("x-goog-api-key", apiKey)
                        .header(HttpHeaders.USER_AGENT, config.userAgent)

                val response =
                    withTimeout(config.requestTimeoutMs) {
                        httpClient.toBlocking().exchange(httpRequest, String::class.java)
                    }

                val responseBody =
                    response.body()
                        ?: throw ProviderClientException(
                            errorType = ErrorType.PROVIDER_RESPONSE_INVALID,
                            message = "Gemini response body is empty",
                        )

                geminiMapper.toProviderCompletionResponse(responseBody)
            } catch (exception: ProviderClientException) {
                throw exception
            } catch (exception: HttpClientResponseException) {
                throw mapHttpException(exception, request)
            } catch (exception: kotlinx.coroutines.TimeoutCancellationException) {
                throw ProviderClientException(
                    errorType = ErrorType.TIMEOUT,
                    message = "Gemini request timed out after ${config.requestTimeoutMs}ms",
                    cause = exception,
                )
            } catch (exception: ReadTimeoutException) {
                throw ProviderClientException(
                    errorType = ErrorType.TIMEOUT,
                    message = "Gemini read timeout after ${config.requestTimeoutMs}ms",
                    cause = exception,
                )
            } catch (exception: SocketTimeoutException) {
                throw ProviderClientException(
                    errorType = ErrorType.TIMEOUT,
                    message = "Gemini socket timeout",
                    cause = exception,
                )
            } catch (exception: IOException) {
                throw ProviderClientException(
                    errorType = ErrorType.SERVER_ERROR,
                    message = "Gemini transport error: ${exception.message}",
                    cause = exception,
                )
            } catch (exception: Exception) {
                if (isTimeoutLike(exception)) {
                    throw ProviderClientException(
                        errorType = ErrorType.TIMEOUT,
                        message = "Gemini request timed out: ${exception.message ?: "timeout"}",
                        cause = exception,
                    )
                }
                throw ProviderClientException(
                    errorType = ErrorType.UNKNOWN,
                    message = "Unexpected Gemini provider error: ${exception.message}",
                    cause = exception,
                )
            }
        }
    }

    override fun streamComplete(request: ProviderCompletionRequest): Flow<ProviderCompletionChunk> =
        flow {
            val response = complete(request)
            val content = response.content.orEmpty()
            if (content.isNotEmpty()) {
                emit(ProviderCompletionChunk(delta = content))
            }
            emit(
                ProviderCompletionChunk(
                    delta = "",
                    finishReason = response.finishReason ?: ProviderFinishReason.STOP,
                    usage = response.usage,
                ),
            )
        }

    override fun estimateTokens(text: String): Int {
        if (text.isBlank()) {
            return 0
        }

        val regexEstimate = GPT_TOKEN_PATTERN.findAll(text).count()
        val byteEstimate = (text.toByteArray(StandardCharsets.UTF_8).size + 3) / 4
        return maxOf(1, maxOf(regexEstimate, byteEstimate))
    }

    @PreDestroy
    fun close() {
        runCatching { httpClient.close() }
    }

    private fun resolveGenerateContentPath(model: String): String {
        val normalizedModel = model.trim()
        if (normalizedModel.isEmpty()) {
            throw ProviderClientException(
                errorType = ErrorType.INVALID_REQUEST,
                message = "Gemini model cannot be blank",
            )
        }
        val pathWithModel =
            when {
                generateContentPathTemplate.contains(MODEL_TOKEN_PLACEHOLDER) ->
                    generateContentPathTemplate.replace(MODEL_TOKEN_PLACEHOLDER, normalizedModel)

                generateContentPathTemplate.contains(MODEL_PLACEHOLDER) ->
                    generateContentPathTemplate.replace(MODEL_PLACEHOLDER, normalizedModel)

                BROKEN_MODEL_PLACEHOLDER_REGEX.containsMatchIn(generateContentPathTemplate) ->
                    BROKEN_MODEL_PLACEHOLDER_REGEX.replace(generateContentPathTemplate) { match ->
                        val suffix = match.groupValues[1].trim()
                        if (suffix.isEmpty()) {
                            normalizedModel
                        } else {
                            "$normalizedModel:$suffix"
                        }
                    }

                else ->
                    DEFAULT_GENERATE_CONTENT_PATH
                        .replace(MODEL_TOKEN_PLACEHOLDER, normalizedModel)
                        .replace(MODEL_PLACEHOLDER, normalizedModel)
            }
        return if (pathWithModel.startsWith('/')) pathWithModel else "/$pathWithModel"
    }

    private fun resolveApiKey(rawValue: String): String {
        val normalized = rawValue.trim()
        if (normalized.isBlank()) {
            return ""
        }
        if (normalized.startsWith(ENV_PREFIX)) {
            return System.getenv(normalized.removePrefix(ENV_PREFIX).trim()).orEmpty()
        }
        val envLookup = System.getenv(normalized)
        if (!envLookup.isNullOrBlank()) {
            return envLookup
        }
        return normalized
    }

    private fun mapHttpException(
        exception: HttpClientResponseException,
        request: ProviderCompletionRequest,
    ): ProviderClientException {
        val statusCode = exception.status.code
        val responseBody =
            runCatching { exception.response.getBody(String::class.java).orElse(null) }
                .getOrNull()
        return mapHttpStatusError(statusCode, responseBody, request.model, exception)
    }

    private fun mapHttpStatusError(
        statusCode: Int,
        responseBody: String?,
        model: String,
        cause: Throwable? = null,
    ): ProviderClientException {
        val geminiError = geminiMapper.toGeminiError(responseBody)
        val status = geminiError?.status?.uppercase()
        val bodySnippet =
            responseBody
                ?.trim()
                ?.replace(Regex("\\s+"), " ")
                ?.take(ERROR_BODY_LOG_LIMIT)
                ?.takeIf { it.isNotEmpty() }
        val message =
            geminiError?.message
                ?: buildString {
                    append("Gemini request failed with HTTP $statusCode")
                    append(" (model=").append(model).append(")")
                    bodySnippet?.let { append(" body=").append(it) }
                }

        val contextLengthExceeded =
            status == "FAILED_PRECONDITION" ||
                status == "OUT_OF_RANGE" ||
                message.contains("context", ignoreCase = true) ||
                message.contains("token limit", ignoreCase = true)

        val isAuthError =
            statusCode == 401 || statusCode == 403 ||
                status == "UNAUTHENTICATED" || status == "PERMISSION_DENIED"
        val errorType =
            when {
                isAuthError -> ErrorType.AUTH_ERROR
                statusCode == 429 || status == "RESOURCE_EXHAUSTED" -> ErrorType.RATE_LIMIT
                status == "DEADLINE_EXCEEDED" || statusCode == 408 || statusCode == 504 -> ErrorType.TIMEOUT
                contextLengthExceeded -> ErrorType.CONTEXT_LENGTH_EXCEEDED
                statusCode in 400..499 || status == "INVALID_ARGUMENT" -> ErrorType.INVALID_REQUEST
                statusCode in 500..599 || status == "INTERNAL" || status == "UNAVAILABLE" -> ErrorType.SERVER_ERROR
                else -> ErrorType.UNKNOWN
            }

        return ProviderClientException(
            errorType = errorType,
            message = message,
            statusCode = statusCode,
            providerErrorType = geminiError?.status,
            providerErrorCode = geminiError?.code?.toString(),
            cause = cause,
        )
    }

    private fun normalizePath(path: String): String {
        val normalized = path.trim()
        if (normalized.isBlank()) {
            return DEFAULT_GENERATE_CONTENT_PATH
        }
        return if (normalized.startsWith('/')) normalized else "/$normalized"
    }

    private fun normalizeBaseUrl(raw: String): String {
        val normalized = raw.trim()
        if (normalized.isBlank()) {
            return DEFAULT_BASE_URL
        }
        if (normalized.startsWith("//")) {
            return "https:$normalized"
        }
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return normalized
        }
        return "https://$normalized"
    }

    private fun isTimeoutLike(exception: Throwable): Boolean {
        var current: Throwable? = exception
        while (current != null) {
            if (
                current is kotlinx.coroutines.TimeoutCancellationException ||
                current is ReadTimeoutException ||
                current is SocketTimeoutException ||
                current is TimeoutException
            ) {
                return true
            }
            val message = current.message?.lowercase()
            if (message?.contains("read timeout") == true || message?.contains("timed out") == true) {
                return true
            }
            current = current.cause
        }
        return false
    }

    companion object {
        private const val ENV_PREFIX = "env:"
        private const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com"
        private const val DEFAULT_GENERATE_CONTENT_PATH = "/v1beta/models/__MODEL__:generateContent"
        private const val DEFAULT_CONNECT_TIMEOUT_MS = 10_000L
        private const val MODEL_TOKEN_PLACEHOLDER = "__MODEL__"
        private const val MODEL_PLACEHOLDER = "{model}"
        private const val ERROR_BODY_LOG_LIMIT = 400
        private val BROKEN_MODEL_PLACEHOLDER_REGEX = Regex("\\{\\s*model\\s*:(.*?)\\s*\\}")

        private val GPT_TOKEN_PATTERN =
            Regex(
                """'s|'t|'re|'ve|'m|'ll|'d| ?\p{L}+| ?\p{N}+| ?[^\s\p{L}\p{N}]+|\s+(?!\S)|\s+""",
            )
    }
}
