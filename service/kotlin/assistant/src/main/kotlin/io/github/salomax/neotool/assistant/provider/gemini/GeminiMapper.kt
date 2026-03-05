package io.github.salomax.neotool.assistant.provider.gemini

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.assistant.provider.error.ErrorType
import io.github.salomax.neotool.assistant.provider.error.ProviderClientException
import io.github.salomax.neotool.assistant.provider.model.ProviderCompletionRequest
import io.github.salomax.neotool.assistant.provider.model.ProviderCompletionResponse
import io.github.salomax.neotool.assistant.provider.model.ProviderFinishReason
import io.github.salomax.neotool.assistant.provider.model.ProviderFunctionCall
import io.github.salomax.neotool.assistant.provider.model.ProviderMessage
import io.github.salomax.neotool.assistant.provider.model.ProviderMessageRole
import io.github.salomax.neotool.assistant.provider.model.ProviderTokenUsage
import io.github.salomax.neotool.assistant.provider.model.ProviderToolCall
import io.github.salomax.neotool.assistant.provider.model.ProviderToolType
import io.github.salomax.neotool.assistant.provider.model.ResponseFormatType
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
class GeminiMapper(
    private val objectMapper: ObjectMapper,
) {
    fun toGeminiRequest(request: ProviderCompletionRequest): Map<String, Any?> {
        val systemMessages =
            request.messages
                .filter { it.role == ProviderMessageRole.SYSTEM }
                .mapNotNull { it.content?.trim()?.takeIf { text -> text.isNotEmpty() } }

        val contents =
            request.messages
                .asSequence()
                .filter { it.role != ProviderMessageRole.SYSTEM }
                .mapNotNull { toGeminiContent(it) }
                .toMutableList()

        if (contents.isEmpty()) {
            contents.add(
                mapOf(
                    "role" to "user",
                    "parts" to listOf(mapOf("text" to "")),
                ),
            )
        }

        val payload = linkedMapOf<String, Any?>("contents" to contents)

        if (systemMessages.isNotEmpty()) {
            payload["systemInstruction"] =
                mapOf(
                    "parts" to listOf(mapOf("text" to systemMessages.joinToString(separator = "\n\n"))),
                )
        }

        val generationConfig = linkedMapOf<String, Any?>()
        request.temperature?.let { generationConfig["temperature"] = it }
        request.maxTokens?.let { generationConfig["maxOutputTokens"] = it }
        request.responseFormat?.let { format ->
            when (format.type) {
                ResponseFormatType.TEXT -> Unit
                ResponseFormatType.JSON_OBJECT -> generationConfig["responseMimeType"] = "application/json"
                ResponseFormatType.JSON_SCHEMA -> {
                    generationConfig["responseMimeType"] = "application/json"
                    format.jsonSchema?.let { schema -> generationConfig["responseSchema"] = schema }
                }
            }
        }
        if (generationConfig.isNotEmpty()) {
            payload["generationConfig"] = generationConfig
        }

        request.tools
            ?.takeIf { it.isNotEmpty() }
            ?.let { tools ->
                payload["tools"] =
                    listOf(
                        mapOf(
                            "functionDeclarations" to
                                tools.map { tool ->
                                    buildMap<String, Any?> {
                                        put("name", tool.function.name)
                                        tool.function.description?.let { put("description", it) }
                                        put("parameters", tool.function.parameters ?: DEFAULT_FUNCTION_PARAMETERS)
                                    }
                                },
                        ),
                    )
            }

        return payload
    }

    fun toJson(value: Any): String = objectMapper.writeValueAsString(value)

    fun toProviderCompletionResponse(responseBody: String): ProviderCompletionResponse {
        val root = parseJson(responseBody)

        val firstCandidate =
            root.firstCandidateOrNull()
                ?: throw ProviderClientException(
                    errorType = ErrorType.PROVIDER_RESPONSE_INVALID,
                    message = "Gemini response missing 'candidates[0]'",
                )

        val (textContent, toolCalls) = extractContentAndToolCalls(firstCandidate.path("content").path("parts"))
        val finishReason =
            when {
                toolCalls.isNotEmpty() -> ProviderFinishReason.TOOL_CALLS
                else -> mapFinishReason(firstCandidate.path("finishReason").asText(null))
            }

        val usage = extractUsage(root)
        val id =
            root.path("responseId").asText(null)
                ?: root.path("id").asText(null)
                ?: "gemini-${UUID.randomUUID()}"

        return ProviderCompletionResponse(
            id = id,
            content = textContent.takeIf { it.isNotBlank() },
            finishReason = finishReason,
            usage = usage,
            toolCalls = toolCalls,
        )
    }

    fun toGeminiError(responseBody: String?): GeminiError? {
        if (responseBody.isNullOrBlank()) {
            return null
        }

        val root =
            try {
                objectMapper.readTree(responseBody)
            } catch (_: Exception) {
                return null
            }

        val errorNode = root.path("error")
        if (!errorNode.isObject) {
            return null
        }

        return GeminiError(
            code = errorNode.path("code").asInt(0).takeIf { it > 0 },
            status = errorNode.path("status").asText(null),
            message = errorNode.path("message").asText(null),
        )
    }

    private fun toGeminiContent(message: ProviderMessage): Map<String, Any?>? {
        val parts = mutableListOf<Map<String, Any?>>()

        message.content
            ?.takeIf { it.isNotBlank() }
            ?.let { text -> parts.add(mapOf("text" to text)) }

        if (message.role == ProviderMessageRole.ASSISTANT && message.toolCalls.isNotEmpty()) {
            parts.addAll(
                message.toolCalls.map { toolCall ->
                    mapOf(
                        "functionCall" to
                            mapOf(
                                "name" to toolCall.function.name,
                                "args" to parseJsonObject(toolCall.function.arguments),
                            ),
                    )
                },
            )
        }

        if (message.role == ProviderMessageRole.TOOL) {
            val toolName = message.name ?: "tool"
            parts.clear()
            parts.add(
                mapOf(
                    "functionResponse" to
                        mapOf(
                            "name" to toolName,
                            "response" to parseToolResponse(message.content),
                        ),
                ),
            )
        }

        if (parts.isEmpty()) {
            return null
        }

        return mapOf("role" to mapRole(message.role), "parts" to parts)
    }

    private fun mapRole(role: ProviderMessageRole): String {
        return when (role) {
            ProviderMessageRole.USER -> "user"
            ProviderMessageRole.ASSISTANT -> "model"
            ProviderMessageRole.TOOL -> "user"
            ProviderMessageRole.SYSTEM -> "user"
        }
    }

    private fun parseToolResponse(content: String?): Any {
        val raw = content?.trim().orEmpty()
        if (raw.isEmpty()) {
            return mapOf("result" to "")
        }

        return try {
            val node = objectMapper.readTree(raw)
            when {
                node.isObject || node.isArray -> objectMapper.convertValue(node, Any::class.java)
                else -> mapOf("result" to node.asText(raw))
            }
        } catch (_: Exception) {
            mapOf("result" to raw)
        }
    }

    private fun parseJsonObject(rawJson: String): Map<String, Any?> {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) {
            return emptyMap()
        }

        return try {
            val node = objectMapper.readTree(trimmed)
            if (node.isObject) {
                objectMapper.convertValue(node, object : TypeReference<Map<String, Any?>>() {})
            } else {
                emptyMap()
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun extractContentAndToolCalls(partsNode: JsonNode): Pair<String, List<ProviderToolCall>> {
        if (!partsNode.isArray) {
            return "" to emptyList()
        }

        val textParts = mutableListOf<String>()
        val toolCalls = mutableListOf<ProviderToolCall>()

        for (index in 0 until partsNode.size()) {
            val part = partsNode[index]

            part.path("text")
                .asText(null)
                ?.takeIf { it.isNotBlank() }
                ?.let(textParts::add)

            val functionCallNode = part.path("functionCall")
            if (functionCallNode.isObject) {
                val functionName = functionCallNode.path("name").asText(null) ?: continue
                val argsNode = functionCallNode.path("args")
                val arguments =
                    when {
                        argsNode.isMissingNode || argsNode.isNull -> "{}"
                        argsNode.isTextual -> argsNode.asText()
                        else -> objectMapper.writeValueAsString(argsNode)
                    }

                toolCalls.add(
                    ProviderToolCall(
                        id = "gemini_tool_call_$index",
                        type = ProviderToolType.FUNCTION,
                        function = ProviderFunctionCall(name = functionName, arguments = arguments),
                    ),
                )
            }
        }

        return textParts.joinToString(separator = "") to toolCalls
    }

    private fun extractUsage(root: JsonNode): ProviderTokenUsage? {
        val usageMetadata = root.path("usageMetadata")
        if (usageMetadata.isObject) {
            val prompt = usageMetadata.path("promptTokenCount").asInt(0)
            val completion = usageMetadata.path("candidatesTokenCount").asInt(0)
            val total = usageMetadata.path("totalTokenCount").asInt(prompt + completion)
            return ProviderTokenUsage(promptTokens = prompt, completionTokens = completion, totalTokens = total)
        }

        val usage = root.path("usage")
        if (usage.isObject) {
            val prompt = usage.path("input_tokens").asInt(0)
            val completion = usage.path("output_tokens").asInt(0)
            val total = usage.path("total_tokens").asInt(prompt + completion)
            return ProviderTokenUsage(promptTokens = prompt, completionTokens = completion, totalTokens = total)
        }

        return null
    }

    private fun mapFinishReason(value: String?): ProviderFinishReason? {
        return when (value?.trim()?.uppercase()) {
            null, "" -> null
            "STOP", "END_TURN" -> ProviderFinishReason.STOP
            "MAX_TOKENS", "MAX_OUTPUT_TOKENS" -> ProviderFinishReason.LENGTH
            "TOOL_CALLS", "TOOL_USE", "FUNCTION_CALL" -> ProviderFinishReason.TOOL_CALLS
            "SAFETY", "RECITATION", "CONTENT_FILTER" -> ProviderFinishReason.CONTENT_FILTER
            "ERROR" -> ProviderFinishReason.ERROR
            else -> ProviderFinishReason.UNKNOWN
        }
    }

    private fun parseJson(payload: String): JsonNode {
        return try {
            objectMapper.readTree(payload)
        } catch (exception: Exception) {
            throw ProviderClientException(
                errorType = ErrorType.PROVIDER_RESPONSE_INVALID,
                message = "Invalid JSON response from Gemini",
                cause = exception,
            )
        }
    }

    private fun JsonNode.firstCandidateOrNull(): JsonNode? {
        val candidates = path("candidates")
        if (!candidates.isArray || candidates.isEmpty) {
            return null
        }
        return candidates[0]
    }

    data class GeminiError(
        val code: Int?,
        val status: String?,
        val message: String?,
    )

    companion object {
        private val DEFAULT_FUNCTION_PARAMETERS =
            mapOf<String, Any?>(
                "type" to "object",
                "properties" to emptyMap<String, Any?>(),
                "required" to emptyList<String>(),
            )
    }
}
