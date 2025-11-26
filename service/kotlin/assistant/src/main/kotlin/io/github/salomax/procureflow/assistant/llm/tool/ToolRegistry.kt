package io.github.salomax.neotool.assistant.llm.tool

import io.github.salomax.neotool.assistant.llm.FunctionDefinition
import jakarta.inject.Singleton

@Singleton
class ToolRegistry(
    private val catalogTool: CatalogTool,
    private val checkoutTool: CheckoutTool,
) {
    fun getAllFunctionDefinitions(): List<FunctionDefinition> {
        return listOf(
            catalogTool.getSearchFunction(),
            catalogTool.getItemFunction(),
            catalogTool.getSaveFunction(),
            checkoutTool.getCheckoutFunction(),
        )
    }

    fun getToolByName(name: String): Tool? {
        return when (name) {
            "searchCatalogItems" -> catalogTool
            "catalogItem" -> catalogTool
            "saveCatalogItem" -> catalogTool
            "checkout" -> checkoutTool
            else -> null
        }
    }
}

interface Tool {
    fun execute(
        functionName: String,
        arguments: Map<String, Any?>,
    ): ToolResult
}

data class ToolResult(
    val success: Boolean,
    val data: Any? = null,
    val error: String? = null,
)
