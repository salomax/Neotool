package io.github.salomax.neotool.framework.graphql

import graphql.GraphQL
import io.micronaut.http.MediaType.APPLICATION_JSON
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Post
import graphql.language.OperationDefinition
import graphql.parser.Parser
import graphql.execution.UnknownOperationException

/**
 * Base GraphQL HTTP endpoint controller for federated services.
 * 
 * This controller provides a standard GraphQL HTTP endpoint (`/graphql`) that
 * follows GraphQL specification best practices. It's designed to be reusable
 * across all service modules in a federated architecture.
 * 
 * **Key Design Decisions:**
 * - Always returns GraphQL spec-compliant responses: `{"data": ..., "errors": ...}`
 * - HTTP status is always 200 (even for errors) - errors are in the response body
 * - Validates operationName before execution to prevent exceptions
 * - Extracts and passes JWT tokens from Authorization header to GraphQL context
 * - Converts all exceptions to GraphQL error format
 * 
 * **Usage:**
 * Each service automatically gets a `/graphql` endpoint by injecting the GraphQL bean:
 * ```kotlin
 * @Controller("/graphql")
 * class GraphQLController(graphQL: GraphQL) : GraphQLControllerBase(graphQL)
 * ```
 * 
 * **Request Format:**
 * ```json
 * POST /graphql
 * {
 *   "query": "query { products { id name } }",
 *   "variables": {},
 *   "operationName": "GetProducts"
 * }
 * ```
 * 
 * **Response Format:**
 * ```json
 * {
 *   "data": { "products": [...] },
 *   "errors": []
 * }
 * ```
 * 
 * **Authentication:**
 * The controller extracts JWT tokens from the `Authorization: Bearer <token>` header
 * and makes them available in the GraphQL context as `context.token`.
 * 
 * **Error Handling:**
 * All errors (syntax, validation, execution) are returned as GraphQL errors
 * in the response body, not as HTTP error status codes. This follows GraphQL
 * specification and allows clients to handle errors uniformly.
 */
@Controller("/graphql")
open class GraphQLControllerBase(private val graphQL: GraphQL) {

  /**
   * Accepts a standard GraphQL request payload.
   *
   * Behavior:
   *  - If the query is empty, return a spec-like error response (data=null, errors=[...]).
   *    We prefer a GraphQL "errors" payload over an HTTP 400 to keep client handling uniform.
   *  - If operationName is provided, ensure it exists in the document before executing.
   *    This avoids graphql-java throwing UnknownOperationException and producing server logs.
   *  - Execute the request and convert the result to the GraphQL spec representation
   *    using toSpecification(), ensuring the payload is JSON-serializable without extra annotations.
   */
  @Post(consumes = [APPLICATION_JSON], produces = [APPLICATION_JSON])
  open fun post(
    @Body request: GraphQLRequest,
    @Header("Authorization") authorization: String?
  ): Map<String, Any?> {

    // Guard: null, empty, or blank queries are invalid per GraphQL usage.
    // We normalize this into a GraphQL error response (200 + errors) instead of HTTP 400,
    // so clients can always parse "data"/"errors" consistently.
    if (request.query == null || request.query.isBlank()) {
      return errorSpec("Query must not be empty")
    }

    // Extract token from Authorization header (Bearer <token>)
    val token = authorization?.removePrefix("Bearer ")?.takeIf { it.isNotBlank() }

    // Build the ExecutionInput incrementally so we can attach operationName conditionally.
    val execution = graphql.ExecutionInput
      .newExecutionInput()
      .query(request.query)
      // Variables default to an empty map to avoid NPEs and match common server behavior
      .variables(request.variables ?: emptyMap())
      // Build GraphQL context with token using builder function
      // Only add token if it's not null (GraphQL context doesn't allow null values)
      .graphQLContext { builder ->
        if (token != null) {
          builder.of("token", token)
        }
      }

    // Only set operationName if present and non-blank.
    // Additionally, we pre-validate that the named operation exists in the document.
    // Rationale: graphql-java throws UnknownOperationException otherwise,
    // which would show up as server errors rather than spec-like errors.
    request.operationName?.takeIf { it.isNotBlank() }?.let {
      if (!operationExists(request.query, it)) {
        return errorSpec("Unknown operation named '$it'")
      }
      execution.operationName(it)
    }

    // Execute and normalize:
    //  - toSpecification() returns a Map<String, Any?> with "data"/"errors"/"extensions",
    //    avoiding the need to @SerdeImport graphql-java internal types for JSON encoding.
    return try {
      val result = graphQL.execute(execution.build())
      result.toSpecification()
    } catch (e: graphql.parser.InvalidSyntaxException) {
      // Handle GraphQL syntax errors - these should be returned as GraphQL errors, not HTTP errors
      errorSpec("Invalid GraphQL syntax: ${e.message ?: "Syntax error"}")
    } catch (e: UnknownOperationException) {
      // Defensive catch: if graphql-java still throws (e.g., due to unusual edge cases),
      // convert it to a GraphQL "errors" payload rather than surfacing an HTTP error.
      errorSpec(e.message ?: "Unknown operation")
    } catch (e: graphql.AssertException) {
      // Handle cases where required GraphQL types are missing
      errorSpec("The type is not defined: ${e.message ?: "Unknown error"}")
    } catch (e: graphql.GraphQLException) {
      // Handle other GraphQL-specific exceptions
      errorSpec("GraphQL error: ${e.message ?: "Unknown error"}")
    } catch (e: Exception) {
      // Log unexpected exceptions (should be rare since GraphQL.execute() typically returns errors in result)
      val logger = org.slf4j.LoggerFactory.getLogger(GraphQLControllerBase::class.java)
      logger.error("GraphQL execution threw exception (unexpected): ${e.javaClass.simpleName} - ${e.message}", e)
      // Catch any other unexpected exceptions and convert to GraphQL error format
      errorSpec("GraphQL execution failed: ${e.message ?: "Unknown error"}")
    }
  }

  /**
   * Checks whether a given operation name exists within the GraphQL document.
   * We parse the document and scan OperationDefinition nodes for the requested name.
   * This avoids throwing exceptions at execution time and lets us return a clean "errors" array instead.
   */
  private fun operationExists(query: String, op: String): Boolean {
    return try {
      val doc = Parser().parseDocument(query)
      doc.definitions
        .filterIsInstance<OperationDefinition>()
        .any { it.name == op }
    } catch (e: Exception) {
      // If parsing fails, the operation doesn't exist
      false
    }
  }

  /**
   * Utility to build a spec-like error response:
   *  {
   *    "data": null,
   *    "errors": [ { "message": "<message>" } ]
   *  }
   * This format is what most GraphQL clients expect and can uniformly handle.
   */
  private fun errorSpec(message: String) =
    mapOf("data" to null, "errors" to listOf(mapOf("message" to message)))
}
