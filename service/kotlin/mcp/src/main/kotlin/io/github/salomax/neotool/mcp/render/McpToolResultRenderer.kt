package io.github.salomax.neotool.mcp.render

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.mcp.protocol.CallToolResult
import io.github.salomax.neotool.mcp.protocol.TextContent
import jakarta.inject.Singleton

@Singleton
class McpToolResultRenderer(
    private val objectMapper: ObjectMapper,
) {
    fun render(
        result: McpToolResult,
        scopes: Set<String>,
    ): CallToolResult {
        val rendered =
            when (result) {
                is McpToolSuccessResult -> renderValue(result.data, scopes, emptyList())
                is McpToolErrorResult ->
                    mapOf(
                        "error" to
                            mapOf(
                                "code" to result.code,
                                "service" to result.service,
                                "field" to result.field,
                                "parameters" to result.parameters,
                            ).filterValues { it != null && it != emptyMap<String, Any?>() },
                    )
            }

        return CallToolResult(
            content = listOf(TextContent(text = objectMapper.writeValueAsString(rendered))),
            isError = result is McpToolErrorResult,
        )
    }

    private fun renderValue(
        value: Any?,
        scopes: Set<String>,
        path: List<String>,
    ): Any? =
        when (value) {
            is Map<*, *> -> renderMap(value, scopes, path)
            is List<*> -> value.map { renderValue(it, scopes, path) }
            is String -> if (isUntrustedText(path)) delimit(value) else value
            else -> value
        }

    private fun renderMap(
        value: Map<*, *>,
        scopes: Set<String>,
        path: List<String>,
    ): Map<String, Any?> =
        value.entries
            .mapNotNull { (rawKey, rawValue) ->
                val key = rawKey.toString()
                if (key in INVESTMENT_IDENTITY_FIELDS && INVESTMENT_READ_SCOPE !in scopes) {
                    return@mapNotNull null
                }
                key to renderValue(rawValue, scopes, path + key)
            }.toMap()

    private fun isUntrustedText(path: List<String>): Boolean {
        val key = path.lastOrNull() ?: return false
        if (key in UNTRUSTED_STRING_FIELDS) {
            return true
        }
        if (key == "name" && path.any { it in UNTRUSTED_NAME_CONTAINERS }) {
            return true
        }
        return false
    }

    private fun delimit(value: String): String = "$UNTRUSTED_START${escapeDelimiter(value)}$UNTRUSTED_END"

    private fun escapeDelimiter(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace(UNTRUSTED_START, "\\$UNTRUSTED_START")
            .replace(UNTRUSTED_END, "\\$UNTRUSTED_END")

    companion object {
        const val INVESTMENT_READ_SCOPE = "finance:investments:read"
        const val UNTRUSTED_START = "<untrusted-data>"
        const val UNTRUSTED_END = "</untrusted-data>"

        private val INVESTMENT_IDENTITY_FIELDS = setOf("linkedInvestmentAsset", "investmentTransactionId")
        private val UNTRUSTED_STRING_FIELDS =
            setOf(
                "description",
                "notes",
                "tags",
                "tag",
                "counterpartyName",
                "paymentReason",
                "merchantName",
                "issuerName",
                "ticker",
                "pattern",
                "descriptionOverride",
                "tagsOverride",
            )
        private val UNTRUSTED_NAME_CONTAINERS =
            setOf(
                "merchant",
                "category",
                "linkedInvestmentAsset",
                "financialAccounts",
                "categories",
                "transactionCategories",
            )
    }
}
