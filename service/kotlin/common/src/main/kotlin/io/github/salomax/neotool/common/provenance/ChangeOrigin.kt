package io.github.salomax.neotool.common.provenance

import java.util.UUID

data class ChangeOrigin(
    val channel: OriginChannel,
    val clientId: String? = null,
    val toolCallId: UUID? = null,
) {
    init {
        if (channel == OriginChannel.MCP) {
            require(!clientId.isNullOrBlank()) { "clientId is required when channel is MCP" }
            require(toolCallId != null) { "toolCallId is required when channel is MCP" }
        }
    }

    companion object {
        fun ui(): ChangeOrigin = ChangeOrigin(channel = OriginChannel.UI)

        fun mcp(
            clientId: String,
            toolCallId: UUID,
        ): ChangeOrigin = ChangeOrigin(channel = OriginChannel.MCP, clientId = clientId, toolCallId = toolCallId)
    }
}
