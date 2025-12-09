package io.github.salomax.neotool.common.graphql

import graphql.AssertException
import graphql.ExecutionInput
import graphql.ExecutionResultImpl
import graphql.GraphQL
import graphql.execution.UnknownOperationException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@DisplayName("GraphQLControllerBase Tests")
class GraphQLControllerBaseTest {
    private lateinit var mockGraphQL: GraphQL
    private lateinit var controller: GraphQLControllerBase

    @BeforeEach
    fun setUp() {
        mockGraphQL = mock()
        controller = GraphQLControllerBase(mockGraphQL)
    }

    @Nested
    @DisplayName("Empty Query Handling")
    inner class EmptyQueryTests {
        @Test
        fun `should return error spec when query is empty`() {
            // Arrange
            val req = GraphQLRequest(query = "", variables = null, operationName = null)

            // Act
            val result = controller.post(req, null)

            // Assert
            assertThat(result["errors"]).isNotNull
            assertThat(result["data"]).isNull()
            val errors = result["errors"] as List<*>
            assertThat(errors).isNotEmpty
            val error = errors[0] as Map<*, *>
            assertThat(error["message"]).isEqualTo("Query must not be empty")
        }

        @Test
        fun `should return error spec when query is blank`() {
            // Arrange
            val req = GraphQLRequest(query = "   ", variables = null, operationName = null)

            // Act
            val result = controller.post(req, null)

            // Assert
            assertThat(result["errors"]).isNotNull
            assertThat(result["data"]).isNull()
        }
    }

    @Nested
    @DisplayName("Operation Name Validation")
    inner class OperationNameValidationTests {
        @Test
        fun `should return error spec when operationName does not exist`() {
            // Arrange
            val query =
                """
                query GetA { a }
                query GetB { b }
                """.trimIndent()
            val req = GraphQLRequest(query = query, variables = null, operationName = "Nope")

            // Act
            val result = controller.post(req, null)

            // Assert
            assertThat(result["errors"]).isNotNull
            verify(mockGraphQL, Mockito.never()).execute(any<ExecutionInput>())
        }

        @Test
        fun `should handle blank operationName by not setting it`() {
            // Arrange
            val query =
                """
                query GetA { a }
                query GetB { b }
                """.trimIndent()
            val req = GraphQLRequest(query = query, variables = null, operationName = "   ")

            whenever(mockGraphQL.execute(any<ExecutionInput>())).thenAnswer {
                ExecutionResultImpl.newExecutionResult()
                    .data(mapOf("a" to "value"))
                    .build()
            }

            // Act
            val result = controller.post(req, null)

            // Assert
            assertThat(result["errors"]).isNull()

            val captor = ArgumentCaptor.forClass(ExecutionInput::class.java)
            verify(mockGraphQL).execute(captor.capture())
            val input = captor.value
            assertThat(input.operationName).isNull()
        }

        @Test
        fun `should handle valid operationName`() {
            // Arrange
            val query =
                """
                query GetA { a }
                query GetB { b }
                """.trimIndent()
            val req = GraphQLRequest(query = query, variables = null, operationName = "GetA")

            whenever(mockGraphQL.execute(any<ExecutionInput>())).thenAnswer {
                ExecutionResultImpl.newExecutionResult()
                    .data(mapOf("a" to "value"))
                    .build()
            }

            // Act
            val result = controller.post(req, null)

            // Assert
            assertThat(result["errors"]).isNull()

            val captor = ArgumentCaptor.forClass(ExecutionInput::class.java)
            verify(mockGraphQL).execute(captor.capture())
            val input = captor.value
            assertThat(input.operationName).isEqualTo("GetA")
        }
    }

    @Nested
    @DisplayName("Authorization Header Handling")
    inner class AuthorizationHeaderTests {
        @Test
        fun `should include token in GraphQL context when Authorization header is provided`() {
            // Arrange
            val query = "query { test }"
            val req = GraphQLRequest(query = query, variables = null, operationName = null)
            val authHeader = "Bearer test-token-123"

            whenever(mockGraphQL.execute(any<ExecutionInput>())).thenAnswer {
                ExecutionResultImpl.newExecutionResult()
                    .data(mapOf("test" to "value"))
                    .build()
            }

            // Act
            val result = controller.post(req, authHeader)

            // Assert
            assertThat(result["errors"]).isNull()
            assertThat(result["data"]).isNotNull

            val captor = ArgumentCaptor.forClass(ExecutionInput::class.java)
            verify(mockGraphQL).execute(captor.capture())
            val input = captor.value
            val context = input.graphQLContext
            assertThat(context.get<String>("token")).isEqualTo("test-token-123")
        }

        @Test
        fun `should not include token in GraphQL context when Authorization header is null`() {
            // Arrange
            val query = "query { test }"
            val req = GraphQLRequest(query = query, variables = null, operationName = null)

            whenever(mockGraphQL.execute(any<ExecutionInput>())).thenAnswer {
                ExecutionResultImpl.newExecutionResult()
                    .data(mapOf("test" to "value"))
                    .build()
            }

            // Act
            val result = controller.post(req, null)

            // Assert
            assertThat(result["errors"]).isNull()
            assertThat(result["data"]).isNotNull

            val captor = ArgumentCaptor.forClass(ExecutionInput::class.java)
            verify(mockGraphQL).execute(captor.capture())
            val input = captor.value
            val context = input.graphQLContext
            assertThat(context.get<Any>("token")).isNull()
        }

        @Test
        fun `should not include token when Authorization header is blank after Bearer prefix`() {
            // Arrange
            val query = "query { test }"
            val req = GraphQLRequest(query = query, variables = null, operationName = null)
            val authHeader = "Bearer   " // Blank token after Bearer

            whenever(mockGraphQL.execute(any<ExecutionInput>())).thenAnswer {
                ExecutionResultImpl.newExecutionResult()
                    .data(mapOf("test" to "value"))
                    .build()
            }

            // Act
            val result = controller.post(req, authHeader)

            // Assert
            assertThat(result["errors"]).isNull()
            assertThat(result["data"]).isNotNull

            val captor = ArgumentCaptor.forClass(ExecutionInput::class.java)
            verify(mockGraphQL).execute(captor.capture())
            val input = captor.value
            val context = input.graphQLContext
            assertThat(context.get<Any>("token")).isNull()
        }
    }

    @Nested
    @DisplayName("Variables Handling")
    inner class VariablesHandlingTests {
        @Test
        fun `should use empty map when variables is null`() {
            // Arrange
            val query = "query { test }"
            val req = GraphQLRequest(query = query, variables = null, operationName = null)

            whenever(mockGraphQL.execute(any<ExecutionInput>())).thenAnswer {
                ExecutionResultImpl.newExecutionResult()
                    .data(mapOf("test" to "value"))
                    .build()
            }

            // Act
            val result = controller.post(req, null)

            // Assert
            assertThat(result["errors"]).isNull()

            val captor = ArgumentCaptor.forClass(ExecutionInput::class.java)
            verify(mockGraphQL).execute(captor.capture())
            val input = captor.value
            assertThat(input.variables).isEmpty()
        }

        @Test
        fun `should forward variables when provided`() {
            // Arrange
            val query = "query GetA(${'$'}id: ID!) { product(id: ${'$'}id) { id } }"
            val req =
                GraphQLRequest(
                    query = query,
                    variables = mapOf("id" to "123", "name" to "Test"),
                    operationName = null,
                )

            whenever(mockGraphQL.execute(any<ExecutionInput>())).thenAnswer {
                ExecutionResultImpl.newExecutionResult()
                    .data(mapOf("product" to mapOf("id" to "123")))
                    .build()
            }

            // Act
            val result = controller.post(req, null)

            // Assert
            assertThat(result["errors"]).isNull()

            val captor = ArgumentCaptor.forClass(ExecutionInput::class.java)
            verify(mockGraphQL).execute(captor.capture())
            val input = captor.value
            assertThat(input.variables["id"]).isEqualTo("123")
            assertThat(input.variables["name"]).isEqualTo("Test")
        }
    }

    @Nested
    @DisplayName("Exception Handling")
    inner class ExceptionHandlingTests {
        @Test
        fun `should handle UnknownOperationException and return error spec`() {
            // Arrange
            val req = GraphQLRequest(query = "query { test }", variables = null, operationName = null)
            whenever(
                mockGraphQL.execute(any<ExecutionInput>()),
            ).thenThrow(UnknownOperationException("Unknown operation"))

            // Act
            val result = controller.post(req, null)

            // Assert
            assertThat(result["errors"]).isNotNull
            assertThat(result["data"]).isNull()
            val errors = result["errors"] as List<*>
            assertThat(errors).isNotEmpty
            val error = errors[0] as Map<*, *>
            assertThat(error["message"]).isEqualTo("Unknown operation")
        }

        @Test
        fun `should handle UnknownOperationException with null message`() {
            // Arrange
            val req = GraphQLRequest(query = "query { test }", variables = null, operationName = null)
            whenever(mockGraphQL.execute(any<ExecutionInput>())).thenThrow(UnknownOperationException(null))

            // Act
            val result = controller.post(req, null)

            // Assert
            assertThat(result["errors"]).isNotNull
            assertThat(result["data"]).isNull()
            val errors = result["errors"] as List<*>
            assertThat(errors).isNotEmpty
            val error = errors[0] as Map<*, *>
            assertThat(error["message"]).isEqualTo("Unknown operation")
        }

        @Test
        fun `should handle AssertException and return error spec`() {
            // Arrange
            val req = GraphQLRequest(query = "query { test }", variables = null, operationName = null)
            whenever(
                mockGraphQL.execute(any<ExecutionInput>()),
            ).thenThrow(AssertException("Type not defined: TestType"))

            // Act
            val result = controller.post(req, null)

            // Assert
            assertThat(result["errors"]).isNotNull
            assertThat(result["data"]).isNull()
            val errors = result["errors"] as List<*>
            assertThat(errors).isNotEmpty
            val error = errors[0] as Map<*, *>
            assertThat(error["message"] as String).contains("The type is not defined")
        }

        @Test
        fun `should handle AssertException with null message`() {
            // Arrange
            val req = GraphQLRequest(query = "query { test }", variables = null, operationName = null)
            whenever(mockGraphQL.execute(any<ExecutionInput>())).thenThrow(AssertException(null))

            // Act
            val result = controller.post(req, null)

            // Assert
            assertThat(result["errors"]).isNotNull
            assertThat(result["data"]).isNull()
            val errors = result["errors"] as List<*>
            assertThat(errors).isNotEmpty
            val error = errors[0] as Map<*, *>
            assertThat(error["message"] as String).contains("The type is not defined")
        }

        @Test
        fun `should handle generic Exception and return error spec`() {
            // Arrange
            val req = GraphQLRequest(query = "query { test }", variables = null, operationName = null)
            whenever(mockGraphQL.execute(any<ExecutionInput>())).thenThrow(RuntimeException("Unexpected error"))

            // Act
            val result = controller.post(req, null)

            // Assert
            assertThat(result["errors"]).isNotNull
            assertThat(result["data"]).isNull()
            val errors = result["errors"] as List<*>
            assertThat(errors).isNotEmpty
            val error = errors[0] as Map<*, *>
            assertThat(error["message"] as String).contains("GraphQL execution failed")
        }

        @Test
        fun `should handle generic Exception with null message`() {
            // Arrange
            val req = GraphQLRequest(query = "query { test }", variables = null, operationName = null)
            whenever(mockGraphQL.execute(any<ExecutionInput>())).thenThrow(RuntimeException(null as String?))

            // Act
            val result = controller.post(req, null)

            // Assert
            assertThat(result["errors"]).isNotNull
            assertThat(result["data"]).isNull()
            val errors = result["errors"] as List<*>
            assertThat(errors).isNotEmpty
            val error = errors[0] as Map<*, *>
            assertThat(error["message"] as String).contains("GraphQL execution failed")
        }
    }

    @Nested
    @DisplayName("Successful Execution")
    inner class SuccessfulExecutionTests {
        @Test
        fun `should return GraphQL result when execution succeeds`() {
            // Arrange
            val query = "query { test }"
            val req = GraphQLRequest(query = query, variables = null, operationName = null)
            val expectedData = mapOf("test" to "value")

            whenever(mockGraphQL.execute(any<ExecutionInput>())).thenAnswer {
                ExecutionResultImpl.newExecutionResult()
                    .data(expectedData)
                    .build()
            }

            // Act
            val result = controller.post(req, null)

            // Assert
            assertThat(result["errors"]).isNull()
            assertThat(result["data"]).isEqualTo(expectedData)
        }

        @Test
        fun `should forward variables and operationName to ExecutionInput`() {
            // Arrange
            val query =
                """
                query GetA(${'$'}id: ID!) { product(id: ${'$'}id) { id name } }
                """.trimIndent()
            val req =
                GraphQLRequest(
                    query = query,
                    variables = mapOf("id" to "123"),
                    operationName = "GetA",
                )

            whenever(mockGraphQL.execute(any<ExecutionInput>())).thenAnswer {
                ExecutionResultImpl.newExecutionResult()
                    .data(mapOf("product" to mapOf("id" to "123", "name" to "X")))
                    .build()
            }

            // Act
            val result = controller.post(req, null)

            // Assert
            assertThat(result["errors"]).isNull()
            assertThat(result["data"]).isNotNull

            val captor = ArgumentCaptor.forClass(ExecutionInput::class.java)
            verify(mockGraphQL).execute(captor.capture())
            val input = captor.value
            assertThat(input.operationName).isEqualTo("GetA")
            assertThat(input.variables["id"]).isEqualTo("123")
            assertThat(input.query).contains("query GetA")
        }
    }
}
