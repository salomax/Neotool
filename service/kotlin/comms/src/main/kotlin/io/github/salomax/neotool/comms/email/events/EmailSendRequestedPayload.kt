package io.github.salomax.neotool.comms.email.events

import io.github.salomax.neotool.comms.email.dto.EmailContent
import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable

@Introspected
@Serdeable
data class EmailSendRequestedPayload(
    val to: String,
    val content: EmailContent,
)
