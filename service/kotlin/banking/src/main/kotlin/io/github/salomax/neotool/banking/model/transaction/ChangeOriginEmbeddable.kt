package io.github.salomax.neotool.banking.model.transaction

import io.github.salomax.neotool.common.provenance.ChangeOrigin
import io.github.salomax.neotool.common.provenance.OriginChannel
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.util.UUID

@Embeddable
open class ChangeOriginEmbeddable(
    @Enumerated(EnumType.STRING)
    @Column(name = "origin_channel", nullable = false, length = 10)
    open var channel: OriginChannel = OriginChannel.UI,
    @Column(name = "origin_client_id")
    open var clientId: String? = null,
    @Column(name = "origin_tool_call_id")
    open var toolCallId: UUID? = null,
) {
    fun toDomain(): ChangeOrigin =
        ChangeOrigin(
            channel = channel,
            clientId = clientId,
            toolCallId = toolCallId,
        )

    companion object {
        fun fromDomain(origin: ChangeOrigin): ChangeOriginEmbeddable =
            ChangeOriginEmbeddable(
                channel = origin.channel,
                clientId = origin.clientId,
                toolCallId = origin.toolCallId,
            )
    }
}
