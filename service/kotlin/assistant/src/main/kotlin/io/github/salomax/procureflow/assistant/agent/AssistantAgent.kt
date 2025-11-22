package io.github.salomax.neotool.assistant.agent

import io.github.salomax.neotool.assistant.llm.FinishReason
import io.github.salomax.neotool.assistant.llm.LLMProvider
import io.github.salomax.neotool.assistant.llm.LLMRequest
import io.github.salomax.neotool.assistant.llm.MessageRole
import io.github.salomax.neotool.assistant.llm.tool.ToolRegistry
import io.github.salomax.neotool.assistant.llm.tool.ToolResult
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory

@Singleton
class AssistantAgent(
    private val llmProvider: LLMProvider,
    private val toolRegistry: ToolRegistry,
) {
    private val logger = LoggerFactory.getLogger(AssistantAgent::class.java)

    private val systemPrompt =
        """
        <enter the system prompt here>
        """.trimIndent()

    suspend fun processMessage(
        userMessage: String,
        context: ConversationContext,
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
        val request =
            LLMRequest(
                messages = context.messages.toList(),
                functions = functions,
            )

        var response = llmProvider.chatWithFunctions(request)

        // Handle function calls
        while (response.finishReason == FinishReason.FUNCTION_CALL && response.functionCalls.isNotEmpty()) {
            // Add assistant message with function calls
            val functionCallText =
                response.functionCalls.joinToString("\n") { call ->
                    "Calling function: ${call.name} with arguments: ${call.arguments}"
                }
            context.addMessage(MessageRole.ASSISTANT, functionCallText)

            // Execute function calls
            val functionResults =
                response.functionCalls.map { call ->
                    val tool = toolRegistry.getToolByName(call.name)
                    if (tool != null) {
                        val result = tool.execute(call.name, call.arguments)
                        // Add function result message
                        val resultText =
                            if (result.success) {
                                "Function ${call.name} result: Success\nData: ${result.data}"
                            } else {
                                "Function ${call.name} result: Error: ${result.error}"
                            }
                        context.addMessage(
                            MessageRole.FUNCTION,
                            resultText,
                        )
                        result
                    } else {
                        val errorResult = ToolResult(false, error = "Unknown function: ${call.name}")
                        context.addMessage(
                            MessageRole.FUNCTION,
                            "Function ${call.name} result: Error: Unknown function",
                        )
                        errorResult
                    }
                }

            // Call LLM again with function results
            response =
                llmProvider.chatWithFunctions(
                    LLMRequest(
                        messages = context.messages.toList(),
                        functions = functions,
                    ),
                )
        }

        // Add final assistant response
        val finalResponse = response.text ?: "I apologize, but I encountered an error processing your request."
        context.addMessage(MessageRole.ASSISTANT, finalResponse)

        return finalResponse
    }
}
