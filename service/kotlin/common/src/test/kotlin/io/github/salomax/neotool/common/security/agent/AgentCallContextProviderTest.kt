package io.github.salomax.neotool.common.security.agent

import graphql.GraphQLContext
import graphql.schema.DataFetchingEnvironment
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class AgentCallContextProviderTest {
    private lateinit var provider: AgentCallContextProvider

    @BeforeEach
    fun setUp() {
        provider = AgentCallContextProvider()
    }

    @Test
    fun `returns agent call context when all MCP headers are present in GraphQL context`() {
        val toolCallId = UUID.randomUUID()
        val env = mock<DataFetchingEnvironment>()
        val graphQlContext =
            GraphQLContext
                .newContext()
                .of(AgentCallContext.TOOL_CALL_ID_CONTEXT_KEY, toolCallId.toString())
                .of(AgentCallContext.CLIENT_ID_CONTEXT_KEY, "mcp-client")
                .of(AgentCallContext.TOKEN_ID_CONTEXT_KEY, "origin-token-id")
                .build()
        whenever(env.graphQlContext).thenReturn(graphQlContext)

        val context = provider.fromGraphQl(env)

        assertThat(context).isEqualTo(
            AgentCallContext(
                toolCallId = toolCallId,
                originClientId = "mcp-client",
                originTokenId = "origin-token-id",
            ),
        )
    }

    @Test
    fun `returns null when MCP headers are absent`() {
        val env = mock<DataFetchingEnvironment>()
        whenever(env.graphQlContext).thenReturn(GraphQLContext.newContext().build())

        assertThat(provider.fromGraphQl(env)).isNull()
    }

    @Test
    fun `rejects malformed tool call id`() {
        assertThatThrownBy {
            provider.fromValues(
                toolCallId = "not-a-uuid",
                originClientId = "mcp-client",
                originTokenId = "origin-token-id",
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining(AgentCallContext.TOOL_CALL_ID_HEADER)
    }
}
