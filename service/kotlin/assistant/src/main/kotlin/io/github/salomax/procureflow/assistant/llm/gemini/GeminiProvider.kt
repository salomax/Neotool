package io.github.salomax.neotool.assistant.llm.gemini

import com.google.genai.Client
import com.google.genai.types.*
import io.github.salomax.neotool.assistant.llm.*
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

@Singleton
class GeminiProvider(
    private val config: GeminiConfig
) : LLMProvider {
    
    private val logger = LoggerFactory.getLogger(GeminiProvider::class.java)
    
    // Lazy initialization of the client
    private val client: Client by lazy {
        if (config.apiKey.isBlank()) {
            throw IllegalStateException("Gemini API key is not configured. Set GEMINI_API_KEY environment variable.")
        }
        Client.builder()
            .apiKey(config.apiKey)
            .build()
    }
    
    override suspend fun chatWithFunctions(request: LLMRequest): LLMResponse {
        return withContext(Dispatchers.IO) {
            try {
                // Validate API key
                if (config.apiKey.isBlank()) {
                    logger.error("Gemini API key is not configured. Set GEMINI_API_KEY environment variable.")
                    return@withContext LLMResponse(
                        text = null,
                        functionCalls = emptyList(),
                        finishReason = io.github.salomax.neotool.assistant.llm.FinishReason.ERROR
                    )
                }
                
                // Validate messages
                if (request.messages.isEmpty()) {
                    logger.error("LLM request has no messages")
                    return@withContext LLMResponse(
                        text = null,
                        functionCalls = emptyList(),
                        finishReason = io.github.salomax.neotool.assistant.llm.FinishReason.ERROR
                    )
                }
                
                // Convert messages to SDK Content format
                val contents = request.messages.map { msg ->
                    when (msg.role) {
                        MessageRole.USER -> Content.fromParts(Part.fromText(msg.content))
                        MessageRole.ASSISTANT -> {
                            Content.builder()
                                .role("model")
                                .parts(Part.fromText(msg.content))
                                .build()
                        }
                        MessageRole.SYSTEM -> Content.fromParts(Part.fromText(msg.content))
                        MessageRole.FUNCTION -> {
                            Content.builder()
                                .role("model")
                                .parts(Part.fromText(msg.content))
                                .build()
                        }
                    }
                }
                
                // Build generation config
                val configBuilder = GenerateContentConfig.builder()
                    .temperature(config.temperature.toFloat())
                    .maxOutputTokens(config.maxTokens)
                
                // Add tools if there are functions
                if (request.functions.isNotEmpty()) {
                    val functionDeclarations = request.functions.map { func ->
                        FunctionDeclaration.builder()
                            .name(func.name)
                            .description(func.description)
                            .parameters(convertParametersToSchema(func.parameters))
                            .build()
                    }
                    
                    val tool = Tool.builder()
                        .functionDeclarations(functionDeclarations)
                        .build()
                    
                    configBuilder.tools(listOf(tool))
                }
                
                val generateContentConfig = configBuilder.build()
                
                logger.debug("Calling Gemini API with model: ${config.model}")
                
                // Generate content using the SDK
                val response = client.models.generateContent(
                    config.model,
                    contents,
                    generateContentConfig
                )
                
                // Parse response
                val text = response.text() ?: ""
                val functionCalls = response.functionCalls()?.mapNotNull { functionCall ->
                    val name = functionCall.name().orElse(null)
                    val args = functionCall.args().orElse(null)
                    if (name != null) {
                        io.github.salomax.neotool.assistant.llm.FunctionCall(
                            name = name,
                            arguments = args?.toMap() ?: emptyMap()
                        )
                    } else null
                } ?: emptyList()
                
                // Map SDK FinishReason to our FinishReason
                val sdkFinishReason = response.finishReason()
                val finishReason = when {
                    functionCalls.isNotEmpty() -> io.github.salomax.neotool.assistant.llm.FinishReason.FUNCTION_CALL
                    text.isNotEmpty() -> io.github.salomax.neotool.assistant.llm.FinishReason.STOP
                    else -> {
                        val knownEnum = sdkFinishReason.knownEnum()
                        when (knownEnum?.toString()) {
                            "MAX_TOKENS", "MAX_OUTPUT_TOKENS" -> io.github.salomax.neotool.assistant.llm.FinishReason.LENGTH
                            "STOP" -> io.github.salomax.neotool.assistant.llm.FinishReason.STOP
                            else -> io.github.salomax.neotool.assistant.llm.FinishReason.ERROR
                        }
                    }
                }
                
                LLMResponse(
                    text = text.ifEmpty { null },
                    functionCalls = functionCalls,
                    finishReason = finishReason
                )
            } catch (e: Exception) {
                logger.error("Error calling Gemini API: ${e.message}", e)
                LLMResponse(
                    text = null,
                    functionCalls = emptyList(),
                    finishReason = io.github.salomax.neotool.assistant.llm.FinishReason.ERROR
                )
            }
        }
    }
    
    override fun getModelName(): String = config.model
    
    private fun convertParametersToSchema(params: FunctionParameters): Schema {
        val properties = params.properties.mapValues { (key, prop) ->
            val type = when (prop.type.lowercase()) {
                "string" -> Type(Type.Known.STRING)
                "integer" -> Type(Type.Known.INTEGER)
                "number" -> Type(Type.Known.NUMBER)
                "boolean" -> Type(Type.Known.BOOLEAN)
                "array" -> Type(Type.Known.ARRAY)
                else -> Type(Type.Known.STRING)
            }
            
            val builder = Schema.builder()
                .type(type)
                .description(prop.description)
            
            // For array types, we need to specify the items schema
            if (prop.type.lowercase() == "array") {
                // Try to infer array item schema from the property name and description
                val itemsSchema = inferArrayItemsSchema(key, prop.description)
                builder.items(itemsSchema)
            }
            
            // Add enum if present
            if (prop.enum != null && prop.enum.isNotEmpty()) {
                builder.enum_(prop.enum)
            }
            
            builder.build()
        }
        
        return Schema.builder()
            .type(Type(Type.Known.OBJECT))
            .properties(properties)
            .required(params.required)
            .build()
    }
    
    private fun inferArrayItemsSchema(propertyName: String, description: String): Schema {
        // For "items" arrays in checkout, create a schema for checkout item objects
        if (propertyName == "items" && description.contains("catalogItemId", ignoreCase = true)) {
            return Schema.builder()
                .type(Type(Type.Known.OBJECT))
                .properties(
                    mapOf(
                        "catalogItemId" to Schema.builder()
                            .type(Type(Type.Known.STRING))
                            .description("Catalog item ID")
                            .build(),
                        "name" to Schema.builder()
                            .type(Type(Type.Known.STRING))
                            .description("Item name")
                            .build(),
                        "priceCents" to Schema.builder()
                            .type(Type(Type.Known.INTEGER))
                            .description("Price in cents")
                            .build(),
                        "quantity" to Schema.builder()
                            .type(Type(Type.Known.INTEGER))
                            .description("Quantity")
                            .build()
                    )
                )
                .required(listOf("catalogItemId", "name", "priceCents", "quantity"))
                .build()
        }
        
        // Default: array of strings (most common fallback)
        return Schema.builder()
            .type(Type(Type.Known.STRING))
            .build()
    }
}
