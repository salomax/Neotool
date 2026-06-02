package io.github.salomax.neotool.assistant.test.unit.provider

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.salomax.neotool.assistant.provider.error.ErrorType
import io.github.salomax.neotool.assistant.provider.error.ProviderClientException
import io.github.salomax.neotool.assistant.provider.gemini.GeminiMapper
import io.github.salomax.neotool.assistant.provider.gemini.GeminiProviderClient
import io.github.salomax.neotool.assistant.provider.gemini.GeminiProviderConfig
import io.github.salomax.neotool.assistant.provider.model.ProviderCompletionRequest
import io.github.salomax.neotool.assistant.provider.model.ProviderFinishReason
import io.github.salomax.neotool.assistant.provider.model.ProviderFunctionDefinition
import io.github.salomax.neotool.assistant.provider.model.ProviderMessage
import io.github.salomax.neotool.assistant.provider.model.ProviderMessageRole
import io.github.salomax.neotool.assistant.provider.model.ProviderTool
import io.github.salomax.neotool.assistant.provider.model.ResponseFormat
import io.github.salomax.neotool.assistant.provider.model.ResponseFormatType
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

class GeminiProviderClientTest {
    private val objectMapper = ObjectMapper()
    private val mapper = GeminiMapper(objectMapper)

    @Test
    fun `toGeminiRequest maps system prompt to top-level systemInstruction and not as user message`() {
        val request =
            ProviderCompletionRequest(
                model = "gemini-2.5-flash",
                messages =
                    listOf(
                        ProviderMessage(role = ProviderMessageRole.SYSTEM, content = "You are a concise assistant."),
                        ProviderMessage(role = ProviderMessageRole.USER, content = "Return account summary."),
                    ),
                temperature = 0.1,
                maxTokens = 256,
                tools =
                    listOf(
                        ProviderTool(
                            function =
                                ProviderFunctionDefinition(
                                    name = "get_balance",
                                    description = "Get account balance",
                                    parameters =
                                        mapOf(
                                            "type" to "object",
                                            "properties" to mapOf("accountId" to mapOf("type" to "string")),
                                            "required" to listOf("accountId"),
                                        ),
                                ),
                        ),
                    ),
                responseFormat = ResponseFormat(type = ResponseFormatType.JSON_OBJECT),
            )

        val payload = mapper.toGeminiRequest(request)

        @Suppress("UNCHECKED_CAST")
        val systemInstruction = payload["systemInstruction"] as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val systemParts = systemInstruction["parts"] as List<Map<String, Any?>>
        assertThat(systemParts.first()["text"]).isEqualTo("You are a concise assistant.")

        @Suppress("UNCHECKED_CAST")
        val contents = payload["contents"] as List<Map<String, Any?>>
        assertThat(contents).hasSize(1)
        assertThat(contents.first()["role"]).isEqualTo("user")

        @Suppress("UNCHECKED_CAST")
        val generationConfig = payload["generationConfig"] as Map<String, Any?>
        assertThat(generationConfig["responseMimeType"]).isEqualTo("application/json")
        assertThat(generationConfig["maxOutputTokens"]).isEqualTo(256)
    }

    @Test
    fun `toProviderCompletionResponse parses text usage and tool calls`() {
        val responseBody =
            """
            {
              "responseId": "gemini-resp-1",
              "candidates": [
                {
                  "finishReason": "STOP",
                  "content": {
                    "parts": [
                      { "text": "Checking your balance..." },
                      { "functionCall": { "name": "get_balance", "args": { "accountId": "acc-1" } } }
                    ]
                  }
                }
              ],
              "usageMetadata": {
                "promptTokenCount": 10,
                "candidatesTokenCount": 5,
                "totalTokenCount": 15
              }
            }
            """.trimIndent()

        val parsed = mapper.toProviderCompletionResponse(responseBody)

        assertThat(parsed.id).isEqualTo("gemini-resp-1")
        assertThat(parsed.content).isEqualTo("Checking your balance...")
        assertThat(parsed.finishReason).isEqualTo(ProviderFinishReason.TOOL_CALLS)
        assertThat(parsed.usage).isNotNull
        assertThat(parsed.usage!!.totalTokens).isEqualTo(15)
        assertThat(parsed.toolCalls).hasSize(1)
        assertThat(parsed.toolCalls.first().function.name).isEqualTo("get_balance")
        assertThat(parsed.toolCalls.first().function.arguments).contains("acc-1")
    }

    @Test
    fun `complete maps HTTP 429 RESOURCE_EXHAUSTED to RATE_LIMIT`() {
        val responseBody =
            """
            {
              "error": {
                "code": 429,
                "status": "RESOURCE_EXHAUSTED",
                "message": "Quota exceeded"
              }
            }
            """.trimIndent()

        withGeminiServer(statusCode = 429, responseBody = responseBody) { baseUrl, captured ->
            val client = newClient(baseUrl)
            try {
                assertThatThrownBy {
                    runBlocking { client.complete(minimalRequest()) }
                }
                    .isInstanceOfSatisfying(ProviderClientException::class.java) { exception ->
                        assertThat(exception.errorType).isEqualTo(ErrorType.RATE_LIMIT)
                        assertThat(exception.providerErrorType).isEqualTo("RESOURCE_EXHAUSTED")
                    }

                assertThat(captured.apiKeyHeader.get()).isEqualTo("test-api-key")
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `complete maps context limit errors to CONTEXT_LENGTH_EXCEEDED`() {
        val responseBody =
            """
            {
              "error": {
                "code": 400,
                "status": "INVALID_ARGUMENT",
                "message": "The context window token limit was exceeded"
              }
            }
            """.trimIndent()

        withGeminiServer(statusCode = 400, responseBody = responseBody) { baseUrl, _ ->
            val client = newClient(baseUrl)
            try {
                assertThatThrownBy {
                    runBlocking { client.complete(minimalRequest()) }
                }
                    .isInstanceOfSatisfying(ProviderClientException::class.java) { exception ->
                        assertThat(exception.errorType).isEqualTo(ErrorType.CONTEXT_LENGTH_EXCEEDED)
                    }
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `streamComplete emits completion content and terminal usage chunk`() {
        val responseBody =
            """
            {
              "responseId": "gemini-stream-1",
              "candidates": [
                {
                  "finishReason": "STOP",
                  "content": {
                    "parts": [{ "text": "Hello from Gemini" }]
                  }
                }
              ],
              "usageMetadata": {
                "promptTokenCount": 2,
                "candidatesTokenCount": 3,
                "totalTokenCount": 5
              }
            }
            """.trimIndent()

        withGeminiServer(statusCode = 200, responseBody = responseBody) { baseUrl, _ ->
            val client = newClient(baseUrl)
            try {
                val chunks = runBlocking { client.streamComplete(minimalRequest()).toList() }
                assertThat(chunks).hasSize(2)
                assertThat(chunks[0].delta).isEqualTo("Hello from Gemini")
                assertThat(chunks[0].finishReason).isNull()
                assertThat(chunks[1].finishReason).isEqualTo(ProviderFinishReason.STOP)
                assertThat(chunks[1].usage).isNotNull
                assertThat(chunks[1].usage!!.totalTokens).isEqualTo(5)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `complete maps delayed read timeout to TIMEOUT`() {
        val responseBody =
            """
            {
              "responseId": "gemini-timeout-1",
              "candidates": [
                {
                  "finishReason": "STOP",
                  "content": {
                    "parts": [{ "text": "late response" }]
                  }
                }
              ]
            }
            """.trimIndent()

        withGeminiServer(statusCode = 200, responseBody = responseBody, responseDelayMs = 300L) { baseUrl, _ ->
            val client =
                GeminiProviderClient(
                    config =
                        GeminiProviderConfig(
                            baseUrl = baseUrl,
                            apiKey = "test-api-key",
                            generateContentPath = "/v1beta/models/{model}:generateContent",
                            requestTimeoutMs = 100,
                            userAgent = "assistant-test/1.0",
                        ),
                    geminiMapper = mapper,
                )
            try {
                assertThatThrownBy {
                    runBlocking { client.complete(minimalRequest()) }
                }
                    .isInstanceOfSatisfying(ProviderClientException::class.java) { exception ->
                        assertThat(exception.errorType).isEqualTo(ErrorType.TIMEOUT)
                    }
            } finally {
                client.close()
            }
        }
    }

    private fun newClient(baseUrl: String): GeminiProviderClient {
        return GeminiProviderClient(
            config =
                GeminiProviderConfig(
                    baseUrl = baseUrl,
                    apiKey = "test-api-key",
                    generateContentPath = "/v1beta/models/{model}:generateContent",
                    requestTimeoutMs = 5_000,
                    userAgent = "assistant-test/1.0",
                ),
            geminiMapper = mapper,
        )
    }

    private fun minimalRequest(): ProviderCompletionRequest {
        return ProviderCompletionRequest(
            model = "gemini-2.5-flash",
            messages = listOf(ProviderMessage(role = ProviderMessageRole.USER, content = "hello")),
            temperature = 0.0,
            maxTokens = 32,
            stream = false,
        )
    }

    private fun withGeminiServer(
        statusCode: Int,
        responseBody: String,
        responseDelayMs: Long = 0L,
        block: (baseUrl: String, captured: CapturedRequest) -> Unit,
    ) {
        val captured = CapturedRequest()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        server.createContext("/v1beta/models/gemini-2.5-flash:generateContent") { exchange ->
            handle(exchange, statusCode, responseBody, captured, responseDelayMs)
        }

        server.start()
        val baseUrl = "http://127.0.0.1:${server.address.port}"

        try {
            block(baseUrl, captured)
        } finally {
            server.stop(0)
        }
    }

    private fun handle(
        exchange: HttpExchange,
        statusCode: Int,
        responseBody: String,
        captured: CapturedRequest,
        responseDelayMs: Long,
    ) {
        captured.apiKeyHeader.set(exchange.requestHeaders.getFirst("x-goog-api-key"))
        captured.userAgent.set(exchange.requestHeaders.getFirst("User-Agent"))
        if (responseDelayMs > 0) {
            Thread.sleep(responseDelayMs)
        }

        val responseBytes = responseBody.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(statusCode, responseBytes.size.toLong())
        exchange.responseBody.use { it.write(responseBytes) }
    }

    private class CapturedRequest {
        val apiKeyHeader: AtomicReference<String?> = AtomicReference(null)
        val userAgent: AtomicReference<String?> = AtomicReference(null)
    }
}
