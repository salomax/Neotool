package io.github.salomax.neotool.assistant.provider.openai

import io.github.salomax.neotool.assistant.provider.LLMProviderClient
import io.github.salomax.neotool.assistant.provider.error.ErrorType
import io.github.salomax.neotool.assistant.provider.error.ProviderClientException
import io.github.salomax.neotool.assistant.provider.model.ProviderCompletionChunk
import io.github.salomax.neotool.assistant.provider.model.ProviderCompletionRequest
import io.github.salomax.neotool.assistant.provider.model.ProviderCompletionResponse
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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.TimeoutException

@Singleton
@Requires(property = "llm.provider", value = "openai")
class OpenAIProviderClient(
    private val config: OpenAIProviderConfig,
    private val openAIMapper: OpenAIMapper,
) : LLMProviderClient {
    private val logger = LoggerFactory.getLogger(OpenAIProviderClient::class.java)

    private val baseUrl: String = config.baseUrl.trim()
    private val chatCompletionsPath: String = normalizePath(config.chatCompletionsPath)

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
            throw IllegalStateException("Invalid OpenAI base URL: $baseUrl", exception)
        }
    }

    override suspend fun complete(request: ProviderCompletionRequest): ProviderCompletionResponse {
        val apiKey = resolveApiKey(config.apiKeyRef)
        if (apiKey.isBlank()) {
            throw ProviderClientException(
                errorType = ErrorType.AUTH_ERROR,
                message = "OpenAI API key is not configured",
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                val openAIRequest = openAIMapper.toOpenAIRequest(request)
                val httpRequest =
                    HttpRequest.POST(chatCompletionsPath, openAIRequest)
                        .contentType(MediaType.APPLICATION_JSON_TYPE)
                        .accept(MediaType.APPLICATION_JSON_TYPE)
                        .bearerAuth(apiKey)
                        .header(HttpHeaders.USER_AGENT, config.userAgent)
                        .header(HEADER_REQUEST_TIMEOUT_MS, config.requestTimeoutMs.toString())

                val response =
                    withTimeout(config.requestTimeoutMs) {
                        httpClient.toBlocking().exchange(httpRequest, String::class.java)
                    }

                val responseBody =
                    response.body()
                        ?: throw ProviderClientException(
                            errorType = ErrorType.PROVIDER_RESPONSE_INVALID,
                            message = "OpenAI response body is empty",
                        )

                openAIMapper.toProviderCompletionResponse(responseBody)
            } catch (exception: ProviderClientException) {
                throw exception
            } catch (exception: HttpClientResponseException) {
                throw mapHttpException(exception)
            } catch (exception: TimeoutCancellationException) {
                throw ProviderClientException(
                    errorType = ErrorType.TIMEOUT,
                    message = "OpenAI request timed out after ${config.requestTimeoutMs}ms",
                    cause = exception,
                )
            } catch (exception: ReadTimeoutException) {
                throw ProviderClientException(
                    errorType = ErrorType.TIMEOUT,
                    message = "OpenAI read timeout after ${config.requestTimeoutMs}ms",
                    cause = exception,
                )
            } catch (exception: SocketTimeoutException) {
                throw ProviderClientException(
                    errorType = ErrorType.TIMEOUT,
                    message = "OpenAI socket timeout",
                    cause = exception,
                )
            } catch (exception: IOException) {
                throw ProviderClientException(
                    errorType = ErrorType.SERVER_ERROR,
                    message = "OpenAI transport error: ${exception.message}",
                    cause = exception,
                )
            } catch (exception: Exception) {
                if (isTimeoutLike(exception)) {
                    throw ProviderClientException(
                        errorType = ErrorType.TIMEOUT,
                        message = "OpenAI request timed out: ${exception.message ?: "timeout"}",
                        cause = exception,
                    )
                }
                throw ProviderClientException(
                    errorType = ErrorType.UNKNOWN,
                    message = "Unexpected OpenAI provider error: ${exception.message}",
                    cause = exception,
                )
            }
        }
    }

    override fun streamComplete(request: ProviderCompletionRequest): Flow<ProviderCompletionChunk> =
        channelFlow {
            val apiKey = resolveApiKey(config.apiKeyRef)
            if (apiKey.isBlank()) {
                close(
                    ProviderClientException(
                        errorType = ErrorType.AUTH_ERROR,
                        message = "OpenAI API key is not configured",
                    ),
                )
                return@channelFlow
            }

            launch(Dispatchers.IO) {
                var connection: HttpURLConnection? = null
                try {
                    val openAIRequest = openAIMapper.toOpenAIRequest(request.copy(stream = true))
                    val payload = openAIMapper.toJson(openAIRequest)
                    connection = buildStreamingConnection(apiKey)
                    connection.outputStream.use { output ->
                        output.write(payload.toByteArray(StandardCharsets.UTF_8))
                        output.flush()
                    }

                    val statusCode = connection.responseCode
                    if (statusCode !in 200..299) {
                        val errorBody = readResponseBody(connection.errorStream?.bufferedReader())
                        throw mapHttpStatusError(statusCode, errorBody)
                    }

                    val reader = connection.inputStream.bufferedReader(StandardCharsets.UTF_8)
                    streamSse(reader) { chunk ->
                        send(chunk)
                    }
                    close()
                } catch (exception: ProviderClientException) {
                    close(exception)
                } catch (exception: SocketTimeoutException) {
                    close(
                        ProviderClientException(
                            errorType = ErrorType.TIMEOUT,
                            message = "OpenAI stream socket timeout",
                            cause = exception,
                        ),
                    )
                } catch (exception: IOException) {
                    close(
                        ProviderClientException(
                            errorType = ErrorType.SERVER_ERROR,
                            message = "OpenAI stream transport error: ${exception.message}",
                            cause = exception,
                        ),
                    )
                } catch (exception: Exception) {
                    close(
                        ProviderClientException(
                            errorType = ErrorType.UNKNOWN,
                            message = "Unexpected OpenAI stream error: ${exception.message}",
                            cause = exception,
                        ),
                    )
                } finally {
                    connection?.disconnect()
                }
            }

            awaitClose { }
        }

    override fun estimateTokens(text: String): Int {
        if (text.isBlank()) {
            return 0
        }

        // Approximation aligned with OpenAI token segmentation rules.
        val regexEstimate = GPT_TOKEN_PATTERN.findAll(text).count()
        val byteEstimate = (text.toByteArray(StandardCharsets.UTF_8).size + 3) / 4
        return maxOf(1, maxOf(regexEstimate, byteEstimate))
    }

    @PreDestroy
    fun close() {
        runCatching { httpClient.close() }
            .onFailure { logger.warn("Failed to close OpenAI HTTP client", it) }
    }

    private fun resolveApiKey(apiKeyRef: String): String {
        val normalizedRef = apiKeyRef.trim()
        if (normalizedRef.isBlank()) {
            return ""
        }

        if (normalizedRef.startsWith(ENV_PREFIX)) {
            val envKey = normalizedRef.removePrefix(ENV_PREFIX).trim()
            if (envKey.isBlank()) {
                return ""
            }
            return System.getenv(envKey).orEmpty()
        }

        val envLookup = System.getenv(normalizedRef)
        if (!envLookup.isNullOrBlank()) {
            return envLookup
        }

        return normalizedRef
    }

    private fun mapHttpException(exception: HttpClientResponseException): ProviderClientException {
        val statusCode = exception.status.code
        val responseBody =
            runCatching { exception.response.getBody(String::class.java).orElse(null) }
                .getOrNull()
        return mapHttpStatusError(statusCode, responseBody, exception)
    }

    private fun mapHttpStatusError(
        statusCode: Int,
        responseBody: String?,
        cause: Throwable? = null,
    ): ProviderClientException {
        val openAIError = openAIMapper.toOpenAIError(responseBody)

        val errorType =
            when {
                statusCode == 401 -> ErrorType.AUTH_ERROR
                statusCode == 429 -> ErrorType.RATE_LIMIT
                statusCode in 500..503 -> ErrorType.SERVER_ERROR
                statusCode == 400 && openAIError?.code == OPENAI_CONTEXT_LENGTH_CODE ->
                    ErrorType.CONTEXT_LENGTH_EXCEEDED
                statusCode in 400..499 -> ErrorType.INVALID_REQUEST
                else -> ErrorType.UNKNOWN
            }

        val message = openAIError?.message ?: "OpenAI request failed with HTTP $statusCode"

        return ProviderClientException(
            errorType = errorType,
            message = message,
            statusCode = statusCode,
            providerErrorType = openAIError?.type,
            providerErrorCode = openAIError?.code,
            cause = cause,
        )
    }

    private suspend fun streamSse(
        reader: BufferedReader,
        emitChunk: suspend (ProviderCompletionChunk) -> Unit,
    ) {
        reader.use { bufferedReader ->
            val eventData = StringBuilder()
            while (true) {
                val line = bufferedReader.readLine() ?: break
                if (line.startsWith(SSE_DATA_PREFIX)) {
                    val payload = line.substringAfter(SSE_DATA_PREFIX).trimStart()
                    if (payload == SSE_DONE_MARKER) {
                        break
                    }
                    eventData.append(payload)
                } else if (line.isBlank()) {
                    if (eventData.isNotEmpty()) {
                        val chunk = openAIMapper.toProviderCompletionChunk(eventData.toString())
                        if (chunk != null) {
                            emitChunk(chunk)
                        }
                        eventData.setLength(0)
                    }
                }
            }
            if (eventData.isNotEmpty()) {
                val chunk = openAIMapper.toProviderCompletionChunk(eventData.toString())
                if (chunk != null) {
                    emitChunk(chunk)
                }
            }
        }
    }

    private fun buildStreamingConnection(apiKey: String): HttpURLConnection {
        val url = URL(URL(baseUrl), chatCompletionsPath)
        val connection = (url.openConnection() as HttpURLConnection)
        connection.requestMethod = "POST"
        connection.connectTimeout = config.requestTimeoutMs.toInt()
        connection.readTimeout = config.requestTimeoutMs.toInt()
        connection.doOutput = true
        connection.setRequestProperty(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
        connection.setRequestProperty(HttpHeaders.USER_AGENT, config.userAgent)
        connection.setRequestProperty(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
        connection.setRequestProperty(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM)
        connection.setRequestProperty(HttpHeaders.CACHE_CONTROL, "no-cache")
        connection.setRequestProperty("Connection", "keep-alive")
        return connection
    }

    private fun readResponseBody(reader: BufferedReader?): String? {
        if (reader == null) {
            return null
        }
        return reader.use { it.readText() }
    }

    private fun normalizePath(path: String): String {
        val normalized = path.trim()
        if (normalized.isBlank()) {
            return DEFAULT_CHAT_COMPLETIONS_PATH
        }
        return if (normalized.startsWith('/')) normalized else "/$normalized"
    }

    private fun isTimeoutLike(exception: Throwable): Boolean {
        var current: Throwable? = exception
        while (current != null) {
            if (
                current is TimeoutCancellationException ||
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
        private const val OPENAI_CONTEXT_LENGTH_CODE = "context_length_exceeded"
        private const val HEADER_REQUEST_TIMEOUT_MS = "X-Request-Timeout-Ms"
        private const val DEFAULT_CONNECT_TIMEOUT_MS = 10_000L
        private const val DEFAULT_CHAT_COMPLETIONS_PATH = "/v1/chat/completions"
        private const val SSE_DATA_PREFIX = "data:"
        private const val SSE_DONE_MARKER = "[DONE]"

        private val GPT_TOKEN_PATTERN =
            Regex(
                """'s|'t|'re|'ve|'m|'ll|'d| ?\p{L}+| ?\p{N}+| ?[^\s\p{L}\p{N}]+|\s+(?!\S)|\s+""",
            )
    }
}
