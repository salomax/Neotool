package io.github.salomax.neotool.graphql

import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment

/**
 * Utility functions for handling GraphQL arguments safely with elegant patterns.
 * Provides high-level abstractions to reduce repetitive argument validation code.
 */
object GraphQLArgumentUtils {
    
    /**
     * Get a required argument, throwing an exception if null
     */
    inline fun <reified T> getRequiredArgument(env: DataFetchingEnvironment, name: String): T {
        val value = env.getArgument<T>(name)
        return value ?: throw IllegalArgumentException("Required argument '$name' is missing")
    }
    
    /**
     * Get an optional argument, returning null if not present
     */
    inline fun <reified T> getOptionalArgument(env: DataFetchingEnvironment, name: String): T? {
        return env.getArgument<T>(name)
    }
    
    /**
     * Get an argument with a default value
     */
    inline fun <reified T> getArgumentOrDefault(env: DataFetchingEnvironment, name: String, defaultValue: T): T {
        return env.getArgument<T>(name) ?: defaultValue
    }
    
    /**
     * Get a required argument as String
     */
    fun getRequiredString(env: DataFetchingEnvironment, name: String): String {
        return getRequiredArgument<String>(env, name)
    }
    
    /**
     * Get an optional argument as String
     */
    fun getOptionalString(env: DataFetchingEnvironment, name: String): String? {
        return getOptionalArgument<String>(env, name)
    }
    
    /**
     * Get a required argument as Map
     */
    fun getRequiredMap(env: DataFetchingEnvironment, name: String): Map<String, Any?> {
        return getRequiredArgument<Map<String, Any?>>(env, name)
    }
    
    /**
     * Get an optional argument as Map
     */
    fun getOptionalMap(env: DataFetchingEnvironment, name: String): Map<String, Any?>? {
        return getOptionalArgument<Map<String, Any?>>(env, name)
    }

    // ===== HIGH-LEVEL PATTERNS FOR COMMON USE CASES =====

    /**
     * Extracts a required ID argument, throwing IllegalArgumentException if null or empty.
     * This is a specialized version for ID fields that are commonly used in CRUD operations.
     */
    fun getRequiredId(env: DataFetchingEnvironment, argumentName: String = "id"): String {
        val value = getRequiredString(env, argumentName)
        if (value.isBlank()) {
            throw IllegalArgumentException("$argumentName is required and cannot be empty")
        }
        return value
    }

    /**
     * Extracts a required input argument, throwing IllegalArgumentException if null.
     * This is a specialized version for input objects that are commonly used in mutations.
     */
    fun getRequiredInput(env: DataFetchingEnvironment, argumentName: String = "input"): Map<String, Any?> {
        return getRequiredMap(env, argumentName)
    }

    /**
     * Validates that all required arguments are present and not null/empty.
     * Throws IllegalArgumentException with a descriptive message if any validation fails.
     */
    fun validateRequiredArguments(env: DataFetchingEnvironment, vararg argumentNames: String) {
        val missingArgs = mutableListOf<String>()
        
        argumentNames.forEach { argName ->
            val value = env.getArgument<Any?>(argName)
            if (value == null || (value is String && value.isBlank())) {
                missingArgs.add(argName)
            }
        }
        
        if (missingArgs.isNotEmpty()) {
            throw IllegalArgumentException("Required arguments are missing or empty: ${missingArgs.joinToString(", ")}")
        }
    }

    // ===== DATA FETCHER FACTORY PATTERNS =====

    /**
     * Creates a data fetcher with automatic argument validation.
     * This reduces boilerplate by automatically validating required arguments.
     */
    fun <T> createValidatedDataFetcher(
        requiredArgs: List<String> = emptyList(),
        block: (DataFetchingEnvironment) -> T
    ): DataFetcher<T> {
        return DataFetcher { env ->
            validateRequiredArguments(env, *requiredArgs.toTypedArray())
            block(env)
        }
    }

    /**
     * Creates a CRUD data fetcher with automatic ID validation.
     * This is a specialized version for common CRUD operations.
     */
    fun <T> createCrudDataFetcher(
        operation: String,
        block: (String) -> T
    ): DataFetcher<T> {
        return createValidatedDataFetcher(listOf("id")) { env ->
            val id = getRequiredId(env)
            block(id)
        }
    }

    /**
     * Creates a mutation data fetcher with automatic input validation.
     * This is a specialized version for common mutation operations.
     */
    fun <T> createMutationDataFetcher(
        operation: String,
        block: (Map<String, Any?>) -> T
    ): DataFetcher<T> {
        return createValidatedDataFetcher(listOf("input")) { env ->
            val input = getRequiredInput(env)
            block(input)
        }
    }

    /**
     * Creates an update mutation data fetcher with automatic ID and input validation.
     * This is a specialized version for update operations that need both ID and input.
     */
    fun <T> createUpdateMutationDataFetcher(
        operation: String,
        block: (String, Map<String, Any?>) -> T
    ): DataFetcher<T> {
        return createValidatedDataFetcher(listOf("id", "input")) { env ->
            val id = getRequiredId(env)
            val input = getRequiredInput(env)
            block(id, input)
        }
    }
}
