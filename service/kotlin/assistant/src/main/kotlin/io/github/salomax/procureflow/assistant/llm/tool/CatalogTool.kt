package io.github.salomax.neotool.assistant.llm.tool

import io.github.salomax.neotool.assistant.graphql.GraphQLClient
import io.github.salomax.neotool.assistant.graphql.GraphQLRequest
import io.github.salomax.neotool.assistant.llm.FunctionDefinition
import io.github.salomax.neotool.assistant.llm.FunctionParameters
import io.github.salomax.neotool.assistant.llm.PropertyDefinition
import jakarta.inject.Singleton
import kotlinx.coroutines.runBlocking
import java.text.NumberFormat
import java.util.Locale

@Singleton
class CatalogTool(
    private val graphQLClient: GraphQLClient
) : Tool {
    
    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US)
    
    /**
     * Formats price in cents to currency string (e.g., 1500 -> "$15.00")
     */
    private fun formatPrice(priceCents: Any?): String {
        val cents = when (priceCents) {
            is Number -> priceCents.toInt()
            is String -> priceCents.toIntOrNull() ?: 0
            else -> 0
        }
        return currencyFormatter.format(cents / 100.0)
    }
    
    /**
     * Transforms catalog item data by converting priceCents to formatted price string
     */
    private fun transformCatalogItem(item: Any?): Any? {
        if (item !is Map<*, *>) return item
        
        val mutableMap = item.toMutableMap()
        val priceCents = mutableMap["priceCents"]
        // Always add price field - format null as $0.00
        mutableMap["price"] = formatPrice(priceCents)
        // Keep priceCents for backward compatibility but add formatted price
        return mutableMap
    }
    
    /**
     * Transforms a list of catalog items
     */
    private fun transformCatalogItems(items: Any?): Any? {
        return when (items) {
            is List<*> -> items.map { transformCatalogItem(it) }
            else -> transformCatalogItem(items)
        }
    }
    
    fun getSearchFunction(): FunctionDefinition {
        return FunctionDefinition(
            name = "searchCatalogItems",
            description = "Search for catalog items by name or query string. Use this when the user wants to find items in the catalog.",
            parameters = FunctionParameters(
                properties = mapOf(
                    "query" to PropertyDefinition(
                        type = "string",
                        description = "The search query string to find catalog items"
                    )
                ),
                required = listOf("query")
            )
        )
    }
    
    fun getItemFunction(): FunctionDefinition {
        return FunctionDefinition(
            name = "catalogItem",
            description = "Get detailed information about a specific catalog item by its ID. Use this when the user asks about a specific item's details, price, status, or description.",
            parameters = FunctionParameters(
                properties = mapOf(
                    "id" to PropertyDefinition(
                        type = "string",
                        description = "The unique identifier (ID) of the catalog item"
                    )
                ),
                required = listOf("id")
            )
        )
    }
    
    fun getSaveFunction(): FunctionDefinition {
        return FunctionDefinition(
            name = "saveCatalogItem",
            description = "Save or enroll a new catalog item. Use this when the user wants to add a new item to the catalog.",
            parameters = FunctionParameters(
                properties = mapOf(
                    "name" to PropertyDefinition(
                        type = "string",
                        description = "The name of the catalog item"
                    ),
                    "category" to PropertyDefinition(
                        type = "string",
                        description = "The category of the item",
                        enum = listOf("MATERIAL", "SERVICE")
                    ),
                    "priceCents" to PropertyDefinition(
                        type = "integer",
                        description = "The price of the item in cents"
                    ),
                    "status" to PropertyDefinition(
                        type = "string",
                        description = "The status of the item",
                        enum = listOf("ACTIVE", "PENDING_APPROVAL", "INACTIVE")
                    ),
                    "description" to PropertyDefinition(
                        type = "string",
                        description = "Optional description of the item"
                    )
                ),
                required = listOf("name", "category", "priceCents", "status")
            )
        )
    }
    
    override fun execute(functionName: String, arguments: Map<String, Any?>): ToolResult {
        return runBlocking {
            try {
                when (functionName) {
                    "searchCatalogItems" -> {
                        val query = arguments["query"] as? String
                            ?: return@runBlocking ToolResult(false, error = "Missing 'query' parameter")
                        
                        val graphQLQuery = """
                            query SearchCatalogItems(${'$'}query: String!) {
                                searchCatalogItems(query: ${'$'}query) {
                                    id
                                    name
                                    category
                                    priceCents
                                    status
                                    description
                                    createdAt
                                    updatedAt
                                }
                            }
                        """.trimIndent()
                        
                        val request = GraphQLRequest(
                            query = graphQLQuery,
                            variables = mapOf("query" to query)
                        )
                        
                        val response = graphQLClient.execute(request)
                        if (response.errors != null && response.errors!!.isNotEmpty()) {
                            val errorMessages = response.errors!!.joinToString(", ") { it["message"]?.toString() ?: "Unknown error" }
                            return@runBlocking ToolResult(false, error = errorMessages)
                        }
                        val rawData = response.data?.get("searchCatalogItems")
                        val transformedData = transformCatalogItems(rawData)
                        ToolResult(true, data = transformedData)
                    }
                    
                    "catalogItem" -> {
                        val id = arguments["id"] as? String
                            ?: return@runBlocking ToolResult(false, error = "Missing 'id' parameter")
                        
                        val graphQLQuery = """
                            query GetCatalogItem(${'$'}id: ID!) {
                                catalogItem(id: ${'$'}id) {
                                    id
                                    name
                                    category
                                    priceCents
                                    status
                                    description
                                    createdAt
                                    updatedAt
                                }
                            }
                        """.trimIndent()
                        
                        val request = GraphQLRequest(
                            query = graphQLQuery,
                            variables = mapOf("id" to id)
                        )
                        
                        val response = graphQLClient.execute(request)
                        if (response.errors != null && response.errors!!.isNotEmpty()) {
                            val errorMessages = response.errors!!.joinToString(", ") { it["message"]?.toString() ?: "Unknown error" }
                            return@runBlocking ToolResult(false, error = errorMessages)
                        }
                        val rawData = response.data?.get("catalogItem")
                        val transformedData = transformCatalogItem(rawData)
                        ToolResult(true, data = transformedData)
                    }
                    
                    "saveCatalogItem" -> {
                        val name = arguments["name"] as? String
                            ?: return@runBlocking ToolResult(false, error = "Missing 'name' parameter")
                        val category = arguments["category"] as? String
                            ?: return@runBlocking ToolResult(false, error = "Missing 'category' parameter")
                        val priceCents = (arguments["priceCents"] as? Number)?.toInt()
                            ?: return@runBlocking ToolResult(false, error = "Missing 'priceCents' parameter")
                        val status = arguments["status"] as? String
                            ?: return@runBlocking ToolResult(false, error = "Missing 'status' parameter")
                        val description = arguments["description"] as? String
                        
                        val graphQLMutation = """
                            mutation SaveCatalogItem(${'$'}input: CatalogItemInput!) {
                                saveCatalogItem(input: ${'$'}input) {
                                    id
                                    name
                                    category
                                    priceCents
                                    status
                                    description
                                    createdAt
                                    updatedAt
                                }
                            }
                        """.trimIndent()
                        
                        val input = mapOf(
                            "name" to name,
                            "category" to category,
                            "priceCents" to priceCents,
                            "status" to status,
                            "description" to description
                        )
                        
                        val request = GraphQLRequest(
                            query = graphQLMutation,
                            variables = mapOf("input" to input)
                        )
                        
                        val response = graphQLClient.execute(request)
                        if (response.errors != null && response.errors!!.isNotEmpty()) {
                            val errorMessages = response.errors!!.joinToString(", ") { it["message"]?.toString() ?: "Unknown error" }
                            return@runBlocking ToolResult(false, error = errorMessages)
                        }
                        val rawData = response.data?.get("saveCatalogItem")
                        val transformedData = transformCatalogItem(rawData)
                        ToolResult(true, data = transformedData)
                    }
                    
                    else -> ToolResult(false, error = "Unknown function: $functionName")
                }
            } catch (e: Exception) {
                ToolResult(false, error = e.message ?: "Unknown error")
            }
        }
    }
}

