package io.github.salomax.neotool.assistant.llm

/**
 * Abstract interface for LLM providers.
 * Allows swapping between different LLM providers (Gemini, OpenAI, etc.)
 */
interface LLMProvider {
    /**
     * Send a chat message with function calling support
     *
     * @param request The LLM request containing messages and function definitions
     * @return LLM response with text and optional function calls
     */
    suspend fun chatWithFunctions(request: LLMRequest): LLMResponse

    /**
     * Get the model name being used
     */
    fun getModelName(): String
}
