package io.github.salomax.neotool.mcp.ratelimit

import io.github.salomax.neotool.mcp.catalog.FinanceWriteMcpTools
import io.github.salomax.neotool.mcp.security.McpPrincipal
import jakarta.inject.Singleton
import java.util.Optional

@Singleton
class McpRequestRateLimiter(
    // Absent when EntityManager/repo is disabled (e.g. protocol integration tests without DB).
    private val rateLimitService: Optional<McpRateLimitService>,
) {
    fun check(
        request: Map<String, Any?>,
        principal: McpPrincipal,
    ): McpRateLimitDecision? {
        val service = rateLimitService.orElse(null) ?: return null
        val classification = classify(request) ?: return null
        val result = service.checkAndIncrement(principal.principal, classification)
        return if (result.allowed) null else service.rejectDecision(result)
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
