package io.github.salomax.neotool.example.test.graphql

import io.github.salomax.neotool.framework.graphql.GraphQLControllerBase
import io.github.salomax.neotool.framework.graphql.GraphQLRequest
import graphql.ExecutionInput
import graphql.ExecutionResultImpl
import graphql.GraphQL
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify

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
    val query = """
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
    val query = """
      query GetA(${'$'}id: ID!) { product(id: ${'$'}id) { id name } }
    """.trimIndent()
    val req = GraphQLRequest(
      query = query,
      variables = mapOf("id" to "123"),
      operationName = "GetA"
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
  fun `GraphQL exceptions are mapped to error spec`() {
    val req = GraphQLRequest(query = "query { test }", variables = null, operationName = null)
    whenever(mockGraphQL.execute(any<ExecutionInput>())).thenThrow(RuntimeException("test"))
    val result = controller.post(req, null)
    assertThat(result["errors"]).isNotNull
  }
}
