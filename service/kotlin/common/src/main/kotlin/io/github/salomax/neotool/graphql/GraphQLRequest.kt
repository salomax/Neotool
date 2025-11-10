package io.github.salomax.neotool.framework.graphql

import io.micronaut.serde.annotation.Serdeable
import io.micronaut.core.annotation.Introspected

/**
 * GraphQL request payload DTO following the GraphQL over HTTP specification.
 * 
 * This data class represents the standard GraphQL request format that clients
 * send to the `/graphql` endpoint. It matches the GraphQL over HTTP specification
 * and is automatically deserialized from JSON by Micronaut.
 * 
 * **Request Format:**
 * ```json
 * {
 *   "query": "query GetProducts { products { id name } }",
 *   "variables": { "limit": 10 },
 *   "operationName": "GetProducts"
 * }
 * ```
 * 
 * **Fields:**
 * - `query`: The GraphQL query/mutation/subscription string (required)
 * - `variables`: Map of variable values for parameterized queries (optional)
 * - `operationName`: Name of the operation to execute if query contains multiple operations (optional)
 * 
 * **Usage:**
 * This class is automatically used by [GraphQLControllerBase] to deserialize
 * incoming GraphQL requests. You typically don't need to interact with it directly.
 * 
 * **Example:**
 * ```kotlin
 * // Client sends:
 * POST /graphql
 * {
 *   "query": "mutation { createProduct(input: {name: \"Widget\"}) { id } }"
 * }
 * 
 * // GraphQLControllerBase receives it as GraphQLRequest
 * ```
 */
@Serdeable
@Introspected
data class GraphQLRequest(
  val query: String? = null,
  val variables: Map<String, Any?>? = null,
  val operationName: String? = null
)
