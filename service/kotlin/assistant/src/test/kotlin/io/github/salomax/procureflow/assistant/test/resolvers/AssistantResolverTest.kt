package io.github.salomax.neotool.assistant.test.resolvers

import io.github.salomax.neotool.assistant.agent.AssistantAgent
import io.github.salomax.neotool.assistant.agent.ConversationContext
import io.github.salomax.neotool.assistant.graphql.resolvers.AssistantResolver
import io.github.salomax.neotool.assistant.llm.MessageRole
import io.github.salomax.neotool.assistant.service.ConversationService
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@MicronautTest
class AssistantResolverTest {
    private lateinit var assistantAgent: AssistantAgent
    private lateinit var conversationService: ConversationService
    private lateinit var resolver: AssistantResolver

    @BeforeEach
    fun setUp() {
        assistantAgent = mock()
        conversationService = mock()
        resolver = AssistantResolver(assistantAgent, conversationService)
    }

    @Test
    fun `should process chat message`() {
        runBlocking {
            // Arrange
            val sessionId = "test-session"
            val message = "I need to buy 10 USB-C cables"
            val input =
                mapOf(
                    "sessionId" to sessionId,
                    "message" to message,
                )
            val context = ConversationContext(sessionId = sessionId)
            val expectedResponse = "I found USB-C Cable. Processing checkout for 10 units."

            whenever(conversationService.getOrCreateContext(sessionId)).thenReturn(context)
            whenever(assistantAgent.processMessage(message, context)).thenReturn(expectedResponse)

            // Act
            val result = resolver.chat(input)

            // Assert
            assertThat(result.sessionId).isEqualTo(sessionId)
            assertThat(result.response).isEqualTo(expectedResponse)
            verify(assistantAgent).processMessage(message, context)
        }
    }

    @Test
    fun `should get conversation context`() {
        // Arrange
        val sessionId = "test-session"
        val context = ConversationContext(sessionId = sessionId)
        context.addMessage(MessageRole.USER, "Hello")
        context.addMessage(MessageRole.ASSISTANT, "Hi!")

        whenever(conversationService.getContext(sessionId)).thenReturn(context)

        // Act
        val result = resolver.conversation(sessionId)

        // Assert
        assertThat(result).isNotNull()
        assertThat(result!!.sessionId).isEqualTo(sessionId)
        assertThat(result.messageCount).isEqualTo(2)
        assertThat(result.lastMessage).isEqualTo("Hi!")
    }

    @Test
    fun `should return null for non-existent conversation`() {
        // Arrange
        val sessionId = "non-existent"
        whenever(conversationService.getContext(sessionId)).thenReturn(null)

        // Act
        val result = resolver.conversation(sessionId)

        // Assert
        assertThat(result).isNull()
    }

    @Test
    fun `should clear conversation`() {
        // Arrange
        val sessionId = "test-session"
        doNothing().whenever(conversationService).clearContext(sessionId)

        // Act
        val result = resolver.clearConversation(sessionId)

        // Assert
        assertThat(result).isTrue()
        verify(conversationService).clearContext(sessionId)
    }
}
