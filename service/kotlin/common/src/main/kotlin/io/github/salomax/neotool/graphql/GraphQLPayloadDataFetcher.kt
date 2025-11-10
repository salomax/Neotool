package io.github.salomax.neotool.graphql

import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import io.github.salomax.neotool.graphql.payload.GraphQLPayload
import io.github.salomax.neotool.graphql.payload.SuccessPayload

/**
 * Utility object for creating GraphQL data fetchers that work with payloads.
 * 
 * This utility provides factory methods for creating data fetchers that
 * automatically handle [GraphQLPayload] responses, extracting data on success
 * or converting errors to GraphQL exceptions.
 * 
 * **Usage:**
 * ```kotlin
 * val createProductFetcher = GraphQLPayloadDataFetcher.createMutationDataFetcher(
 *     "createProduct"
 * ) { input ->
 *     productResolver.create(input)
 * }
 * 
 * // Register in wiring factory
 * type.dataFetcher("createProduct", createProductFetcher)
 * ```
 * 
 * **Features:**
 * - Automatic payload extraction (returns data from SuccessPayload)
 * - Error conversion (converts ErrorPayload to GraphQL exceptions)
 * - Input validation (validates required arguments)
 * - Type-safe data fetcher creation
 * 
 * **Payload Pattern:**
 * Mutations return `GraphQLPayload<T>` which includes both data and errors.
 * These fetchers automatically extract the data or throw exceptions for errors.
 */
object GraphQLPayloadDataFetcher {
    
    /**
     * Creates a data fetcher that extracts data from a GraphQL payload
     * This is useful when you want to return just the data part of a payload
     */
    fun <T> createPayloadDataFetcher(
        operation: String,
        block: (DataFetchingEnvironment) -> GraphQLPayload<T>
    ): DataFetcher<T?> {
        return DataFetcher { env ->
            val payload = block(env)
            when (payload) {
                is SuccessPayload -> payload.data
                else -> {
                    // For error payloads, we let GraphQL handle the error
                    // by throwing an exception that will be caught by the error handler
                    throw GraphQLPayloadException(payload.errors)
                }
            }
        }
    }
    
    /**
     * Creates a data fetcher that returns the full payload
     * This is useful when you want to return both data and errors
     */
    fun <T> createFullPayloadDataFetcher(
        operation: String,
        block: (DataFetchingEnvironment) -> GraphQLPayload<T>
    ): DataFetcher<GraphQLPayload<T>> {
        return DataFetcher { env ->
            block(env)
        }
    }
    
    /**
     * Creates a mutation data fetcher with automatic payload handling
     */
    fun <T> createMutationDataFetcher(
        operation: String,
        block: (Map<String, Any?>) -> GraphQLPayload<T>
    ): DataFetcher<T?> {
        return DataFetcher { env ->
            val input = env.getArgument<Map<String, Any?>>("input")
                ?: throw IllegalArgumentException("Input is required")
            val payload = block(input)
            when (payload) {
                is SuccessPayload -> payload.data
                else -> throw GraphQLPayloadException(payload.errors)
            }
        }
    }
    
    /**
     * Creates an update mutation data fetcher with automatic payload handling
     */
    fun <T> createUpdateMutationDataFetcher(
        operation: String,
        block: (String, Map<String, Any?>) -> GraphQLPayload<T>
    ): DataFetcher<T?> {
        return DataFetcher { env ->
            val id = env.getArgument<String>("id")
                ?: throw IllegalArgumentException("ID is required")
            val input = env.getArgument<Map<String, Any?>>("input")
                ?: throw IllegalArgumentException("Input is required")
            val payload = block(id, input)
            when (payload) {
                is SuccessPayload -> payload.data
                else -> throw GraphQLPayloadException(payload.errors)
            }
        }
    }
    
    /**
     * Creates a CRUD data fetcher with automatic payload handling
     */
    fun <T> createCrudDataFetcher(
        operation: String,
        block: (String) -> GraphQLPayload<T>
    ): DataFetcher<T?> {
        return DataFetcher { env ->
            val id = env.getArgument<String>("id")
                ?: throw IllegalArgumentException("ID is required")
            val payload = block(id)
            when (payload) {
                is SuccessPayload -> payload.data
                else -> throw GraphQLPayloadException(payload.errors)
            }
        }
    }
}

/**
 * Exception thrown when a GraphQL payload contains errors.
 * 
 * This exception is used internally by [GraphQLPayloadDataFetcher] to convert
 * [GraphQLPayload] error payloads into GraphQL exceptions that can be properly
 * handled by the GraphQL error handling system.
 * 
 * **Note:** You typically don't need to catch or throw this exception directly.
 * It's automatically used by the payload data fetcher utilities.
 */
class GraphQLPayloadException(
    val errors: List<io.github.salomax.neotool.graphql.payload.GraphQLError>
) : RuntimeException("GraphQL operation failed: ${errors.joinToString { it.message }}") {
    
    /**
     * Convert the first error to a simple message for GraphQL
     */
    override val message: String
        get() = errors.firstOrNull()?.message ?: "An error occurred"
}
