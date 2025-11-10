package io.github.salomax.neotool.graphql

import graphql.analysis.MaxQueryComplexityInstrumentation
import graphql.analysis.MaxQueryDepthInstrumentation
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton

/**
 * Factory for creating GraphQL instrumentation beans.
 * 
 * This factory provides standard GraphQL instrumentation following industry
 * best practices for query complexity and depth limits. These instruments
 * help protect against malicious or inefficient queries.
 * 
 * **Note:** [BaseGraphQLFactory] already includes these instruments with
 * standard limits. This factory is provided for cases where you need to
 * customize instrumentation or use it outside of BaseGraphQLFactory.
 * 
 * **Instruments Provided:**
 * - Query Complexity: Limits the computational complexity of queries (default: 1000)
 * - Query Depth: Prevents deeply nested queries (default: 10 levels)
 * 
 * **Usage:**
 * ```kotlin
 * val graphQL = GraphQL.newGraphQL(schema)
 *     .instrumentation(instrumentationFactory.queryComplexityInstrumentation())
 *     .instrumentation(instrumentationFactory.queryDepthInstrumentation())
 *     .build()
 * ```
 * 
 * **Best Practices:**
 * - Complexity limit: 1000-5000 for production (big tech standard)
 * - Depth limit: 10-15 levels (prevents deeply nested queries)
 * - Adjust based on your schema complexity and performance requirements
 */
@Factory
class GraphQLInstrumentationFactory {

    @Bean
    @Singleton
    fun queryComplexityInstrumentation(): MaxQueryComplexityInstrumentation {
        // Big tech companies typically use 1000-5000 as complexity limit
        return MaxQueryComplexityInstrumentation(1000)
    }

    @Bean
    @Singleton
    fun queryDepthInstrumentation(): MaxQueryDepthInstrumentation {
        // Prevent deeply nested queries (big tech standard: 10-15 levels)
        return MaxQueryDepthInstrumentation(10)
    }
}
