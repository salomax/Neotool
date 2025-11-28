package io.github.salomax.neotool.common.graphql

import graphql.schema.DataFetchingEnvironment
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@DisplayName("GraphQLArgumentUtils Tests")
class GraphQLArgumentUtilsTest {
    private lateinit var env: DataFetchingEnvironment

    @Nested
    @DisplayName("getRequiredArgument()")
    inner class GetRequiredArgumentTests {
        @Test
        fun `should return argument when present`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<String>("name")).thenReturn("John")

            // Act
            val result = GraphQLArgumentUtils.getRequiredArgument<String>(env, "name")

            // Assert
            assertThat(result).isEqualTo("John")
        }

        @Test
        fun `should throw exception when argument is missing`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<String>("name")).thenReturn(null)

            // Act & Assert
            val exception =
                assertThrows<IllegalArgumentException> {
                    GraphQLArgumentUtils.getRequiredArgument<String>(env, "name")
                }

            assertThat(exception.message).isEqualTo("Required argument 'name' is missing")
        }
    }

    @Nested
    @DisplayName("getOptionalArgument()")
    inner class GetOptionalArgumentTests {
        @Test
        fun `should return argument when present`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<String>("name")).thenReturn("John")

            // Act
            val result = GraphQLArgumentUtils.getOptionalArgument<String>(env, "name")

            // Assert
            assertThat(result).isEqualTo("John")
        }

        @Test
        fun `should return null when argument is missing`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<String>("name")).thenReturn(null)

            // Act
            val result = GraphQLArgumentUtils.getOptionalArgument<String>(env, "name")

            // Assert
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("getArgumentOrDefault()")
    inner class GetArgumentOrDefaultTests {
        @Test
        fun `should return argument when present`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<String>("name")).thenReturn("John")

            // Act
            val result = GraphQLArgumentUtils.getArgumentOrDefault(env, "name", "Default")

            // Assert
            assertThat(result).isEqualTo("John")
        }

        @Test
        fun `should return default when argument is missing`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<String>("name")).thenReturn(null)

            // Act
            val result = GraphQLArgumentUtils.getArgumentOrDefault(env, "name", "Default")

            // Assert
            assertThat(result).isEqualTo("Default")
        }
    }

    @Nested
    @DisplayName("getRequiredString()")
    inner class GetRequiredStringTests {
        @Test
        fun `should return string argument when present`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<String>("name")).thenReturn("John")

            // Act
            val result = GraphQLArgumentUtils.getRequiredString(env, "name")

            // Assert
            assertThat(result).isEqualTo("John")
        }

        @Test
        fun `should throw exception when string argument is missing`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<String>("name")).thenReturn(null)

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                GraphQLArgumentUtils.getRequiredString(env, "name")
            }
        }
    }

    @Nested
    @DisplayName("getOptionalString()")
    inner class GetOptionalStringTests {
        @Test
        fun `should return string argument when present`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<String>("name")).thenReturn("John")

            // Act
            val result = GraphQLArgumentUtils.getOptionalString(env, "name")

            // Assert
            assertThat(result).isEqualTo("John")
        }

        @Test
        fun `should return null when string argument is missing`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<String>("name")).thenReturn(null)

            // Act
            val result = GraphQLArgumentUtils.getOptionalString(env, "name")

            // Assert
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("getRequiredMap()")
    inner class GetRequiredMapTests {
        @Test
        fun `should return map argument when present`() {
            // Arrange
            env = mock()
            val map = mapOf("key" to "value")
            whenever(env.getArgument<Map<String, Any?>>("input")).thenReturn(map)

            // Act
            val result = GraphQLArgumentUtils.getRequiredMap(env, "input")

            // Assert
            assertThat(result).isEqualTo(map)
        }

        @Test
        fun `should throw exception when map argument is missing`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<Map<String, Any?>>("input")).thenReturn(null)

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                GraphQLArgumentUtils.getRequiredMap(env, "input")
            }
        }
    }

    @Nested
    @DisplayName("getOptionalMap()")
    inner class GetOptionalMapTests {
        @Test
        fun `should return map argument when present`() {
            // Arrange
            env = mock()
            val map = mapOf("key" to "value")
            whenever(env.getArgument<Map<String, Any?>>("input")).thenReturn(map)

            // Act
            val result = GraphQLArgumentUtils.getOptionalMap(env, "input")

            // Assert
            assertThat(result).isEqualTo(map)
        }

        @Test
        fun `should return null when map argument is missing`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<Map<String, Any?>>("input")).thenReturn(null)

            // Act
            val result = GraphQLArgumentUtils.getOptionalMap(env, "input")

            // Assert
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("getRequiredId()")
    inner class GetRequiredIdTests {
        @Test
        fun `should return ID when present`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<String>("id")).thenReturn("123")

            // Act
            val result = GraphQLArgumentUtils.getRequiredId(env, "id")

            // Assert
            assertThat(result).isEqualTo("123")
        }

        @Test
        fun `should return ID with default argument name`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<String>("id")).thenReturn("123")

            // Act
            val result = GraphQLArgumentUtils.getRequiredId(env)

            // Assert
            assertThat(result).isEqualTo("123")
        }

        @Test
        fun `should throw exception when ID is missing`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<String>("id")).thenReturn(null)

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                GraphQLArgumentUtils.getRequiredId(env, "id")
            }
        }

        @Test
        fun `should throw exception when ID is empty`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<String>("id")).thenReturn("")

            // Act & Assert
            val exception =
                assertThrows<IllegalArgumentException> {
                    GraphQLArgumentUtils.getRequiredId(env, "id")
                }

            assertThat(exception.message).isEqualTo("id is required and cannot be empty")
        }

        @Test
        fun `should throw exception when ID is blank`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<String>("id")).thenReturn("   ")

            // Act & Assert
            val exception =
                assertThrows<IllegalArgumentException> {
                    GraphQLArgumentUtils.getRequiredId(env, "id")
                }

            assertThat(exception.message).isEqualTo("id is required and cannot be empty")
        }
    }

    @Nested
    @DisplayName("getRequiredInput()")
    inner class GetRequiredInputTests {
        @Test
        fun `should return input when present`() {
            // Arrange
            env = mock()
            val input = mapOf("key" to "value")
            whenever(env.getArgument<Map<String, Any?>>("input")).thenReturn(input)

            // Act
            val result = GraphQLArgumentUtils.getRequiredInput(env, "input")

            // Assert
            assertThat(result).isEqualTo(input)
        }

        @Test
        fun `should return input with default argument name`() {
            // Arrange
            env = mock()
            val input = mapOf("key" to "value")
            whenever(env.getArgument<Map<String, Any?>>("input")).thenReturn(input)

            // Act
            val result = GraphQLArgumentUtils.getRequiredInput(env)

            // Assert
            assertThat(result).isEqualTo(input)
        }

        @Test
        fun `should throw exception when input is missing`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<Map<String, Any?>>("input")).thenReturn(null)

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                GraphQLArgumentUtils.getRequiredInput(env, "input")
            }
        }
    }

    @Nested
    @DisplayName("validateRequiredArguments()")
    inner class ValidateRequiredArgumentsTests {
        @Test
        fun `should not throw when all required arguments are present`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<Any?>("arg1")).thenReturn("value1")
            whenever(env.getArgument<Any?>("arg2")).thenReturn("value2")

            // Act & Assert - should not throw
            GraphQLArgumentUtils.validateRequiredArguments(env, "arg1", "arg2")
        }

        @Test
        fun `should throw exception when some arguments are missing`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<Any?>("arg1")).thenReturn("value1")
            whenever(env.getArgument<Any?>("arg2")).thenReturn(null)

            // Act & Assert
            val exception =
                assertThrows<IllegalArgumentException> {
                    GraphQLArgumentUtils.validateRequiredArguments(env, "arg1", "arg2")
                }

            assertThat(exception.message).isEqualTo("Required arguments are missing or empty: arg2")
        }

        @Test
        fun `should throw exception when multiple arguments are missing`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<Any?>("arg1")).thenReturn(null)
            whenever(env.getArgument<Any?>("arg2")).thenReturn(null)

            // Act & Assert
            val exception =
                assertThrows<IllegalArgumentException> {
                    GraphQLArgumentUtils.validateRequiredArguments(env, "arg1", "arg2")
                }

            assertThat(exception.message).contains("arg1")
            assertThat(exception.message).contains("arg2")
        }

        @Test
        fun `should throw exception when argument is empty string`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<Any?>("arg1")).thenReturn("")

            // Act & Assert
            val exception =
                assertThrows<IllegalArgumentException> {
                    GraphQLArgumentUtils.validateRequiredArguments(env, "arg1")
                }

            assertThat(exception.message).isEqualTo("Required arguments are missing or empty: arg1")
        }

        @Test
        fun `should throw exception when argument is blank string`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<Any?>("arg1")).thenReturn("   ")

            // Act & Assert
            val exception =
                assertThrows<IllegalArgumentException> {
                    GraphQLArgumentUtils.validateRequiredArguments(env, "arg1")
                }

            assertThat(exception.message).isEqualTo("Required arguments are missing or empty: arg1")
        }

        @Test
        fun `should not throw when no arguments are required`() {
            // Arrange
            env = mock()

            // Act & Assert - should not throw
            GraphQLArgumentUtils.validateRequiredArguments(env)
        }
    }

    @Nested
    @DisplayName("createValidatedDataFetcher()")
    inner class CreateValidatedDataFetcherTests {
        @Test
        fun `should execute block when required arguments are present`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<Any?>("arg1")).thenReturn("value1")

            val dataFetcher =
                GraphQLArgumentUtils.createValidatedDataFetcher(listOf("arg1")) {
                    "result"
                }

            // Act
            val result = dataFetcher.get(env)

            // Assert
            assertThat(result).isEqualTo("result")
        }

        @Test
        fun `should throw exception when required arguments are missing`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<Any?>("arg1")).thenReturn(null)

            val dataFetcher =
                GraphQLArgumentUtils.createValidatedDataFetcher(listOf("arg1")) {
                    "result"
                }

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                dataFetcher.get(env)
            }
        }

        @Test
        fun `should execute block when no required arguments`() {
            // Arrange
            env = mock()

            val dataFetcher =
                GraphQLArgumentUtils.createValidatedDataFetcher<String>(emptyList()) {
                    "result"
                }

            // Act
            val result = dataFetcher.get(env)

            // Assert
            assertThat(result).isEqualTo("result")
        }
    }

    @Nested
    @DisplayName("createCrudDataFetcher()")
    inner class CreateCrudDataFetcherTests {
        @Test
        fun `should execute block with ID when ID is present`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<String>("id")).thenReturn("123")

            val dataFetcher =
                GraphQLArgumentUtils.createCrudDataFetcher("getById") { id ->
                    "result-$id"
                }

            // Act
            val result = dataFetcher.get(env)

            // Assert
            assertThat(result).isEqualTo("result-123")
        }

        @Test
        fun `should throw exception when ID is missing`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<String>("id")).thenReturn(null)

            val dataFetcher =
                GraphQLArgumentUtils.createCrudDataFetcher("getById") { id ->
                    "result-$id"
                }

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                dataFetcher.get(env)
            }
        }
    }

    @Nested
    @DisplayName("createMutationDataFetcher()")
    inner class CreateMutationDataFetcherTests {
        @Test
        fun `should execute block with input when input is present`() {
            // Arrange
            env = mock()
            val input = mapOf("key" to "value")
            whenever(env.getArgument<Map<String, Any?>>("input")).thenReturn(input)

            val dataFetcher =
                GraphQLArgumentUtils.createMutationDataFetcher("create") { inp ->
                    "result-${inp["key"]}"
                }

            // Act
            val result = dataFetcher.get(env)

            // Assert
            assertThat(result).isEqualTo("result-value")
        }

        @Test
        fun `should throw exception when input is missing`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<Map<String, Any?>>("input")).thenReturn(null)

            val dataFetcher =
                GraphQLArgumentUtils.createMutationDataFetcher("create") { input ->
                    "result"
                }

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                dataFetcher.get(env)
            }
        }
    }

    @Nested
    @DisplayName("createUpdateMutationDataFetcher()")
    inner class CreateUpdateMutationDataFetcherTests {
        @Test
        fun `should execute block with ID and input when both are present`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<String>("id")).thenReturn("123")
            val input = mapOf("key" to "value")
            whenever(env.getArgument<Map<String, Any?>>("input")).thenReturn(input)

            val dataFetcher =
                GraphQLArgumentUtils.createUpdateMutationDataFetcher("update") { id, inp ->
                    "result-$id-${inp["key"]}"
                }

            // Act
            val result = dataFetcher.get(env)

            // Assert
            assertThat(result).isEqualTo("result-123-value")
        }

        @Test
        fun `should throw exception when ID is missing`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<String>("id")).thenReturn(null)
            val input = mapOf("key" to "value")
            whenever(env.getArgument<Map<String, Any?>>("input")).thenReturn(input)

            val dataFetcher =
                GraphQLArgumentUtils.createUpdateMutationDataFetcher("update") { id, input ->
                    "result"
                }

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                dataFetcher.get(env)
            }
        }

        @Test
        fun `should throw exception when input is missing`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<String>("id")).thenReturn("123")
            whenever(env.getArgument<Map<String, Any?>>("input")).thenReturn(null)

            val dataFetcher =
                GraphQLArgumentUtils.createUpdateMutationDataFetcher("update") { id, input ->
                    "result"
                }

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                dataFetcher.get(env)
            }
        }
    }
}
