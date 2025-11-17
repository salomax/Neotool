package io.github.salomax.neotool.assistant.test.llm

import io.github.salomax.neotool.assistant.llm.*
import io.github.salomax.neotool.assistant.llm.gemini.GeminiProvider
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Replaces
import jakarta.inject.Singleton
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Factory that creates a mock LLM provider for integration tests.
 * This replaces the real GeminiProvider to avoid making actual API calls.
 */
@Factory
class MockLLMProviderFactory {
    
    /**
     * Creates a mock LLM provider that replaces GeminiProvider in tests.
     * The mock can be configured in tests to return custom responses.
     */
    @Singleton
    @Primary
    @Replaces(GeminiProvider::class)
    fun mockLLMProvider(): LLMProvider {
        return MockLLMProvider()
    }
}

/**
 * Mock implementation of LLMProvider for testing.
 * Provides default responses but can be configured per test.
 */
class MockLLMProvider : LLMProvider {
    
    // Thread-safe storage for custom responses per request
    private val customResponses = ConcurrentHashMap<String, LLMResponse>()
    private val defaultResponseProvider = AtomicReference<(LLMRequest) -> LLMResponse>(this::defaultResponse)
    
    override suspend fun chatWithFunctions(request: LLMRequest): LLMResponse {
        // Small delay to simulate API call
        delay(10)
        
        // Check for custom response based on user message
        val userMessage = request.messages.findLast { it.role == MessageRole.USER }?.content ?: ""
        val customResponse = customResponses[userMessage]
        if (customResponse != null) {
            return customResponse
        }
        
        // Use default response provider
        return defaultResponseProvider.get()(request)
    }
    
    override fun getModelName(): String = "mock-llm-provider"
    
    /**
     * Sets a custom response for a specific user message.
     */
    fun setResponseForMessage(userMessage: String, response: LLMResponse) {
        customResponses[userMessage] = response
    }
    
    /**
     * Sets a custom default response provider.
     */
    fun setDefaultResponseProvider(provider: (LLMRequest) -> LLMResponse) {
        defaultResponseProvider.set(provider)
    }
    
    /**
     * Clears all custom responses.
     */
    fun clearCustomResponses() {
        customResponses.clear()
    }
    
    /**
     * Default response based on message content.
     */
    private fun defaultResponse(request: LLMRequest): LLMResponse {
        val userMessage = request.messages.findLast { it.role == MessageRole.USER }?.content ?: ""
        val responseText = when {
            userMessage.contains("USB-C", ignoreCase = true) -> 
                "I found several USB-C cables in the catalog. Would you like to see the details?"
            userMessage.contains("price", ignoreCase = true) -> 
                "The price for the USB-C cable is $15.99."
            userMessage.contains("Hello", ignoreCase = true) -> 
                "Hello! How can I help you with your procurement needs today?"
            else -> 
                "I understand. How can I help you with your procurement needs?"
        }
        return LLMResponse(
            text = responseText,
            functionCalls = emptyList(),
            finishReason = FinishReason.STOP
        )
    }
}

