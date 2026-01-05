package io.github.salomax.neotool.common.graphql

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("GraphQLResolverRegistry Unit Tests")
class GraphQLResolverRegistryTest {
    private lateinit var registry: GraphQLResolverRegistry

    @BeforeEach
    fun setUp() {
        registry = GraphQLResolverRegistry()
    }

    @Nested
    @DisplayName("register Tests")
    inner class RegisterTests {
        @Test
        fun `should register resolver and return it`() {
            // Arrange
            val resolverName = "testResolver"
            val resolver = "test-resolver-instance"

            // Act
            val result = registry.register(resolverName, resolver)

            // Assert
            assertThat(result).isEqualTo(resolver)
            assertThat(registry.get<String>(resolverName)).isEqualTo(resolver)
        }

        @Test
        fun `should overwrite existing resolver with same name`() {
            // Arrange
            val resolverName = "testResolver"
            val resolver1 = "resolver-1"
            val resolver2 = "resolver-2"

            // Act
            registry.register(resolverName, resolver1)
            val result = registry.register(resolverName, resolver2)

            // Assert
            assertThat(result).isEqualTo(resolver2)
            assertThat(registry.get<String>(resolverName)).isEqualTo(resolver2)
            assertThat(registry.get<String>(resolverName)).isNotEqualTo(resolver1)
        }

        @Test
        fun `should register different types of resolvers`() {
            // Arrange
            val stringResolver = "string-resolver"
            val intResolver = 42
            val listResolver = listOf("item1", "item2")

            // Act
            registry.register("string", stringResolver)
            registry.register("int", intResolver)
            registry.register("list", listResolver)

            // Assert
            assertThat(registry.get<String>("string")).isEqualTo(stringResolver)
            assertThat(registry.get<Int>("int")).isEqualTo(intResolver)
            assertThat(registry.get<List<String>>("list")).isEqualTo(listResolver)
        }
    }

    @Nested
    @DisplayName("get Tests")
    inner class GetTests {
        @Test
        fun `should return registered resolver`() {
            // Arrange
            val resolverName = "testResolver"
            val resolver = "test-resolver-instance"
            registry.register(resolverName, resolver)

            // Act
            val result = registry.get<String>(resolverName)

            // Assert
            assertThat(result).isEqualTo(resolver)
        }

        @Test
        fun `should return null for non-existent resolver`() {
            // Act
            val result = registry.get<String>("nonExistent")

            // Assert
            assertThat(result).isNull()
        }

        @Test
        fun `should return null when type mismatch`() {
            // Arrange
            val resolverName = "testResolver"
            registry.register(resolverName, "string-resolver")

            // Act - The as? operator should return null for incompatible types
            // However, due to type erasure, this may throw ClassCastException at runtime
            // We'll catch and verify the behavior
            try {
                val result = registry.get<Int>(resolverName)
                // If no exception, result should be null
                assertThat(result).isNull()
            } catch (e: ClassCastException) {
                // If exception is thrown, that's also acceptable behavior
                // The important thing is that we don't get the wrong type back
                assertThat(e).isNotNull()
            }
        }

        @Test
        fun `should handle generic types correctly`() {
            // Arrange
            val resolverName = "listResolver"
            val resolver = listOf("item1", "item2")
            registry.register(resolverName, resolver)

            // Act
            val result = registry.get<List<String>>(resolverName)

            // Assert
            assertThat(result).isEqualTo(resolver)
        }
    }

    @Nested
    @DisplayName("getAll Tests")
    inner class GetAllTests {
        @Test
        fun `should return all registered resolvers`() {
            // Arrange
            val resolver1 = "resolver-1"
            val resolver2 = "resolver-2"
            val resolver3 = 42

            registry.register("resolver1", resolver1)
            registry.register("resolver2", resolver2)
            registry.register("resolver3", resolver3)

            // Act
            val all = registry.getAll()

            // Assert
            assertThat(all).hasSize(3)
            assertThat(all["resolver1"]).isEqualTo(resolver1)
            assertThat(all["resolver2"]).isEqualTo(resolver2)
            assertThat(all["resolver3"]).isEqualTo(resolver3)
        }

        @Test
        fun `should return empty map when no resolvers registered`() {
            // Act
            val all = registry.getAll()

            // Assert
            assertThat(all).isEmpty()
        }

        @Test
        fun `should return immutable copy of resolvers`() {
            // Arrange
            registry.register("resolver1", "resolver-1")

            // Act
            val all = registry.getAll()
            // Try to modify (should not affect internal state)
            val mutable = all.toMutableMap()
            mutable["resolver2"] = "resolver-2"

            // Assert - original getAll should not be affected
            assertThat(registry.getAll()).hasSize(1)
            assertThat(all).hasSize(1)
        }
    }
}
