package io.github.salomax.neotool.assistant.provider.openai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.assistant.entity.ToolEntity
import io.github.salomax.neotool.assistant.provider.error.ErrorType
import io.github.salomax.neotool.assistant.provider.error.ProviderClientException
import io.github.salomax.neotool.assistant.provider.model.ProviderCompletionChunk
import io.github.salomax.neotool.assistant.provider.model.ProviderCompletionRequest
import io.github.salomax.neotool.assistant.provider.model.ProviderCompletionResponse
import io.github.salomax.neotool.assistant.provider.model.ProviderFinishReason
import io.github.salomax.neotool.assistant.provider.model.ProviderFunctionCall
import io.github.salomax.neotool.assistant.provider.model.ProviderMessage
import io.github.salomax.neotool.assistant.provider.model.ProviderMessageRole
import io.github.salomax.neotool.assistant.provider.model.ProviderTokenUsage
import io.github.salomax.neotool.assistant.provider.model.ProviderTool
import io.github.salomax.neotool.assistant.provider.model.ProviderToolCall
import io.github.salomax.neotool.assistant.provider.model.ProviderToolType
import io.github.salomax.neotool.assistant.provider.model.ResponseFormat
import io.github.salomax.neotool.assistant.provider.model.ResponseFormatType
import io.github.salomax.neotool.assistant.tool.JsonObject
import jakarta.inject.Singleton

@Singleton
class OpenAIMapper(
    private val objectMapper: ObjectMapper,
) {
    fun toJson(value: Any): String {
        return objectMapper.writeValueAsString(value)
    }

    fun toolToOpenAIFunction(tool: ToolEntity): OpenAIFunction {
        val name = tool.name.trim()
        require(name.isNotEmpty()) { "Tool name is required" }

        val description = tool.description.trim().takeIf { it.isNotEmpty() }
        val parameters = tool.inputSchema.takeIf { it.isNotEmpty() } ?: DEFAULT_FUNCTION_PARAMETERS

        return OpenAIFunction(
            name = name,
            description = description,
            parameters = parameters,
        )
    }

    fun openAIToolCallToInput(toolCall: ProviderToolCall): JsonObject {
        val rawArguments = toolCall.function.arguments.trim()
        if (rawArguments.isEmpty()) {
            return emptyMap()
        }

        val root =
            try {
                objectMapper.readTree(rawArguments)
            } catch (exception: Exception) {
                throw IllegalArgumentException(
                    "Invalid JSON arguments for tool call '${toolCall.function.name}'",
                    exception,
                )
            }

        require(root.isObject) {
            "Tool call arguments for '${toolCall.function.name}' must be a JSON object"
        }

        return objectMapper.convertValue(root, JsonObject::class.java) ?: emptyMap()
    }

    fun toOpenAIRequest(request: ProviderCompletionRequest): Map<String, Any?> {
        val payload =
            linkedMapOf<String, Any?>(
                "model" to request.model,
                "messages" to request.messages.map { toOpenAIMessage(it) },
                "stream" to request.stream,
            )

        request.temperature?.let { payload["temperature"] = it }
        request.maxTokens?.let { payload["max_tokens"] = it }
        request.tools?.takeIf { it.isNotEmpty() }?.let { payload["tools"] = it.map { tool -> toOpenAITool(tool) } }
        request.responseFormat?.let { format ->
            toOpenAIResponseFormat(format)?.let { payload["response_format"] = it }
        }
        if (request.stream) {
            payload["stream_options"] = mapOf("include_usage" to true)
        }

        return payload
    }

    fun toProviderCompletionChunk(eventData: String): ProviderCompletionChunk? {
        val root = parseJson(eventData)
        val usage = extractUsage(root.path("usage"))
        val firstChoice = root.firstChoiceOrNull()

        if (firstChoice == null) {
            return if (usage != null) {
                ProviderCompletionChunk(
                    delta = "",
                    finishReason = null,
                    usage = usage,
                )
            } else {
                null
            }
        }

        val deltaNode = firstChoice.path("delta")
        val delta = extractContent(deltaNode.get("content")).orEmpty()
        val finishReason = ProviderFinishReason.fromValue(firstChoice.path("finish_reason").asText(null))

        if (delta.isEmpty() && finishReason == null && usage == null) {
            return null
        }

        return ProviderCompletionChunk(
            delta = delta,
            finishReason = finishReason,
            usage = usage,
        )
    }

    fun toProviderCompletionResponse(responseBody: String): ProviderCompletionResponse {
        val root = parseJson(responseBody)
        val id =
            root.path("id").asText(null)
                ?: throw ProviderClientException(
                    errorType = ErrorType.PROVIDER_RESPONSE_INVALID,
                    message = "OpenAI response missing 'id'",
                )

        val firstChoice =
            root.firstChoiceOrNull()
                ?: throw ProviderClientException(
                    errorType = ErrorType.PROVIDER_RESPONSE_INVALID,
                    message = "OpenAI response missing 'choices[0]'",
                )

        val messageNode = firstChoice.path("message")
        val content = extractContent(messageNode.get("content"))
        val finishReason = ProviderFinishReason.fromValue(firstChoice.path("finish_reason").asText(null))
        val usage = extractUsage(root.path("usage"))
        val toolCalls = extractToolCalls(messageNode.path("tool_calls"))

        return ProviderCompletionResponse(
            id = id,
            content = content,
            finishReason = finishReason,
            usage = usage,
            toolCalls = toolCalls,
        )
    }

    fun toOpenAIError(responseBody: String?): OpenAIError? {
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

        return OpenAIError(
            message = errorNode.path("message").asText(null),
            type = errorNode.path("type").asText(null),
            code = errorNode.path("code").asText(null),
        )
    }

    private fun parseJson(payload: String): JsonNode {
        return try {
            objectMapper.readTree(payload)
        } catch (exception: Exception) {
            throw ProviderClientException(
                errorType = ErrorType.PROVIDER_RESPONSE_INVALID,
                message = "Invalid JSON response from OpenAI",
                cause = exception,
            )
        }
    }

    private fun toOpenAIMessage(message: ProviderMessage): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        result["role"] = toOpenAIRole(message.role)

        val normalizedContent =
            when {
                message.content != null -> message.content
                message.role == ProviderMessageRole.USER -> ""
                message.role == ProviderMessageRole.SYSTEM -> ""
                message.role == ProviderMessageRole.TOOL -> ""
                else -> null
            }

        if (normalizedContent != null) {
            result["content"] = normalizedContent
        }

        message.name?.let { result["name"] = it }
        message.toolCallId?.let { result["tool_call_id"] = it }
        if (message.toolCalls.isNotEmpty()) {
            result["tool_calls"] = message.toolCalls.map { toOpenAIToolCall(it) }
        }

        return result
    }

    private fun toOpenAIRole(role: ProviderMessageRole): String {
        return when (role) {
            ProviderMessageRole.SYSTEM -> "system"
            ProviderMessageRole.USER -> "user"
            ProviderMessageRole.ASSISTANT -> "assistant"
            ProviderMessageRole.TOOL -> "tool"
        }
    }

    private fun toOpenAITool(tool: ProviderTool): Map<String, Any?> {
        val function = linkedMapOf<String, Any?>("name" to tool.function.name)
        tool.function.description?.let { function["description"] = it }
        tool.function.parameters?.let { function["parameters"] = it }

        return mapOf(
            "type" to tool.type.name.lowercase(),
            "function" to function,
        )
    }

    private fun toOpenAIToolCall(toolCall: ProviderToolCall): Map<String, Any?> {
        return mapOf(
            "id" to toolCall.id,
            "type" to toolCall.type.name.lowercase(),
            "function" to
                mapOf(
                    "name" to toolCall.function.name,
                    "arguments" to toolCall.function.arguments,
                ),
        )
    }

    private fun toOpenAIResponseFormat(format: ResponseFormat): Map<String, Any?>? {
        return when (format.type) {
            ResponseFormatType.TEXT -> null
            ResponseFormatType.JSON_OBJECT -> mapOf("type" to "json_object")
            ResponseFormatType.JSON_SCHEMA -> {
                val payload = linkedMapOf<String, Any?>("type" to "json_schema")
                format.jsonSchema?.let { payload["json_schema"] = it }
                payload
            }
        }
    }

    private fun extractUsage(usageNode: JsonNode): ProviderTokenUsage? {
        if (!usageNode.isObject) {
            return null
        }

        return ProviderTokenUsage(
            promptTokens = usageNode.path("prompt_tokens").asInt(0),
            completionTokens = usageNode.path("completion_tokens").asInt(0),
            totalTokens = usageNode.path("total_tokens").asInt(0),
        )
    }

    private fun extractToolCalls(toolCallsNode: JsonNode): List<ProviderToolCall> {
        if (!toolCallsNode.isArray) {
            return emptyList()
        }

        return (0 until toolCallsNode.size()).mapNotNull { index ->
            val toolCallNode = toolCallsNode[index]
            val functionNode = toolCallNode.path("function")
            val functionName = functionNode.path("name").asText(null) ?: return@mapNotNull null
            val argumentsNode = functionNode.get("arguments")
            val arguments =
                when {
                    argumentsNode == null || argumentsNode.isNull -> "{}"
                    argumentsNode.isTextual -> argumentsNode.asText()
                    else -> objectMapper.writeValueAsString(argumentsNode)
                }

            ProviderToolCall(
                id = toolCallNode.path("id").asText("tool_call_$index"),
                type = ProviderToolType.FUNCTION,
                function =
                    ProviderFunctionCall(
                        name = functionName,
                        arguments = arguments,
                    ),
            )
        }
    }

    private fun extractContent(contentNode: JsonNode?): String? {
        if (contentNode == null || contentNode.isNull) {
            return null
        }
        if (contentNode.isTextual) {
            return contentNode.asText()
        }
        return objectMapper.writeValueAsString(contentNode)
    }

    private fun JsonNode.firstChoiceOrNull(): JsonNode? {
        val choices = path("choices")
        if (!choices.isArray || choices.isEmpty) {
            return null
        }
        return choices[0]
    }

    data class OpenAIError(
        val message: String?,
        val type: String?,
        val code: String?,
    )

    data class OpenAIFunction(
        val name: String,
        val description: String?,
        val parameters: JsonObject,
    )

    companion object {
        private val DEFAULT_FUNCTION_PARAMETERS: JsonObject =
            mapOf(
                "type" to "object",
                "properties" to emptyMap<String, Any?>(),
            )
    }
}
