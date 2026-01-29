package io.github.salomax.neotool.comms.email

import io.github.salomax.neotool.comms.email.dto.EmailContent
import io.github.salomax.neotool.comms.email.dto.EmailContentKind
import io.github.salomax.neotool.comms.email.dto.EmailSendRequest
import io.github.salomax.neotool.comms.email.events.EmailSendRequestedEvent
import io.github.salomax.neotool.comms.email.kafka.EmailSendProducer
import io.github.salomax.neotool.comms.email.service.KafkaEmailSendService
import io.github.salomax.neotool.comms.events.CommsEventType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KafkaEmailSendServiceTest {
    @Test
    fun `publishes email send requested event and returns request id`() {
        val producer = mockk<EmailSendProducer>()
        every { producer.send(any(), any()) } returns Unit

        val service = KafkaEmailSendService(producer)
        val request =
            EmailSendRequest(
                to = "user@example.com",
                content =
                    EmailContent(
                        kind = EmailContentKind.RAW,
                        subject = "Subject",
                        body = "Body",
                    ),
            )

        val keySlot = slot<String>()
        val eventSlot = slot<EmailSendRequestedEvent>()

        val result = service.requestSend(request)

        verify(exactly = 1) { producer.send(capture(keySlot), capture(eventSlot)) }
        assertThat(result.requestId).isEqualTo(keySlot.captured)
        assertThat(eventSlot.captured.id).isEqualTo(result.requestId)
        assertThat(eventSlot.captured.type).isEqualTo(CommsEventType.EMAIL_SEND_REQUESTED)
        assertThat(eventSlot.captured.payload.to).isEqualTo("user@example.com")
    }
}
