package io.github.salomax.neotool.graphql

import graphql.analysis.MaxQueryComplexityInstrumentation
import graphql.analysis.MaxQueryDepthInstrumentation
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton

/**
 * GraphQL instrumentation factory following big tech best practices
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
