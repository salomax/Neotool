package io.github.salomax.neotool.assistant.agent

import io.github.salomax.neotool.assistant.llm.*
import io.github.salomax.neotool.assistant.llm.tool.ToolRegistry
import io.github.salomax.neotool.assistant.llm.tool.ToolResult
import jakarta.inject.Singleton
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

@Singleton
class AssistantAgent(
    private val llmProvider: LLMProvider,
    private val toolRegistry: ToolRegistry
) {
    
    private val logger = LoggerFactory.getLogger(AssistantAgent::class.java)
    
    private val systemPrompt = """
        You are a helpful procurement assistant for a catalog system. 
        
        Your role is to help users:
        1. Search for catalog items
        2. Get details about specific items (price, status, description)
        3. Checkout items directly (no cart - direct checkout)
        
        When users want to buy items, you should:
        - First search for the item if the name is not exact
        - Extract the quantity from their message (default to 1 if not specified)
        - Calculate the total price
        - Use the checkout function to process the order
        
        When displaying prices to users, always use the "price" field (formatted currency string like "$15.00") 
        instead of "priceCents" (raw number). 
        The "price" field is already formatted as currency and should be used directly in your responses.
        
        Always respond in natural, friendly language and consider the users' language (locale). 
        When you use functions, explain what you're doing.
    """.trimIndent()
    
    suspend fun processMessage(
        userMessage: String,
        context: ConversationContext
    ): String {
        // Add system prompt if not present
        if (context.messages.none { it.role == MessageRole.SYSTEM }) {
            context.addSystemMessage(systemPrompt)
        }
        
        // Add user message
        context.addMessage(MessageRole.USER, userMessage)
        
        // Get function definitions
        val functions = toolRegistry.getAllFunctionDefinitions()
        
        // Call LLM with function calling
        val request = LLMRequest(
            messages = context.messages.toList(),
            functions = functions
        )
        
        var response = llmProvider.chatWithFunctions(request)
        
        // Handle function calls
        while (response.finishReason == FinishReason.FUNCTION_CALL && response.functionCalls.isNotEmpty()) {
            // Add assistant message with function calls
            val functionCallText = response.functionCalls.joinToString("\n") { call ->
                "Calling function: ${call.name} with arguments: ${call.arguments}"
            }
            context.addMessage(MessageRole.ASSISTANT, functionCallText)
            
            // Execute function calls
            val functionResults = response.functionCalls.map { call ->
                val tool = toolRegistry.getToolByName(call.name)
                if (tool != null) {
                    val result = tool.execute(call.name, call.arguments)
                    // Add function result message
                    val resultText = if (result.success) {
                        "Function ${call.name} result: Success\nData: ${result.data}"
                    } else {
                        "Function ${call.name} result: Error: ${result.error}"
                    }
                    context.addMessage(
                        MessageRole.FUNCTION,
                        resultText
                    )
                    result
                } else {
                    val errorResult = ToolResult(false, error = "Unknown function: ${call.name}")
                    context.addMessage(
                        MessageRole.FUNCTION,
                        "Function ${call.name} result: Error: Unknown function"
                    )
                    errorResult
                }
            }
            
            // Call LLM again with function results
            response = llmProvider.chatWithFunctions(
                LLMRequest(
                    messages = context.messages.toList(),
                    functions = functions
                )
            )
        }
        
        // Add final assistant response
        val finalResponse = response.text ?: "I apologize, but I encountered an error processing your request."
        context.addMessage(MessageRole.ASSISTANT, finalResponse)
        
        return finalResponse
    }
}

