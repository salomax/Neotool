package io.github.salomax.neotool.assistant.agent

/**
 * High-level assistant orchestration contract used by conversation flows.
 *
 * Implementations are responsible for:
 * - Taking the current conversation context and new user message
 * - Calling the underlying completion orchestration (LLM/tool calling, budgets, etc.)
 * - Appending assistant messages to the provided context
 * - Returning the assistant's final textual response
 *
 * Tests and services can mock this interface to focus on conversation and escalation behavior.
 */
interface AssistantAgent {
    suspend fun processMessage(
        userMessage: String,
        context: ConversationContext,
    ): String
}
