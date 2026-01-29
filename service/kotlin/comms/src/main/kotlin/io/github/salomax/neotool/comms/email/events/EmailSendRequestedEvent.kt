package io.github.salomax.neotool.comms.email.events

import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable
import java.time.Instant

@Introspected
@Serdeable
data class EmailSendRequestedEvent(
    val id: String,
    val type: String,
    val traceId: String,
    val payload: EmailSendRequestedPayload,
    val createdAt: Instant,
)
