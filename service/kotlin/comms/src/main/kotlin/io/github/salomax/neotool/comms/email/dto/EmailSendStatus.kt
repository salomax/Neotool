package io.github.salomax.neotool.comms.email.dto

import io.micronaut.serde.annotation.Serdeable

@Serdeable
enum class EmailSendStatus {
    QUEUED,
}
