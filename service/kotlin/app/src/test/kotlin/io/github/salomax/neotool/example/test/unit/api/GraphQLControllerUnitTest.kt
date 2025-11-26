package io.github.salomax.neotool.example.test.unit.api

import graphql.AssertException
import graphql.ExecutionInput
import graphql.ExecutionResultImpl
import graphql.GraphQL
import graphql.execution.UnknownOperationException
import io.github.salomax.neotool.common.graphql.GraphQLControllerBase
import io.github.salomax.neotool.common.graphql.GraphQLRequest
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@MicronautTest(startApplication = false)
class GraphQLControllerUnitTest {
    private val mockGraphQL: GraphQL = mock()

    private val controller = GraphQLControllerBase(mockGraphQL)

    @Test
    fun `empty query returns error spec`() {
        val req = GraphQLRequest(query = "", variables = null, operationName = null)

        val result = controller.post(req, null)

        assertThat(result["errors"]).isNotNull
        assertThat(result["data"]).isNull()
    }

    @Test
    fun `unknown operationName returns error spec without calling GraphQL`() {
        val query =
            """
            query GetA { a }
            query GetB { b }
            """.trimIndent()
        val req = GraphQLRequest(query = query, variables = null, operationName = "Nope")

        val result = controller.post(req, null)

        assertThat(result["errors"]).isNotNull
        verify(mockGraphQL, Mockito.never()).execute(any<ExecutionInput>())
    }

    @Test
    fun `variables and operationName are forwarded to ExecutionInput`() {
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
            // Return a minimal successful result
            ExecutionResultImpl.newExecutionResult()
                .data(mapOf("product" to mapOf("id" to "123", "name" to "X")))
                .build()
        }

        val result = controller.post(req, null)
        assertThat(result["errors"]).isNull()
        assertThat(result["data"]).isNotNull

        val captor = ArgumentCaptor.forClass(ExecutionInput::class.java)
        verify(mockGraphQL).execute(captor.capture())
        val input = captor.value
        assertThat(input.operationName).isEqualTo("GetA")
        assertThat(input.variables["id"]).isEqualTo("123")
        assertThat(input.query).contains("query GetA")
    }

    @Test
    fun `blank query returns error spec`() {
        val req = GraphQLRequest(query = "   ", variables = null, operationName = null)

        val result = controller.post(req, null)

        assertThat(result["errors"]).isNotNull
        assertThat(result["data"]).isNull()
    }

    @Nested
    @DisplayName("Authorization Header Handling")
    inner class AuthorizationHeaderTests {
        @Test
        fun `should include token in GraphQL context when Authorization header is provided`() {
            val query = "query { test }"
            val req = GraphQLRequest(query = query, variables = null, operationName = null)
            val authHeader = "Bearer test-token-123"

            whenever(mockGraphQL.execute(any<ExecutionInput>())).thenAnswer {
                ExecutionResultImpl.newExecutionResult()
                    .data(mapOf("test" to "value"))
                    .build()
            }

            val result = controller.post(req, authHeader)
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
            val query = "query { test }"
            val req = GraphQLRequest(query = query, variables = null, operationName = null)

            whenever(mockGraphQL.execute(any<ExecutionInput>())).thenAnswer {
                ExecutionResultImpl.newExecutionResult()
                    .data(mapOf("test" to "value"))
                    .build()
            }

            val result = controller.post(req, null)
            assertThat(result["errors"]).isNull()
            assertThat(result["data"]).isNotNull

            val captor = ArgumentCaptor.forClass(ExecutionInput::class.java)
            verify(mockGraphQL).execute(captor.capture())
            val input = captor.value
            val context = input.graphQLContext
            // Token should not be in context when header is null
            assertThat(context.get<Any>("token")).isNull()
        }

        @Test
        fun `should not include token when Authorization header is blank after Bearer prefix`() {
            val query = "query { test }"
            val req = GraphQLRequest(query = query, variables = null, operationName = null)
            val authHeader = "Bearer   " // Blank token after Bearer

            whenever(mockGraphQL.execute(any<ExecutionInput>())).thenAnswer {
                ExecutionResultImpl.newExecutionResult()
                    .data(mapOf("test" to "value"))
                    .build()
            }

            val result = controller.post(req, authHeader)
            assertThat(result["errors"]).isNull()
            assertThat(result["data"]).isNotNull

            val captor = ArgumentCaptor.forClass(ExecutionInput::class.java)
            verify(mockGraphQL).execute(captor.capture())
            val input = captor.value
            val context = input.graphQLContext
            // Token should not be in context when blank
            assertThat(context.get<Any>("token")).isNull()
        }
    }

    @Nested
    @DisplayName("Operation Name Handling")
    inner class OperationNameTests {
        @Test
        fun `should handle blank operationName by not setting it`() {
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

            val result = controller.post(req, null)
            assertThat(result["errors"]).isNull()

            val captor = ArgumentCaptor.forClass(ExecutionInput::class.java)
            verify(mockGraphQL).execute(captor.capture())
            val input = captor.value
            // Blank operationName should not be set
            assertThat(input.operationName).isNull()
        }

        @Test
        fun `should handle valid operationName`() {
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

            val result = controller.post(req, null)
            assertThat(result["errors"]).isNull()

            val captor = ArgumentCaptor.forClass(ExecutionInput::class.java)
            verify(mockGraphQL).execute(captor.capture())
            val input = captor.value
            assertThat(input.operationName).isEqualTo("GetA")
        }
    }

    @Nested
    @DisplayName("Exception Handling")
    inner class ExceptionHandlingTests {
        @Test
        fun `should handle UnknownOperationException and return error spec`() {
            val req = GraphQLRequest(query = "query { test }", variables = null, operationName = null)
            whenever(
                mockGraphQL.execute(any<ExecutionInput>()),
            ).thenThrow(UnknownOperationException("Unknown operation"))

            val result = controller.post(req, null)

            assertThat(result["errors"]).isNotNull
            assertThat(result["data"]).isNull()
            val errors = result["errors"] as List<*>
            assertThat(errors).isNotEmpty
            val error = errors[0] as Map<*, *>
            assertThat(error["message"]).isNotNull
        }

        @Test
        fun `should handle UnknownOperationException with null message`() {
            val req = GraphQLRequest(query = "query { test }", variables = null, operationName = null)
            whenever(mockGraphQL.execute(any<ExecutionInput>())).thenThrow(UnknownOperationException(null))

            val result = controller.post(req, null)

            assertThat(result["errors"]).isNotNull
            assertThat(result["data"]).isNull()
            val errors = result["errors"] as List<*>
            assertThat(errors).isNotEmpty
            val error = errors[0] as Map<*, *>
            assertThat(error["message"]).isEqualTo("Unknown operation")
        }

        @Test
        fun `should handle AssertException and return error spec`() {
            val req = GraphQLRequest(query = "query { test }", variables = null, operationName = null)
            whenever(
                mockGraphQL.execute(any<ExecutionInput>()),
            ).thenThrow(AssertException("Type not defined: TestType"))

            val result = controller.post(req, null)

            assertThat(result["errors"]).isNotNull
            assertThat(result["data"]).isNull()
            val errors = result["errors"] as List<*>
            assertThat(errors).isNotEmpty
            val error = errors[0] as Map<*, *>
            assertThat(error["message"] as String).contains("The type is not defined")
        }

        @Test
        fun `should handle AssertException with null message`() {
            val req = GraphQLRequest(query = "query { test }", variables = null, operationName = null)
            whenever(mockGraphQL.execute(any<ExecutionInput>())).thenThrow(AssertException(null))

            val result = controller.post(req, null)

            assertThat(result["errors"]).isNotNull
            assertThat(result["data"]).isNull()
            val errors = result["errors"] as List<*>
            assertThat(errors).isNotEmpty
            val error = errors[0] as Map<*, *>
            assertThat(error["message"] as String).contains("The type is not defined")
        }

        @Test
        fun `should handle generic Exception and return error spec`() {
            val req = GraphQLRequest(query = "query { test }", variables = null, operationName = null)
            whenever(mockGraphQL.execute(any<ExecutionInput>())).thenThrow(RuntimeException("Unexpected error"))

            val result = controller.post(req, null)

            assertThat(result["errors"]).isNotNull
            assertThat(result["data"]).isNull()
            val errors = result["errors"] as List<*>
            assertThat(errors).isNotEmpty
            val error = errors[0] as Map<*, *>
            assertThat(error["message"] as String).contains("GraphQL execution failed")
        }

        @Test
        fun `should handle generic Exception with null message`() {
            val req = GraphQLRequest(query = "query { test }", variables = null, operationName = null)
            whenever(mockGraphQL.execute(any<ExecutionInput>())).thenThrow(RuntimeException(null as String?))

            val result = controller.post(req, null)

            assertThat(result["errors"]).isNotNull
            assertThat(result["data"]).isNull()
            val errors = result["errors"] as List<*>
            assertThat(errors).isNotEmpty
            val error = errors[0] as Map<*, *>
            assertThat(error["message"] as String).contains("GraphQL execution failed")
        }
    }

    @Nested
    @DisplayName("Variables Handling")
    inner class VariablesHandlingTests {
        @Test
        fun `should use empty map when variables is null`() {
            val query = "query { test }"
            val req = GraphQLRequest(query = query, variables = null, operationName = null)

            whenever(mockGraphQL.execute(any<ExecutionInput>())).thenAnswer {
                ExecutionResultImpl.newExecutionResult()
                    .data(mapOf("test" to "value"))
                    .build()
            }

            val result = controller.post(req, null)
            assertThat(result["errors"]).isNull()

            val captor = ArgumentCaptor.forClass(ExecutionInput::class.java)
            verify(mockGraphQL).execute(captor.capture())
            val input = captor.value
            assertThat(input.variables).isEmpty()
        }

        @Test
        fun `should forward variables when provided`() {
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

            val result = controller.post(req, null)
            assertThat(result["errors"]).isNull()

            val captor = ArgumentCaptor.forClass(ExecutionInput::class.java)
            verify(mockGraphQL).execute(captor.capture())
            val input = captor.value
            assertThat(input.variables["id"]).isEqualTo("123")
            assertThat(input.variables["name"]).isEqualTo("Test")
        }
    }
}
