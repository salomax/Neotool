package io.github.salomax.neotool.assistant.llm.tool

import io.github.salomax.neotool.assistant.graphql.GraphQLClient
import io.github.salomax.neotool.assistant.graphql.GraphQLRequest
import io.github.salomax.neotool.assistant.llm.FunctionDefinition
import io.github.salomax.neotool.assistant.llm.FunctionParameters
import io.github.salomax.neotool.assistant.llm.PropertyDefinition
import jakarta.inject.Singleton
import kotlinx.coroutines.runBlocking

@Singleton
class CheckoutTool(
    private val graphQLClient: GraphQLClient,
) : Tool {
    fun getCheckoutFunction(): FunctionDefinition {
        return FunctionDefinition(
            name = "checkout",
            description =
                "Process a checkout for catalog items. Use this when the user wants to buy or checkout items. " +
                    "The items array should contain catalogItemId, name, priceCents, and quantity for each item.",
            parameters =
                FunctionParameters(
                    properties =
                        mapOf(
                            "items" to
                                PropertyDefinition(
                                    type = "array",
                                    description =
                                        "Array of items to checkout. " +
                                            "Each item must have catalogItemId, name, priceCents, and quantity.",
                                ),
                            "totalPriceCents" to
                                PropertyDefinition(
                                    type = "integer",
                                    description = "Total price of all items in cents",
                                ),
                            "itemCount" to
                                PropertyDefinition(
                                    type = "integer",
                                    description = "Total number of items (sum of all quantities)",
                                ),
                        ),
                    required = listOf("items", "totalPriceCents", "itemCount"),
                ),
        )
    }

    override fun execute(
        functionName: String,
        arguments: Map<String, Any?>,
    ): ToolResult {
        return runBlocking {
            try {
                if (functionName != "checkout") {
                    return@runBlocking ToolResult(false, error = "Unknown function: $functionName")
                }

                val items =
                    arguments["items"] as? List<*>
                        ?: return@runBlocking ToolResult(false, error = "Missing 'items' parameter")
                val totalPriceCents =
                    (arguments["totalPriceCents"] as? Number)?.toInt()
                        ?: return@runBlocking ToolResult(false, error = "Missing 'totalPriceCents' parameter")
                val itemCount =
                    (arguments["itemCount"] as? Number)?.toInt()
                        ?: return@runBlocking ToolResult(false, error = "Missing 'itemCount' parameter")

                val graphQLMutation =
                    """
                    mutation Checkout(${'$'}input: CheckoutInput!) {
                        checkout(input: ${'$'}input) {
                            id
                            userId
                            items {
                                catalogItemId
                                name
                                priceCents
                                quantity
                            }
                            totalPriceCents
                            itemCount
                            status
                            createdAt
                        }
                    }
                    """.trimIndent()

                val input =
                    mapOf(
                        "items" to items,
                        "totalPriceCents" to totalPriceCents,
                        "itemCount" to itemCount,
                    )

                val request =
                    GraphQLRequest(
                        query = graphQLMutation,
                        variables = mapOf("input" to input),
                    )

                val response = graphQLClient.execute(request)
                if (response.errors != null && response.errors!!.isNotEmpty()) {
                    val errorMessages =
                        response.errors!!.joinToString(
                            ", ",
                        ) { it["message"]?.toString() ?: "Unknown error" }
                    return@runBlocking ToolResult(false, error = errorMessages)
                }
                ToolResult(true, data = response.data?.get("checkout"))
            } catch (e: Exception) {
                ToolResult(false, error = e.message ?: "Unknown error")
            }
        }
    }
}
