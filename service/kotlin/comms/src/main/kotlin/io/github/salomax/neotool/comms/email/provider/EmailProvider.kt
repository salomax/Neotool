package io.github.salomax.neotool.comms.email.provider

import io.github.salomax.neotool.comms.email.events.EmailSendRequestedPayload

interface EmailProvider {
    val id: String

    fun send(request: EmailSendRequestedPayload): EmailProviderResult
}
