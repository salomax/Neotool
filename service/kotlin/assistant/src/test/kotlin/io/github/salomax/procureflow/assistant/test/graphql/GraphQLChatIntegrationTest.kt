package io.github.salomax.neotool.assistant.test.graphql

import io.github.salomax.neotool.assistant.llm.LLMProvider
import io.github.salomax.neotool.assistant.test.AssistantIntegrationTest
import io.github.salomax.neotool.assistant.test.llm.MockLLMProvider
import io.github.salomax.neotool.common.test.assertions.assertNoErrors
import io.github.salomax.neotool.common.test.assertions.shouldBeJson
import io.github.salomax.neotool.common.test.assertions.shouldBeSuccessful
import io.github.salomax.neotool.common.test.http.exchangeAsString
import io.github.salomax.neotool.common.test.json.read
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.json.tree.JsonNode
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Integration tests for GraphQL chat mutations and queries.
 * 
 * Note: These tests use a mocked LLM provider (MockLLMProvider) instead of making
 * real API calls to Gemini. The mock provider is automatically injected via
 * MockLLMProviderFactory which replaces GeminiProvider in the test context.
 */
class GraphQLChatIntegrationTest : AssistantIntegrationTest() {
    
    @Inject
    private lateinit var llmProvider: LLMProvider
    
    private val mockProvider: MockLLMProvider?
        get() = llmProvider as? MockLLMProvider
    
    @BeforeEach
    override fun setUp() {
        // Clear any custom responses from previous tests
        mockProvider?.clearCustomResponses()
    }
    
    private fun uniqueSessionId() = "test-session-${System.currentTimeMillis()}"
    
    @Test
    fun `should process chat message via GraphQL mutation`() {
        // Arrange
        val sessionId = uniqueSessionId()
        val message = "Search for USB-C cables"
        val mutation = chatMutation(sessionId, message)
        
        val request = HttpRequest.POST("/graphql", mutation)
            .contentType(MediaType.APPLICATION_JSON)
        
        // Act
        val response = httpClient.exchangeAsString(request)
        response
            .shouldBeSuccessful()
            .shouldBeJson()
        
        // Assert
        val payload: JsonNode = json.read(response)
        payload["errors"].assertNoErrors()
        
        val data = payload["data"]
        assertThat(data).isNotNull()
        
        val chatResponse = data["chat"]
        assertThat(chatResponse).isNotNull()
        assertThat(chatResponse["sessionId"].stringValue).isEqualTo(sessionId)
        assertThat(chatResponse["response"].stringValue).isNotEmpty()
    }
    
    @Test
    fun `should get conversation context via GraphQL query`() {
        // Arrange - first send a message
        val sessionId = uniqueSessionId()
        val message = "Hello"
        val chatMutation = chatMutation(sessionId, message)
        
        val chatRequest = HttpRequest.POST("/graphql", chatMutation)
            .contentType(MediaType.APPLICATION_JSON)
        
        val chatResponse = httpClient.exchangeAsString(chatRequest)
        chatResponse.shouldBeSuccessful()
        
        // Act - query conversation
        val query = conversationQuery(sessionId)
        val request = HttpRequest.POST("/graphql", query)
            .contentType(MediaType.APPLICATION_JSON)
        
        val response = httpClient.exchangeAsString(request)
        response
            .shouldBeSuccessful()
            .shouldBeJson()
        
        // Assert
        val payload: JsonNode = json.read(response)
        payload["errors"].assertNoErrors()
        
        val data = payload["data"]
        assertThat(data).isNotNull()
        
        val conversation = data["conversation"]
        assertThat(conversation).isNotNull()
        assertThat(conversation["sessionId"].stringValue).isEqualTo(sessionId)
        assertThat(conversation["messageCount"].intValue).isGreaterThan(0)
    }
    
    @Test
    fun `should clear conversation via GraphQL mutation`() {
        // Arrange - first send a message
        val sessionId = uniqueSessionId()
        val message = "Hello"
        val chatMutation = chatMutation(sessionId, message)
        
        val chatRequest = HttpRequest.POST("/graphql", chatMutation)
            .contentType(MediaType.APPLICATION_JSON)
        
        httpClient.exchangeAsString(chatRequest).shouldBeSuccessful()
        
        // Act - clear conversation
        val mutation = clearConversationMutation(sessionId)
        val request = HttpRequest.POST("/graphql", mutation)
            .contentType(MediaType.APPLICATION_JSON)
        
        val response = httpClient.exchangeAsString(request)
        response
            .shouldBeSuccessful()
            .shouldBeJson()
        
        // Assert
        val payload: JsonNode = json.read(response)
        payload["errors"].assertNoErrors()
        
        val data = payload["data"]
        assertThat(data).isNotNull()
        assertThat(data["clearConversation"].booleanValue).isTrue()
        
        // Verify conversation is cleared
        val query = conversationQuery(sessionId)
        val queryRequest = HttpRequest.POST("/graphql", query)
            .contentType(MediaType.APPLICATION_JSON)
        
        val queryResponse = httpClient.exchangeAsString(queryRequest)
        val queryPayload: JsonNode = json.read(queryResponse)
        val conversation = queryPayload["data"]["conversation"]
        assertThat(conversation.isNull).isTrue()
    }
    
    @Test
    fun `should handle multiple messages in same session`() {
        // Arrange
        val sessionId = uniqueSessionId()
        
        // Act - send multiple messages
        val message1 = "Search for USB-C cables"
        val mutation1 = chatMutation(sessionId, message1)
        val response1 = httpClient.exchangeAsString(
            HttpRequest.POST("/graphql", mutation1).contentType(MediaType.APPLICATION_JSON)
        )
        response1.shouldBeSuccessful()
        
        val message2 = "What is the price?"
        val mutation2 = chatMutation(sessionId, message2)
        val response2 = httpClient.exchangeAsString(
            HttpRequest.POST("/graphql", mutation2).contentType(MediaType.APPLICATION_JSON)
        )
        response2.shouldBeSuccessful()
        
        // Assert - check conversation has multiple messages
        val query = conversationQuery(sessionId)
        val queryRequest = HttpRequest.POST("/graphql", query)
            .contentType(MediaType.APPLICATION_JSON)
        
        val queryResponse = httpClient.exchangeAsString(queryRequest)
        val payload: JsonNode = json.read(queryResponse)
        payload["errors"].assertNoErrors()
        
        val conversation = payload["data"]["conversation"]
        assertThat(conversation["messageCount"].intValue).isGreaterThanOrEqualTo(4) // System + 2 user + 2 assistant
    }
}

