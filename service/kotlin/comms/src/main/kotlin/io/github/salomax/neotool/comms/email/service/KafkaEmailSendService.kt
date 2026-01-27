package io.github.salomax.neotool.comms.email.service

import io.github.salomax.neotool.comms.email.dto.EmailSendRequest
import io.github.salomax.neotool.comms.email.dto.EmailSendResult
import io.github.salomax.neotool.comms.email.events.EmailSendRequestedPayload
import io.github.salomax.neotool.comms.email.kafka.EmailSendProducer
import io.github.salomax.neotool.comms.events.CommsEvent
import io.github.salomax.neotool.comms.events.CommsEventType
import jakarta.inject.Singleton
import java.time.Instant
import java.util.UUID

@Singleton
class KafkaEmailSendService(
    private val producer: EmailSendProducer,
) : EmailSendService {
    override fun requestSend(request: EmailSendRequest): EmailSendResult {
        val requestId = UUID.randomUUID().toString()
        val event = CommsEvent(
            id = requestId,
            type = CommsEventType.EMAIL_SEND_REQUESTED,
            traceId = requestId,
            payload = EmailSendRequestedPayload(
                to = request.to,
                content = request.content,
            ),
            createdAt = Instant.now(),
        )
        producer.send(requestId, event)
        return EmailSendResult(requestId = requestId)
    }
}
