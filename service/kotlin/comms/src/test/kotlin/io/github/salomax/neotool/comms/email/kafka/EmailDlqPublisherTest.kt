package io.github.salomax.neotool.comms.email.kafka

import io.github.salomax.neotool.comms.email.dto.EmailContent
import io.github.salomax.neotool.comms.email.dto.EmailContentKind
import io.github.salomax.neotool.comms.email.events.EmailSendRequestedEvent
import io.github.salomax.neotool.comms.email.events.EmailSendRequestedPayload
import io.github.salomax.neotool.comms.events.CommsEventType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class EmailDlqPublisherTest {
    @Test
    fun `publishes to dlq successfully`() {
        val producer = mockk<EmailDlqProducer>(relaxed = true)
        val publisher = EmailDlqPublisher(producer)

        val message =
            EmailSendRequestedEvent(
                id = "id-1",
                type = CommsEventType.EMAIL_SEND_REQUESTED,
                traceId = "trace",
                payload =
                    EmailSendRequestedPayload(
                        to = "user@example.com",
                        content =
                            EmailContent(
                                kind = EmailContentKind.RAW,
                                subject = "Hello",
                                body = "World",
                            ),
                    ),
                createdAt = Instant.now(),
            )

        val success = publisher.publishToDlq(message, RuntimeException("boom"), 0)

        assertThat(success).isTrue()
        verify(exactly = 1) { producer.send(eq("id-1"), any()) }
    }

    @Test
    fun `returns false when dlq publish fails`() {
        val producer = mockk<EmailDlqProducer>()
        every { producer.send(any(), any()) } throws RuntimeException("fail")
        val publisher = EmailDlqPublisher(producer)

        val message =
            EmailSendRequestedEvent(
                id = "id-2",
                type = CommsEventType.EMAIL_SEND_REQUESTED,
                traceId = "trace",
                payload =
                    EmailSendRequestedPayload(
                        to = "user@example.com",
                        content =
                            EmailContent(
                                kind = EmailContentKind.RAW,
                                subject = "Hello",
                                body = "World",
                            ),
                    ),
                createdAt = Instant.now(),
            )

        val success = publisher.publishToDlq(message, RuntimeException("boom"), 1)

        assertThat(success).isFalse()
    }
}
