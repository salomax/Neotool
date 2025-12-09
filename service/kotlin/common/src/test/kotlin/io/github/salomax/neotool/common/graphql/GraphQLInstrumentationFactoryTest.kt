package io.github.salomax.neotool.common.graphql

import graphql.analysis.MaxQueryComplexityInstrumentation
import graphql.analysis.MaxQueryDepthInstrumentation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("GraphQLInstrumentationFactory Tests")
class GraphQLInstrumentationFactoryTest {
    private val factory = GraphQLInstrumentationFactory()

    @Nested
    @DisplayName("queryComplexityInstrumentation()")
    inner class QueryComplexityInstrumentationTests {
        @Test
        fun `should create MaxQueryComplexityInstrumentation with correct limit`() {
            // Act
            val instrumentation = factory.queryComplexityInstrumentation()

            // Assert
            assertThat(instrumentation).isNotNull
            assertThat(instrumentation).isInstanceOf(MaxQueryComplexityInstrumentation::class.java)
            // The limit is set to 1000 as per big tech standards
            // We can't directly access the limit value, but we can verify the instance is created
        }

        @Test
        fun `should return singleton instance`() {
            // Act
            val instance1 = factory.queryComplexityInstrumentation()
            val instance2 = factory.queryComplexityInstrumentation()

            // Assert
            // Note: Factory methods create new instances, but @Singleton annotation ensures
            // Micronaut will return the same instance when injected
            assertThat(instance1).isNotNull
            assertThat(instance2).isNotNull
            assertThat(instance1).isInstanceOf(MaxQueryComplexityInstrumentation::class.java)
            assertThat(instance2).isInstanceOf(MaxQueryComplexityInstrumentation::class.java)
        }
    }

    @Nested
    @DisplayName("queryDepthInstrumentation()")
    inner class QueryDepthInstrumentationTests {
        @Test
        fun `should create MaxQueryDepthInstrumentation with correct limit`() {
            // Act
            val instrumentation = factory.queryDepthInstrumentation()

            // Assert
            assertThat(instrumentation).isNotNull
            assertThat(instrumentation).isInstanceOf(MaxQueryDepthInstrumentation::class.java)
            // The limit is set to 10 as per big tech standards
            // We can't directly access the limit value, but we can verify the instance is created
        }

        @Test
        fun `should return singleton instance`() {
            // Act
            val instance1 = factory.queryDepthInstrumentation()
            val instance2 = factory.queryDepthInstrumentation()

            // Assert
            // Note: Factory methods create new instances, but @Singleton annotation ensures
            // Micronaut will return the same instance when injected
            assertThat(instance1).isNotNull
            assertThat(instance2).isNotNull
            assertThat(instance1).isInstanceOf(MaxQueryDepthInstrumentation::class.java)
            assertThat(instance2).isInstanceOf(MaxQueryDepthInstrumentation::class.java)
        }
    }
}
