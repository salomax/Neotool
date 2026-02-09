package io.github.salomax.neotool.comms.events

import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable
import java.time.Instant

@Introspected
@Serdeable
data class CommsEvent<TPayload>(
    val id: String,
    val type: String,
    val traceId: String,
    val payload: TPayload,
    val createdAt: Instant,
)
