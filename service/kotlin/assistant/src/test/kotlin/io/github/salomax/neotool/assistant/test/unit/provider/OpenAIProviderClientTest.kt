package io.github.salomax.neotool.assistant.test.unit.provider

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.salomax.neotool.assistant.entity.ToolEntity
import io.github.salomax.neotool.assistant.provider.error.ErrorType
import io.github.salomax.neotool.assistant.provider.error.ProviderClientException
import io.github.salomax.neotool.assistant.provider.model.ProviderCompletionRequest
import io.github.salomax.neotool.assistant.provider.model.ProviderFinishReason
import io.github.salomax.neotool.assistant.provider.model.ProviderFunctionCall
import io.github.salomax.neotool.assistant.provider.model.ProviderFunctionDefinition
import io.github.salomax.neotool.assistant.provider.model.ProviderMessage
import io.github.salomax.neotool.assistant.provider.model.ProviderMessageRole
import io.github.salomax.neotool.assistant.provider.model.ProviderTool
import io.github.salomax.neotool.assistant.provider.model.ProviderToolCall
import io.github.salomax.neotool.assistant.provider.model.ResponseFormat
import io.github.salomax.neotool.assistant.provider.model.ResponseFormatType
import io.github.salomax.neotool.assistant.provider.openai.OpenAIMapper
import io.github.salomax.neotool.assistant.provider.openai.OpenAIProviderClient
import io.github.salomax.neotool.assistant.provider.openai.OpenAIProviderConfig
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

class OpenAIProviderClientTest {
    private val objectMapper = ObjectMapper()
    private val mapper = OpenAIMapper(objectMapper)

    @Test
    fun `toolToOpenAIFunction maps tool metadata to function definition`() {
        val tool =
            ToolEntity(
                id = UUID.randomUUID(),
                name = "get_balance",
                description = "Get account balance",
                category = "accounts",
                inputSchema =
                    mapOf(
                        "type" to "object",
                        "properties" to mapOf("accountId" to mapOf("type" to "string")),
                        "required" to listOf("accountId"),
                    ),
                outputSchema = mapOf("type" to "object"),
                executorService = "service",
                executorEndpoint = "/tool/get-balance",
                timeoutMs = 1000,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )

        val function = mapper.toolToOpenAIFunction(tool)

        assertThat(function.name).isEqualTo("get_balance")
        assertThat(function.description).isEqualTo("Get account balance")
        assertThat(function.parameters["type"]).isEqualTo("object")
    }

    @Test
    fun `openAIToolCallToInput parses JSON object arguments`() {
        val input =
            mapper.openAIToolCallToInput(
                ProviderToolCall(
                    id = "call_1",
                    function = ProviderFunctionCall(name = "get_balance", arguments = "{\"accountId\":\"acc-1\"}"),
                ),
            )

        assertThat(input).containsEntry("accountId", "acc-1")
    }

    @Test
    fun `toOpenAIRequest maps messages tools and JSON mode`() {
        val request =
            ProviderCompletionRequest(
                model = "gpt-4o-mini",
                messages =
                    listOf(
                        ProviderMessage(role = ProviderMessageRole.SYSTEM, content = "You are a finance assistant."),
                        ProviderMessage(
                            role = ProviderMessageRole.USER,
                            content = "Summarize my spending by category.",
                        ),
                    ),
                temperature = 0.2,
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
                                            "properties" to
                                                mapOf(
                                                    "accountId" to mapOf("type" to "string"),
                                                ),
                                            "required" to listOf("accountId"),
                                        ),
                                ),
                        ),
                    ),
                responseFormat = ResponseFormat(type = ResponseFormatType.JSON_OBJECT),
                stream = false,
            )

        val payload = mapper.toOpenAIRequest(request)

        assertThat(payload["model"]).isEqualTo("gpt-4o-mini")
        assertThat(payload["temperature"]).isEqualTo(0.2)
        assertThat(payload["max_tokens"]).isEqualTo(256)
        assertThat(payload["response_format"]).isEqualTo(mapOf("type" to "json_object"))

        @Suppress("UNCHECKED_CAST")
        val messages = payload["messages"] as List<Map<String, Any?>>
        assertThat(messages).hasSize(2)
        assertThat(messages[0]["role"]).isEqualTo("system")
        assertThat(messages[0]["content"]).isEqualTo("You are a finance assistant.")
        assertThat(messages[1]["role"]).isEqualTo("user")
        assertThat(messages[1]["content"]).isEqualTo("Summarize my spending by category.")

        @Suppress("UNCHECKED_CAST")
        val tools = payload["tools"] as List<Map<String, Any?>>
        assertThat(tools).hasSize(1)
        assertThat(tools[0]["type"]).isEqualTo("function")

        @Suppress("UNCHECKED_CAST")
        val function = tools[0]["function"] as Map<String, Any?>
        assertThat(function["name"]).isEqualTo("get_balance")
        assertThat(function["description"]).isEqualTo("Get account balance")
    }

    @Test
    fun `toProviderCompletionResponse parses content finishReason usage and toolCalls`() {
        val responseBody =
            """
            {
              "id": "chatcmpl_123",
              "choices": [
                {
                  "finish_reason": "tool_calls",
                  "message": {
                    "content": "Looking up balance",
                    "tool_calls": [
                      {
                        "id": "call_1",
                        "type": "function",
                        "function": {
                          "name": "get_balance",
                          "arguments": "{\"accountId\":\"acc-1\"}"
                        }
                      }
                    ]
                  }
                }
              ],
              "usage": {
                "prompt_tokens": 12,
                "completion_tokens": 7,
                "total_tokens": 19
              }
            }
            """.trimIndent()

        val parsed = mapper.toProviderCompletionResponse(responseBody)

        assertThat(parsed.id).isEqualTo("chatcmpl_123")
        assertThat(parsed.content).isEqualTo("Looking up balance")
        assertThat(parsed.finishReason).isEqualTo(ProviderFinishReason.TOOL_CALLS)
        assertThat(parsed.usage).isNotNull
        assertThat(parsed.usage!!.promptTokens).isEqualTo(12)
        assertThat(parsed.usage!!.completionTokens).isEqualTo(7)
        assertThat(parsed.usage!!.totalTokens).isEqualTo(19)

        assertThat(parsed.toolCalls).hasSize(1)
        val toolCall = parsed.toolCalls.first()
        assertThat(toolCall.id).isEqualTo("call_1")
        assertThat(toolCall.function.name).isEqualTo("get_balance")
        assertThat(toolCall.function.arguments).isEqualTo("{\"accountId\":\"acc-1\"}")
    }

    @Test
    fun `complete maps HTTP 429 to RATE_LIMIT`() {
        val responseBody =
            """
            {
              "error": {
                "message": "Rate limit reached",
                "type": "rate_limit_error",
                "code": "rate_limit_exceeded"
              }
            }
            """.trimIndent()

        withOpenAIServer(statusCode = 429, responseBody = responseBody) { baseUrl, captured ->
            val client = newClient(baseUrl)

            try {
                assertThatThrownBy {
                    runBlocking { client.complete(minimalRequest()) }
                }
                    .isInstanceOfSatisfying(ProviderClientException::class.java) { exception ->
                        assertThat(exception.errorType).isEqualTo(ErrorType.RATE_LIMIT)
                        assertThat(exception.statusCode).isEqualTo(429)
                        assertThat(exception.providerErrorType).isEqualTo("rate_limit_error")
                    }

                assertThat(captured.authorization.get()).isEqualTo("Bearer test-api-key")
                assertThat(captured.userAgent.get()).isEqualTo("assistant-test/1.0")
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `complete maps HTTP 500 to SERVER_ERROR`() {
        val responseBody =
            """
            {
              "error": {
                "message": "Upstream unavailable",
                "type": "server_error",
                "code": "internal_error"
              }
            }
            """.trimIndent()

        withOpenAIServer(statusCode = 500, responseBody = responseBody) { baseUrl, _ ->
            val client = newClient(baseUrl)

            try {
                assertThatThrownBy {
                    runBlocking { client.complete(minimalRequest()) }
                }
                    .isInstanceOfSatisfying(ProviderClientException::class.java) { exception ->
                        assertThat(exception.errorType).isEqualTo(ErrorType.SERVER_ERROR)
                        assertThat(exception.statusCode).isEqualTo(500)
                    }
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `streamComplete parses SSE delta chunks and terminal usage`() {
        val chunks =
            listOf(
                """data: {"id":"chatcmpl_stream","choices":[{"delta":{"content":"Hel"},"finish_reason":null}]}""",
                """data: {"id":"chatcmpl_stream","choices":[{"delta":{"content":"lo"},"finish_reason":"stop"}],""" +
                    """ "usage":{"prompt_tokens":5,"completion_tokens":2,"total_tokens":7}}""",
                "data: [DONE]",
            ).joinToString(separator = "\n\n", postfix = "\n\n")

        withOpenAIServer(statusCode = 200, responseBody = chunks, contentType = "text/event-stream") { baseUrl, _ ->
            val client = newClient(baseUrl)
            try {
                val collected = runBlocking { client.streamComplete(minimalRequest()).toList() }
                assertThat(collected).hasSize(2)
                assertThat(collected[0].delta).isEqualTo("Hel")
                assertThat(collected[0].finishReason).isNull()
                assertThat(collected[1].delta).isEqualTo("lo")
                assertThat(collected[1].finishReason).isEqualTo(ProviderFinishReason.STOP)
                assertThat(collected[1].usage).isNotNull
                assertThat(collected[1].usage!!.totalTokens).isEqualTo(7)
            } finally {
                client.close()
            }
        }
    }

    private fun newClient(baseUrl: String): OpenAIProviderClient {
        return OpenAIProviderClient(
            config =
                OpenAIProviderConfig(
                    baseUrl = baseUrl,
                    apiKeyRef = "test-api-key",
                    chatCompletionsPath = "/v1/chat/completions",
                    requestTimeoutMs = 5_000,
                    userAgent = "assistant-test/1.0",
                ),
            openAIMapper = mapper,
        )
    }

    private fun minimalRequest(): ProviderCompletionRequest {
        return ProviderCompletionRequest(
            model = "gpt-4o-mini",
            messages = listOf(ProviderMessage(role = ProviderMessageRole.USER, content = "hello")),
            temperature = 0.0,
            maxTokens = 32,
            stream = false,
        )
    }

    private fun withOpenAIServer(
        statusCode: Int,
        responseBody: String,
        contentType: String = "application/json",
        block: (baseUrl: String, captured: CapturedRequest) -> Unit,
    ) {
        val captured = CapturedRequest()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        server.createContext("/v1/chat/completions") { exchange ->
            handle(exchange, statusCode, responseBody, contentType, captured)
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
        contentType: String,
        captured: CapturedRequest,
    ) {
        captured.authorization.set(exchange.requestHeaders.getFirst("Authorization"))
        captured.userAgent.set(exchange.requestHeaders.getFirst("User-Agent"))

        val responseBytes = responseBody.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", contentType)
        if (contentType == "text/event-stream") {
            exchange.sendResponseHeaders(statusCode, 0)
            exchange.responseBody.use {
                it.write(responseBytes)
                it.flush()
            }
        } else {
            exchange.sendResponseHeaders(statusCode, responseBytes.size.toLong())
            exchange.responseBody.use { it.write(responseBytes) }
        }
    }

    private class CapturedRequest {
        val authorization: AtomicReference<String?> = AtomicReference(null)
        val userAgent: AtomicReference<String?> = AtomicReference(null)
    }
}
