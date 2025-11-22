package io.github.salomax.neotool.assistant.test.agent

import io.github.salomax.neotool.assistant.agent.AssistantAgent
import io.github.salomax.neotool.assistant.agent.ConversationContext
import io.github.salomax.neotool.assistant.llm.FinishReason
import io.github.salomax.neotool.assistant.llm.FunctionCall
import io.github.salomax.neotool.assistant.llm.LLMProvider
import io.github.salomax.neotool.assistant.llm.LLMResponse
import io.github.salomax.neotool.assistant.llm.MessageRole
import io.github.salomax.neotool.assistant.llm.tool.Tool
import io.github.salomax.neotool.assistant.llm.tool.ToolRegistry
import io.github.salomax.neotool.assistant.llm.tool.ToolResult
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@MicronautTest
class AssistantAgentTest {
    private lateinit var llmProvider: LLMProvider
    private lateinit var toolRegistry: ToolRegistry
    private lateinit var assistantAgent: AssistantAgent

    @BeforeEach
    fun setUp() {
        llmProvider = mock()
        toolRegistry = mock()
        assistantAgent = AssistantAgent(llmProvider, toolRegistry)
    }

    @Test
    fun `should process message and return LLM response`() {
        runBlocking {
            // Arrange
            val userMessage = "Search for USB-C cables"
            val context = ConversationContext(sessionId = "test-session")

            val functions =
                listOf(
                    FunctionDefinition(
                        name = "searchCatalogItems",
                        description = "Search catalog",
                        parameters =
                            FunctionParameters(
                                properties = emptyMap(),
                                required = emptyList(),
                            ),
                    ),
                )

            val llmResponse =
                LLMResponse(
                    text = "I found 2 USB-C cables in the catalog.",
                    functionCalls = emptyList(),
                    finishReason = FinishReason.STOP,
                )

            whenever(toolRegistry.getAllFunctionDefinitions()).thenReturn(functions)
            whenever(llmProvider.chatWithFunctions(any())).thenReturn(llmResponse)

            // Act
            val result = assistantAgent.processMessage(userMessage, context)

            // Assert
            assertThat(result).isEqualTo("I found 2 USB-C cables in the catalog.")
            verify(llmProvider).chatWithFunctions(any())
            assertThat(context.messages).hasSize(3) // System, User, Assistant
        }
    }

    @Test
    fun `should handle function calls and execute tools`() {
        runBlocking {
            // Arrange
            val userMessage = "I need to buy 10 USB-C cables"
            val context = ConversationContext(sessionId = "test-session")

            val functions =
                listOf(
                    FunctionDefinition(
                        name = "searchCatalogItems",
                        description = "Search catalog",
                        parameters =
                            FunctionParameters(
                                properties = emptyMap(),
                                required = emptyList(),
                            ),
                    ),
                )

            val tool = mock<Tool>()
            val toolResult =
                ToolResult(
                    success = true,
                    data = mapOf("items" to listOf(mapOf("id" to "1", "name" to "USB-C Cable"))),
                )

            val firstLLMResponse =
                LLMResponse(
                    text = null,
                    functionCalls =
                        listOf(
                            FunctionCall(
                                name = "searchCatalogItems",
                                arguments = mapOf("query" to "USB-C"),
                            ),
                        ),
                    finishReason = FinishReason.FUNCTION_CALL,
                )

            val secondLLMResponse =
                LLMResponse(
                    text = "I found USB-C Cable. Processing checkout for 10 units.",
                    functionCalls = emptyList(),
                    finishReason = FinishReason.STOP,
                )

            whenever(toolRegistry.getAllFunctionDefinitions()).thenReturn(functions)
            whenever(toolRegistry.getToolByName("searchCatalogItems")).thenReturn(tool)
            whenever(tool.execute(eq("searchCatalogItems"), any())).thenReturn(toolResult)
            whenever(llmProvider.chatWithFunctions(any()))
                .thenReturn(firstLLMResponse)
                .thenReturn(secondLLMResponse)

            // Act
            val result = assistantAgent.processMessage(userMessage, context)

            // Assert
            assertThat(result).contains("Processing checkout")
            verify(tool).execute(eq("searchCatalogItems"), any())
            verify(llmProvider, times(2)).chatWithFunctions(any())
        }
    }

    @Test
    fun `should add system prompt on first message`() {
        runBlocking {
            // Arrange
            val userMessage = "Hello"
            val context = ConversationContext(sessionId = "test-session")

            val llmResponse =
                LLMResponse(
                    text = "Hello! How can I help you?",
                    functionCalls = emptyList(),
                    finishReason = FinishReason.STOP,
                )

            whenever(toolRegistry.getAllFunctionDefinitions()).thenReturn(emptyList())
            whenever(llmProvider.chatWithFunctions(any())).thenReturn(llmResponse)

            // Act
            assistantAgent.processMessage(userMessage, context)

            // Assert
            assertThat(context.messages).hasSize(3)
            assertThat(context.messages[0].role).isEqualTo(MessageRole.SYSTEM)
        }
    }
}
