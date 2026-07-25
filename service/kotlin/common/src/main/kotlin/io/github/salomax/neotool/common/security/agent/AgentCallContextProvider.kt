package io.github.salomax.neotool.common.security.agent

import graphql.schema.DataFetchingEnvironment
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
class AgentCallContextProvider {
    fun fromGraphQl(env: DataFetchingEnvironment): AgentCallContext? {
        val cachedContext =
            try {
                env.graphQlContext.getOrEmpty<AgentCallContext>(AgentCallContext.CACHED_CONTEXT_KEY).orElse(null)
            } catch (e: Exception) {
                null
            }
        if (cachedContext != null) {
            return cachedContext
        }

        val context =
            fromValues(
                toolCallId = graphQlContextValue(env, AgentCallContext.TOOL_CALL_ID_CONTEXT_KEY),
                originClientId = graphQlContextValue(env, AgentCallContext.CLIENT_ID_CONTEXT_KEY),
                originTokenId = graphQlContextValue(env, AgentCallContext.TOKEN_ID_CONTEXT_KEY),
            ) ?: return null

        try {
            env.graphQlContext.put(AgentCallContext.CACHED_CONTEXT_KEY, context)
        } catch (_: Exception) {
            // Best-effort cache only.
        }

        return context
    }

    internal fun fromValues(
        toolCallId: String?,
        originClientId: String?,
        originTokenId: String?,
    ): AgentCallContext? {
        val normalizedToolCallId = toolCallId?.trim()
        val normalizedClientId = originClientId?.trim()
        val normalizedTokenId = originTokenId?.trim()

        if (
            normalizedToolCallId.isNullOrEmpty() &&
            normalizedClientId.isNullOrEmpty() &&
            normalizedTokenId.isNullOrEmpty()
        ) {
            return null
        }

        require(!normalizedToolCallId.isNullOrEmpty()) { "${AgentCallContext.TOOL_CALL_ID_HEADER} header is required" }
        require(!normalizedClientId.isNullOrEmpty()) { "${AgentCallContext.CLIENT_ID_HEADER} header is required" }
        require(!normalizedTokenId.isNullOrEmpty()) { "${AgentCallContext.TOKEN_ID_HEADER} header is required" }

        val parsedToolCallId =
            try {
                UUID.fromString(normalizedToolCallId)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid ${AgentCallContext.TOOL_CALL_ID_HEADER} header", e)
            }

        return AgentCallContext(
            toolCallId = parsedToolCallId,
            originClientId = normalizedClientId,
            originTokenId = normalizedTokenId,
        )
    }

    private fun graphQlContextValue(
        env: DataFetchingEnvironment,
        key: String,
    ): String? =
        try {
            env.graphQlContext.getOrEmpty<String>(key).orElse(null)
        } catch (e: Exception) {
            null
        }
}
