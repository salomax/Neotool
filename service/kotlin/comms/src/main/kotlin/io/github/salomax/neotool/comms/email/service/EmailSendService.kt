package io.github.salomax.neotool.comms.email.service

import io.github.salomax.neotool.comms.email.dto.EmailSendRequest
import io.github.salomax.neotool.comms.email.dto.EmailSendResult

interface EmailSendService {
    fun requestSend(request: EmailSendRequest): EmailSendResult
}
