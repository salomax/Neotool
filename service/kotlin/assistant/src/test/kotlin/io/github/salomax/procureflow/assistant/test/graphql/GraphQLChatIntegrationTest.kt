package io.github.salomax.neotool.assistant.test.graphql

import io.github.salomax.neotool.assistant.llm.*
import io.github.salomax.neotool.assistant.test.AssistantIntegrationTest
import io.github.salomax.neotool.assistant.test.llm.MockLLMProvider
import io.github.salomax.neotool.common.test.assertions.assertNoErrors
import io.github.salomax.neotool.common.test.assertions.shouldBeJson
import io.github.salomax.neotool.common.test.assertions.shouldBeSuccessful
import io.github.salomax.neotool.common.test.http.exchangeAsString
import io.github.salomax.neotool.common.test.json.read
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.json.tree.JsonNode
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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
    
    @Test
    fun `should handle chat with function calling`() {
        // Arrange - configure mock to return function call
        val sessionId = uniqueSessionId()
        val message = "Search for USB-C cables"
        
        val functionCall = FunctionCall(
            name = "searchCatalogItems",
            arguments = mapOf("query" to "USB-C")
        )
        
        val functionCallResponse = LLMResponse(
            text = null,
            functionCalls = listOf(functionCall),
            finishReason = FinishReason.FUNCTION_CALL
        )
        
        // After function call, return final response
        val finalResponse = LLMResponse(
            text = "I found several USB-C cables in the catalog.",
            functionCalls = emptyList(),
            finishReason = FinishReason.STOP
        )
        
        var callCount = 0
        mockProvider?.setDefaultResponseProvider { request ->
            callCount++
            if (callCount == 1) {
                functionCallResponse
            } else {
                finalResponse
            }
        }
        
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
    fun `should handle chat with multiple function calls`() {
        // Arrange
        val sessionId = uniqueSessionId()
        val message = "Search for USB-C and get item details"
        
        var callCount = 0
        mockProvider?.setDefaultResponseProvider { request ->
            callCount++
            when (callCount) {
                1 -> LLMResponse(
                    text = null,
                    functionCalls = listOf(
                        FunctionCall("searchCatalogItems", mapOf("query" to "USB-C"))
                    ),
                    finishReason = FinishReason.FUNCTION_CALL
                )
                2 -> LLMResponse(
                    text = null,
                    functionCalls = listOf(
                        FunctionCall("catalogItem", mapOf("id" to "1"))
                    ),
                    finishReason = FinishReason.FUNCTION_CALL
                )
                else -> LLMResponse(
                    text = "Here are the USB-C cables I found.",
                    functionCalls = emptyList(),
                    finishReason = FinishReason.STOP
                )
            }
        }
        
        val mutation = chatMutation(sessionId, message)
        val request = HttpRequest.POST("/graphql", mutation)
            .contentType(MediaType.APPLICATION_JSON)
        
        // Act
        val response = httpClient.exchangeAsString(request)
        response.shouldBeSuccessful()
        
        // Assert
        val payload: JsonNode = json.read(response)
        payload["errors"].assertNoErrors()
        
        val chatResponse = payload["data"]["chat"]
        assertThat(chatResponse["response"].stringValue).isNotEmpty()
    }
    
    @Test
    fun `should handle unknown function call`() {
        // Arrange
        val sessionId = uniqueSessionId()
        val message = "Call unknown function"
        
        val functionCall = FunctionCall(
            name = "unknownFunction",
            arguments = mapOf("param" to "value")
        )
        
        var callCount = 0
        mockProvider?.setDefaultResponseProvider { request ->
            callCount++
            if (callCount == 1) {
                LLMResponse(
                    text = null,
                    functionCalls = listOf(functionCall),
                    finishReason = FinishReason.FUNCTION_CALL
                )
            } else {
                LLMResponse(
                    text = "I encountered an error with the function call.",
                    functionCalls = emptyList(),
                    finishReason = FinishReason.STOP
                )
            }
        }
        
        val mutation = chatMutation(sessionId, message)
        val request = HttpRequest.POST("/graphql", mutation)
            .contentType(MediaType.APPLICATION_JSON)
        
        // Act
        val response = httpClient.exchangeAsString(request)
        response.shouldBeSuccessful()
        
        // Assert - should handle gracefully
        val payload: JsonNode = json.read(response)
        payload["errors"].assertNoErrors()
    }
    
    @Test
    fun `should handle chat with missing sessionId`() {
        // Arrange
        val mutation = graphQLQuery(
            """
            mutation Chat(${'$'}input: ChatInput!) {
                chat(input: ${'$'}input) {
                    sessionId
                    response
                }
            }
            """.trimIndent(),
            mapOf("input" to mapOf("message" to "Hello"))
        )
        
        val request = HttpRequest.POST("/graphql", mutation)
            .contentType(MediaType.APPLICATION_JSON)
        
        // Act & Assert - should return error for missing required field
        val response = httpClient.exchangeAsString(request)
        response.shouldBeSuccessful()
        
        val payload: JsonNode = json.read(response)
        // GraphQL validation should catch this
        val errors = payload["errors"]
        assertThat(errors).isNotNull()
    }
    
    @Test
    fun `should handle chat with missing message`() {
        // Arrange
        val sessionId = uniqueSessionId()
        val mutation = graphQLQuery(
            """
            mutation Chat(${'$'}input: ChatInput!) {
                chat(input: ${'$'}input) {
                    sessionId
                    response
                }
            }
            """.trimIndent(),
            mapOf("input" to mapOf("sessionId" to sessionId))
        )
        
        val request = HttpRequest.POST("/graphql", mutation)
            .contentType(MediaType.APPLICATION_JSON)
        
        // Act & Assert
        val response = httpClient.exchangeAsString(request)
        response.shouldBeSuccessful()
        
        val payload: JsonNode = json.read(response)
        val errors = payload["errors"]
        assertThat(errors).isNotNull()
    }
    
    @Test
    fun `should handle conversation query for non-existent session`() {
        // Arrange
        val nonExistentSessionId = "non-existent-session-${System.currentTimeMillis()}"
        val query = conversationQuery(nonExistentSessionId)
        val request = HttpRequest.POST("/graphql", query)
            .contentType(MediaType.APPLICATION_JSON)
        
        // Act
        val response = httpClient.exchangeAsString(request)
        response.shouldBeSuccessful()
        
        // Assert
        val payload: JsonNode = json.read(response)
        payload["errors"].assertNoErrors()
        
        val conversation = payload["data"]["conversation"]
        assertThat(conversation.isNull).isTrue()
    }
    
    @Test
    fun `should handle clear conversation for non-existent session`() {
        // Arrange
        val nonExistentSessionId = "non-existent-session-${System.currentTimeMillis()}"
        val mutation = clearConversationMutation(nonExistentSessionId)
        val request = HttpRequest.POST("/graphql", mutation)
            .contentType(MediaType.APPLICATION_JSON)
        
        // Act
        val response = httpClient.exchangeAsString(request)
        response.shouldBeSuccessful()
        
        // Assert - should succeed even if session doesn't exist
        val payload: JsonNode = json.read(response)
        payload["errors"].assertNoErrors()
        
        val data = payload["data"]
        assertThat(data["clearConversation"].booleanValue).isTrue()
    }
    
    @Test
    fun `should handle empty message`() {
        // Arrange
        val sessionId = uniqueSessionId()
        val message = ""
        val mutation = chatMutation(sessionId, message)
        
        val request = HttpRequest.POST("/graphql", mutation)
            .contentType(MediaType.APPLICATION_JSON)
        
        // Act
        val response = httpClient.exchangeAsString(request)
        response.shouldBeSuccessful()
        
        // Assert
        val payload: JsonNode = json.read(response)
        payload["errors"].assertNoErrors()
        
        val chatResponse = payload["data"]["chat"]
        assertThat(chatResponse).isNotNull()
    }
    
    @Test
    fun `should handle very long message`() {
        // Arrange
        val sessionId = uniqueSessionId()
        val longMessage = "A".repeat(10000)
        val mutation = chatMutation(sessionId, longMessage)
        
        val request = HttpRequest.POST("/graphql", mutation)
            .contentType(MediaType.APPLICATION_JSON)
        
        // Act
        val response = httpClient.exchangeAsString(request)
        response.shouldBeSuccessful()
        
        // Assert
        val payload: JsonNode = json.read(response)
        payload["errors"].assertNoErrors()
    }
    
    @Test
    fun `should handle special characters in message`() {
        // Arrange
        val sessionId = uniqueSessionId()
        val message = "Test with special chars: !@#\$%^&*()_+-=[]{}|;':\",./<>?"
        val mutation = chatMutation(sessionId, message)
        
        val request = HttpRequest.POST("/graphql", mutation)
            .contentType(MediaType.APPLICATION_JSON)
        
        // Act
        val response = httpClient.exchangeAsString(request)
        response.shouldBeSuccessful()
        
        // Assert
        val payload: JsonNode = json.read(response)
        payload["errors"].assertNoErrors()
    }
    
    @Test
    fun `should handle unicode characters in message`() {
        // Arrange
        val sessionId = uniqueSessionId()
        val message = "Test with unicode: 你好世界 🌍 émojis 🎉"
        val mutation = chatMutation(sessionId, message)
        
        val request = HttpRequest.POST("/graphql", mutation)
            .contentType(MediaType.APPLICATION_JSON)
        
        // Act
        val response = httpClient.exchangeAsString(request)
        response.shouldBeSuccessful()
        
        // Assert
        val payload: JsonNode = json.read(response)
        payload["errors"].assertNoErrors()
    }
    
    @Test
    fun `should handle LLM response with error finish reason`() {
        // Arrange
        val sessionId = uniqueSessionId()
        val message = "Test error handling"
        
        mockProvider?.setDefaultResponseProvider {
            LLMResponse(
                text = "I apologize, but I encountered an error processing your request.",
                functionCalls = emptyList(),
                finishReason = FinishReason.ERROR
            )
        }
        
        val mutation = chatMutation(sessionId, message)
        val request = HttpRequest.POST("/graphql", mutation)
            .contentType(MediaType.APPLICATION_JSON)
        
        // Act
        val response = httpClient.exchangeAsString(request)
        response.shouldBeSuccessful()
        
        // Assert
        val payload: JsonNode = json.read(response)
        payload["errors"].assertNoErrors()
        
        val chatResponse = payload["data"]["chat"]
        assertThat(chatResponse["response"].stringValue).isNotEmpty()
    }
    
    @Test
    fun `should handle LLM response with null text`() {
        // Arrange
        val sessionId = uniqueSessionId()
        val message = "Test null text"
        
        mockProvider?.setDefaultResponseProvider {
            LLMResponse(
                text = null,
                functionCalls = emptyList(),
                finishReason = FinishReason.STOP
            )
        }
        
        val mutation = chatMutation(sessionId, message)
        val request = HttpRequest.POST("/graphql", mutation)
            .contentType(MediaType.APPLICATION_JSON)
        
        // Act
        val response = httpClient.exchangeAsString(request)
        response.shouldBeSuccessful()
        
        // Assert - should use fallback message
        val payload: JsonNode = json.read(response)
        payload["errors"].assertNoErrors()
        
        val chatResponse = payload["data"]["chat"]
        assertThat(chatResponse["response"].stringValue).isNotEmpty()
    }
    
    @Test
    fun `should maintain conversation context across multiple function calls`() {
        // Arrange
        val sessionId = uniqueSessionId()
        val message1 = "Search for USB-C"
        val message2 = "What is the price of the first one?"
        
        var callCount = 0
        mockProvider?.setDefaultResponseProvider { request ->
            callCount++
            val userMessage = request.messages.findLast { it.role == MessageRole.USER }?.content ?: ""
            
            when {
                userMessage.contains("Search") -> LLMResponse(
                    text = "I found USB-C cables.",
                    functionCalls = emptyList(),
                    finishReason = FinishReason.STOP
                )
                userMessage.contains("price") -> LLMResponse(
                    text = "The price is $15.99.",
                    functionCalls = emptyList(),
                    finishReason = FinishReason.STOP
                )
                else -> LLMResponse(
                    text = "How can I help?",
                    functionCalls = emptyList(),
                    finishReason = FinishReason.STOP
                )
            }
        }
        
        // Send first message
        val mutation1 = chatMutation(sessionId, message1)
        val response1 = httpClient.exchangeAsString(
            HttpRequest.POST("/graphql", mutation1).contentType(MediaType.APPLICATION_JSON)
        )
        response1.shouldBeSuccessful()
        
        // Send second message
        val mutation2 = chatMutation(sessionId, message2)
        val response2 = httpClient.exchangeAsString(
            HttpRequest.POST("/graphql", mutation2).contentType(MediaType.APPLICATION_JSON)
        )
        response2.shouldBeSuccessful()
        
        // Assert - check conversation has context
        val query = conversationQuery(sessionId)
        val queryResponse = httpClient.exchangeAsString(
            HttpRequest.POST("/graphql", query).contentType(MediaType.APPLICATION_JSON)
        )
        
        val payload: JsonNode = json.read(queryResponse)
        val conversation = payload["data"]["conversation"]
        assertThat(conversation["messageCount"].intValue).isGreaterThan(4)
    }
    
    @Test
    fun `should not add system message again on second message in session`() {
        // Arrange - send first message (system message will be added)
        val sessionId = uniqueSessionId()
        val message1 = "First message"
        val mutation1 = chatMutation(sessionId, message1)
        
        val response1 = httpClient.exchangeAsString(
            HttpRequest.POST("/graphql", mutation1).contentType(MediaType.APPLICATION_JSON)
        )
        response1.shouldBeSuccessful()
        
        // Act - send second message (system message should NOT be added again)
        val message2 = "Second message"
        val mutation2 = chatMutation(sessionId, message2)
        val response2 = httpClient.exchangeAsString(
            HttpRequest.POST("/graphql", mutation2).contentType(MediaType.APPLICATION_JSON)
        )
        response2.shouldBeSuccessful()
        
        // Assert - check conversation has only one system message
        val query = conversationQuery(sessionId)
        val queryResponse = httpClient.exchangeAsString(
            HttpRequest.POST("/graphql", query).contentType(MediaType.APPLICATION_JSON)
        )
        
        val payload: JsonNode = json.read(queryResponse)
        val conversation = payload["data"]["conversation"]
        // Should have: 1 system + 2 user + 2 assistant = 5 messages (only 1 system message)
        assertThat(conversation["messageCount"].intValue).isEqualTo(5)
    }
    
    @Test
    fun `should handle function call with tool returning error result`() {
        // Arrange - configure mock to return function call with missing required parameter
        // This will cause the tool to return ToolResult(success = false, error = "...")
        // which tests the else branch at line 59 in AssistantAgent (result.success = false)
        val sessionId = uniqueSessionId()
        val message = "Search without query parameter"
        
        // Function call with missing 'query' parameter will cause CatalogTool to return error
        val functionCall = FunctionCall(
            name = "searchCatalogItems",
            arguments = mapOf<String, Any?>() // Missing 'query' parameter
        )
        
        val functionCallResponse = LLMResponse(
            text = null,
            functionCalls = listOf(functionCall),
            finishReason = FinishReason.FUNCTION_CALL
        )
        
        // After function call (which will return error), return final response
        val finalResponse = LLMResponse(
            text = "I encountered an error while searching. Please provide a search query.",
            functionCalls = emptyList(),
            finishReason = FinishReason.STOP
        )
        
        var callCount = 0
        mockProvider?.setDefaultResponseProvider { request ->
            callCount++
            if (callCount == 1) {
                functionCallResponse
            } else {
                finalResponse
            }
        }
        
        val mutation = chatMutation(sessionId, message)
        val request = HttpRequest.POST("/graphql", mutation)
            .contentType(MediaType.APPLICATION_JSON)
        
        // Act
        val response = httpClient.exchangeAsString(request)
        response.shouldBeSuccessful()
        
        // Assert - should handle tool error gracefully (tests result.success = false branch)
        val payload: JsonNode = json.read(response)
        payload["errors"].assertNoErrors()
        
        val chatResponse = payload["data"]["chat"]
        assertThat(chatResponse).isNotNull()
        assertThat(chatResponse["response"].stringValue).isNotEmpty()
    }
    
    @Test
    fun `should handle conversation query with lastMessage when messages exist`() {
        // Arrange - send a message to create conversation
        val sessionId = uniqueSessionId()
        val message = "Test message for lastMessage"
        val mutation = chatMutation(sessionId, message)
        httpClient.exchangeAsString(
            HttpRequest.POST("/graphql", mutation).contentType(MediaType.APPLICATION_JSON)
        ).shouldBeSuccessful()
        
        // Act - query conversation
        val query = conversationQuery(sessionId)
        val queryResponse = httpClient.exchangeAsString(
            HttpRequest.POST("/graphql", query).contentType(MediaType.APPLICATION_JSON)
        )
        
        // Assert - lastMessage should be present (tests lastOrNull()?.content branch)
        val payload: JsonNode = json.read(queryResponse)
        payload["errors"].assertNoErrors()
        
        val conversation = payload["data"]["conversation"]
        assertThat(conversation).isNotNull()
        assertThat(conversation["lastMessage"]).isNotNull()
        assertThat(conversation["lastMessage"].stringValue).isNotEmpty()
    }
    
    @Test
    fun `should handle function call with empty functionCalls list`() {
        // Arrange - configure mock to return FUNCTION_CALL finishReason but empty functionCalls
        // This tests the branch: while (finishReason == FUNCTION_CALL && functionCalls.isNotEmpty())
        // Specifically the case where finishReason is FUNCTION_CALL but functionCalls is empty
        val sessionId = uniqueSessionId()
        val message = "Trigger function call with empty list"
        
        val functionCallResponse = LLMResponse(
            text = null,
            functionCalls = emptyList(), // Empty list
            finishReason = FinishReason.FUNCTION_CALL
        )
        
        val finalResponse = LLMResponse(
            text = "Response after empty function call",
            functionCalls = emptyList(),
            finishReason = FinishReason.STOP
        )
        
        var callCount = 0
        mockProvider?.setDefaultResponseProvider { request ->
            callCount++
            if (callCount == 1) {
                functionCallResponse
            } else {
                finalResponse
            }
        }
        
        val mutation = chatMutation(sessionId, message)
        val request = HttpRequest.POST("/graphql", mutation)
            .contentType(MediaType.APPLICATION_JSON)
        
        // Act
        val response = httpClient.exchangeAsString(request)
        response.shouldBeSuccessful()
        
        // Assert - should handle gracefully (while loop should not execute)
        val payload: JsonNode = json.read(response)
        payload["errors"].assertNoErrors()
        
        val chatResponse = payload["data"]["chat"]
        assertThat(chatResponse).isNotNull()
        assertThat(chatResponse["response"].stringValue).isNotEmpty()
    }
    
    @Test
    fun `should handle function call with finishReason not FUNCTION_CALL`() {
        // Arrange - configure mock to return STOP finishReason with functionCalls
        // This tests the branch: while (finishReason == FUNCTION_CALL && functionCalls.isNotEmpty())
        // Specifically the case where finishReason is not FUNCTION_CALL
        val sessionId = uniqueSessionId()
        val message = "Trigger stop with function calls"
        
        val responseWithFunctionCalls = LLMResponse(
            text = "Response text",
            functionCalls = listOf(
                FunctionCall("searchCatalogItems", mapOf("query" to "test"))
            ),
            finishReason = FinishReason.STOP // Not FUNCTION_CALL
        )
        
        mockProvider?.setDefaultResponseProvider { request ->
            responseWithFunctionCalls
        }
        
        val mutation = chatMutation(sessionId, message)
        val request = HttpRequest.POST("/graphql", mutation)
            .contentType(MediaType.APPLICATION_JSON)
        
        // Act
        val response = httpClient.exchangeAsString(request)
        response.shouldBeSuccessful()
        
        // Assert - should handle gracefully (while loop should not execute)
        val payload: JsonNode = json.read(response)
        payload["errors"].assertNoErrors()
        
        val chatResponse = payload["data"]["chat"]
        assertThat(chatResponse).isNotNull()
        assertThat(chatResponse["response"].stringValue).isNotEmpty()
    }
}

