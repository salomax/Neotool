package io.github.salomax.neotool.mcp.ratelimit

import io.github.salomax.neotool.mcp.catalog.FinanceWriteMcpTools
import io.github.salomax.neotool.mcp.security.McpPrincipal
import jakarta.inject.Singleton

@Singleton
class McpRequestRateLimiter(
    private val rateLimitService: McpRateLimitService,
) {
    fun check(
        request: Map<String, Any?>,
        principal: McpPrincipal,
    ): McpRateLimitDecision? {
        val classification = classify(request) ?: return null
        val result = rateLimitService.checkAndIncrement(principal.principal, classification)
        return if (result.allowed) null else rateLimitService.rejectDecision(result)
    }

    private fun classify(request: Map<String, Any?>): McpToolClassification? {
        return when (request["method"]?.toString()) {
            "initialize", "tools/list" -> McpToolClassification.READ
            "tools/call" -> classifyToolCall(request["params"] as? Map<*, *>)
            else -> null
        }
    }

    private fun classifyToolCall(params: Map<*, *>?): McpToolClassification {
        val toolName = params?.get("name")?.toString()
        return if (toolName != null && toolName in writeTools) {
            McpToolClassification.WRITE
        } else {
            McpToolClassification.READ
        }
    }

    private companion object {
        private val writeTools = FinanceWriteMcpTools.all.map { it.name }.toSet()
    }
}
