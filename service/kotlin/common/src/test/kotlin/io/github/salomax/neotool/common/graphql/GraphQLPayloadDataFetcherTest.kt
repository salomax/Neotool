package io.github.salomax.neotool.common.graphql

import graphql.schema.DataFetchingEnvironment
import io.github.salomax.neotool.common.graphql.payload.ErrorPayload
import io.github.salomax.neotool.common.graphql.payload.GraphQLError
import io.github.salomax.neotool.common.graphql.payload.GraphQLPayload
import io.github.salomax.neotool.common.graphql.payload.SuccessPayload
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@DisplayName("GraphQLPayloadDataFetcher Tests")
class GraphQLPayloadDataFetcherTest {
    private lateinit var env: DataFetchingEnvironment

    @Nested
    @DisplayName("createPayloadDataFetcher()")
    inner class CreatePayloadDataFetcherTests {
        @Test
        fun `should return data when payload is success`() {
            // Arrange
            env = mock()
            val expectedData = "test-data"
            val payload: GraphQLPayload<String> = SuccessPayload(expectedData)

            // Act
            val fetcher = GraphQLPayloadDataFetcher.createPayloadDataFetcher("testOperation") { payload }
            val result = fetcher.get(env)

            // Assert
            assertThat(result).isEqualTo(expectedData)
        }

        @Test
        fun `should throw exception when payload has errors`() {
            // Arrange
            env = mock()
            val errors = listOf(GraphQLError(listOf("field"), "Error message", "ERROR_CODE"))
            val payload: GraphQLPayload<String> = ErrorPayload(errors = errors)

            // Act & Assert
            val fetcher =
                GraphQLPayloadDataFetcher.createPayloadDataFetcher("testOperation") { payload }
            val exception =
                assertThrows<GraphQLPayloadException> {
                    fetcher.get(env)
                }

            assertThat(exception.errors).isEqualTo(errors)
            assertThat(exception.message).isEqualTo("Error message")
        }
    }

    @Nested
    @DisplayName("createFullPayloadDataFetcher()")
    inner class CreateFullPayloadDataFetcherTests {
        @Test
        fun `should return full payload`() {
            // Arrange
            env = mock()
            val expectedData = "test-data"
            val payload: GraphQLPayload<String> = SuccessPayload(expectedData)

            // Act
            val fetcher = GraphQLPayloadDataFetcher.createFullPayloadDataFetcher("testOperation") { payload }
            val result = fetcher.get(env)

            // Assert
            assertThat(result).isEqualTo(payload)
            assertThat(result.data).isEqualTo(expectedData)
        }

        @Test
        fun `should return error payload when block returns error`() {
            // Arrange
            env = mock()
            val errors = listOf(GraphQLError(listOf("field"), "Error message", "ERROR_CODE"))
            val payload: GraphQLPayload<String> = ErrorPayload(errors = errors)

            // Act
            val fetcher = GraphQLPayloadDataFetcher.createFullPayloadDataFetcher("testOperation") { payload }
            val result = fetcher.get(env)

            // Assert
            assertThat(result).isEqualTo(payload)
            assertThat(result.errors).isEqualTo(errors)
            assertThat(result.success).isFalse
        }
    }

    @Nested
    @DisplayName("createMutationDataFetcher()")
    inner class CreateMutationDataFetcherTests {
        @Test
        fun `should return data when mutation succeeds`() {
            // Arrange
            env = mock()
            val input = mapOf("name" to "test")
            val expectedData = "created-data"
            whenever(env.getArgument<Map<String, Any?>>("input")).thenReturn(input)

            // Act
            val fetcher =
                GraphQLPayloadDataFetcher.createMutationDataFetcher("createTest") { inputMap ->
                    SuccessPayload(expectedData)
                }
            val result = fetcher.get(env)

            // Assert
            assertThat(result).isEqualTo(expectedData)
        }

        @Test
        fun `should throw exception when input is missing`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<Map<String, Any?>>("input")).thenReturn(null)

            // Act & Assert
            val fetcher =
                GraphQLPayloadDataFetcher.createMutationDataFetcher("createTest") { inputMap ->
                    SuccessPayload("data")
                }
            val exception =
                assertThrows<IllegalArgumentException> {
                    fetcher.get(env)
                }

            assertThat(exception.message).isEqualTo("Input is required")
        }

        @Test
        fun `should throw GraphQLPayloadException when mutation fails`() {
            // Arrange
            env = mock()
            val input = mapOf("name" to "test")
            val errors = listOf(GraphQLError(listOf("field"), "Validation error", "VALIDATION_ERROR"))
            whenever(env.getArgument<Map<String, Any?>>("input")).thenReturn(input)

            // Act & Assert
            val fetcher =
                GraphQLPayloadDataFetcher.createMutationDataFetcher<String>("createTest") { inputMap ->
                    ErrorPayload<String>(errors = errors)
                }
            val exception =
                assertThrows<GraphQLPayloadException> {
                    fetcher.get(env)
                }

            assertThat(exception.errors).isEqualTo(errors)
        }
    }

    @Nested
    @DisplayName("createUpdateMutationDataFetcher()")
    inner class CreateUpdateMutationDataFetcherTests {
        @Test
        fun `should return data when update succeeds`() {
            // Arrange
            env = mock()
            val id = "test-id"
            val input = mapOf("name" to "updated")
            val expectedData = "updated-data"
            whenever(env.getArgument<String>("id")).thenReturn(id)
            whenever(env.getArgument<Map<String, Any?>>("input")).thenReturn(input)

            // Act
            val fetcher =
                GraphQLPayloadDataFetcher.createUpdateMutationDataFetcher("updateTest") {
                        updateId,
                        updateInput,
                    ->
                    assertThat(updateId).isEqualTo(id)
                    assertThat(updateInput).isEqualTo(input)
                    SuccessPayload(expectedData)
                }
            val result = fetcher.get(env)

            // Assert
            assertThat(result).isEqualTo(expectedData)
        }

        @Test
        fun `should throw exception when id is missing`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<String>("id")).thenReturn(null)
            whenever(env.getArgument<Map<String, Any?>>("input")).thenReturn(mapOf())

            // Act & Assert
            val fetcher =
                GraphQLPayloadDataFetcher.createUpdateMutationDataFetcher("updateTest") {
                        updateId,
                        updateInput,
                    ->
                    SuccessPayload("data")
                }
            val exception =
                assertThrows<IllegalArgumentException> {
                    fetcher.get(env)
                }

            assertThat(exception.message).isEqualTo("ID is required")
        }

        @Test
        fun `should throw exception when input is missing`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<String>("id")).thenReturn("test-id")
            whenever(env.getArgument<Map<String, Any?>>("input")).thenReturn(null)

            // Act & Assert
            val fetcher =
                GraphQLPayloadDataFetcher.createUpdateMutationDataFetcher("updateTest") {
                        updateId,
                        updateInput,
                    ->
                    SuccessPayload("data")
                }
            val exception =
                assertThrows<IllegalArgumentException> {
                    fetcher.get(env)
                }

            assertThat(exception.message).isEqualTo("Input is required")
        }

        @Test
        fun `should throw GraphQLPayloadException when update fails`() {
            // Arrange
            env = mock()
            val id = "test-id"
            val input = mapOf("name" to "updated")
            val errors = listOf(GraphQLError(listOf("field"), "Update error", "UPDATE_ERROR"))
            whenever(env.getArgument<String>("id")).thenReturn(id)
            whenever(env.getArgument<Map<String, Any?>>("input")).thenReturn(input)

            // Act & Assert
            val fetcher =
                GraphQLPayloadDataFetcher.createUpdateMutationDataFetcher<String>("updateTest") {
                        updateId,
                        updateInput,
                    ->
                    ErrorPayload<String>(errors = errors)
                }
            val exception =
                assertThrows<GraphQLPayloadException> {
                    fetcher.get(env)
                }

            assertThat(exception.errors).isEqualTo(errors)
        }
    }

    @Nested
    @DisplayName("createCrudDataFetcher()")
    inner class CreateCrudDataFetcherTests {
        @Test
        fun `should return data when CRUD operation succeeds`() {
            // Arrange
            env = mock()
            val id = "test-id"
            val expectedData = "fetched-data"
            whenever(env.getArgument<String>("id")).thenReturn(id)

            // Act
            val fetcher =
                GraphQLPayloadDataFetcher.createCrudDataFetcher("getTest") { fetchedId ->
                    assertThat(fetchedId).isEqualTo(id)
                    SuccessPayload(expectedData)
                }
            val result = fetcher.get(env)

            // Assert
            assertThat(result).isEqualTo(expectedData)
        }

        @Test
        fun `should throw exception when id is missing`() {
            // Arrange
            env = mock()
            whenever(env.getArgument<String>("id")).thenReturn(null)

            // Act & Assert
            val fetcher =
                GraphQLPayloadDataFetcher.createCrudDataFetcher("getTest") { fetchedId ->
                    SuccessPayload("data")
                }
            val exception =
                assertThrows<IllegalArgumentException> {
                    fetcher.get(env)
                }

            assertThat(exception.message).isEqualTo("ID is required")
        }

        @Test
        fun `should throw GraphQLPayloadException when CRUD operation fails`() {
            // Arrange
            env = mock()
            val id = "test-id"
            val errors = listOf(GraphQLError(listOf("id"), "Not found", "NOT_FOUND"))
            whenever(env.getArgument<String>("id")).thenReturn(id)

            // Act & Assert
            val fetcher =
                GraphQLPayloadDataFetcher.createCrudDataFetcher<String>("getTest") { fetchedId ->
                    ErrorPayload<String>(errors = errors)
                }
            val exception =
                assertThrows<GraphQLPayloadException> {
                    fetcher.get(env)
                }

            assertThat(exception.errors).isEqualTo(errors)
        }
    }

    @Nested
    @DisplayName("GraphQLPayloadException")
    inner class GraphQLPayloadExceptionTests {
        @Test
        fun `should use first error message when errors exist`() {
            // Arrange
            val errors =
                listOf(
                    GraphQLError(listOf("field1"), "First error", "ERROR1"),
                    GraphQLError(listOf("field2"), "Second error", "ERROR2"),
                )

            // Act
            val exception = GraphQLPayloadException(errors)

            // Assert
            assertThat(exception.errors).isEqualTo(errors)
            assertThat(exception.message).isEqualTo("First error")
        }

        @Test
        fun `should use default message when errors are empty`() {
            // Arrange
            val errors = emptyList<GraphQLError>()

            // Act
            val exception = GraphQLPayloadException(errors)

            // Assert
            assertThat(exception.errors).isEmpty()
            assertThat(exception.message).isEqualTo("An error occurred")
        }
    }
}
