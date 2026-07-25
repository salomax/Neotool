package io.github.salomax.neotool.common.security.agent

import java.util.UUID

/**
 * Correlates one MCP tool call with downstream banking writes.
 *
 * This context is propagated separately from delegated service tokens on purpose:
 * the delegated token remains purely token-derived identity, while this structure
 * carries per-call provenance metadata needed for attribution and reversal.
 */
data class AgentCallContext(
    val toolCallId: UUID,
    val originClientId: String,
    val originTokenId: String,
) {
    companion object {
        const val TOOL_CALL_ID_HEADER = "X-Mcp-Tool-Call-Id"
        const val CLIENT_ID_HEADER = "X-Mcp-Client-Id"
        const val TOKEN_ID_HEADER = "X-Mcp-Token-Id"

        const val TOOL_CALL_ID_CONTEXT_KEY = "mcpToolCallId"
        const val CLIENT_ID_CONTEXT_KEY = "mcpClientId"
        const val TOKEN_ID_CONTEXT_KEY = "mcpTokenId"
        const val CACHED_CONTEXT_KEY = "agentCallContext"
    }
}
