package io.github.salomax.neotool.common.provenance

import io.github.salomax.neotool.common.security.agent.AgentCallContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class ChangeOriginTest {
    @Test
    fun `mcp origin requires client id`() {
        val toolCallId = UUID.randomUUID()

        val error =
            assertThrows<IllegalArgumentException> {
                ChangeOrigin(channel = OriginChannel.MCP, clientId = " ", toolCallId = toolCallId)
            }

        assertThat(error).hasMessage("clientId is required when channel is MCP")
    }

    @Test
    fun `nullable agent call context defaults to ui`() {
        val origin = (null as AgentCallContext?).toChangeOriginOrUi()

        assertThat(origin).isEqualTo(ChangeOrigin.ui())
    }

    @Test
    fun `agent call context converts to mcp origin`() {
        val toolCallId = UUID.randomUUID()
        val context =
            AgentCallContext(
                toolCallId = toolCallId,
                originClientId = "mcp-client",
                originTokenId = "origin-token-id",
            )

        val origin = context.toChangeOrigin()

        assertThat(origin).isEqualTo(ChangeOrigin.mcp("mcp-client", toolCallId))
    }
}
