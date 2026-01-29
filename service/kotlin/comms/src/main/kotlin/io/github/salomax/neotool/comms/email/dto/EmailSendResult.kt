package io.github.salomax.neotool.comms.email.dto

import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable

@Introspected
@Serdeable
data class EmailSendResult(
    val requestId: String,
    val status: EmailSendStatus = EmailSendStatus.QUEUED,
)
