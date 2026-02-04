package io.github.salomax.neotool.comms.email

import io.github.salomax.neotool.common.batch.exceptions.ValidationException
import io.github.salomax.neotool.comms.email.dto.EmailContent
import io.github.salomax.neotool.comms.email.dto.EmailContentKind
import io.github.salomax.neotool.comms.email.events.EmailSendRequestedEvent
import io.github.salomax.neotool.comms.email.events.EmailSendRequestedPayload
import io.github.salomax.neotool.comms.email.processor.EmailSendProcessor
import io.github.salomax.neotool.comms.email.provider.EmailProvider
import io.github.salomax.neotool.comms.email.provider.EmailProviderRegistry
import io.github.salomax.neotool.comms.email.validation.EmailContentValidator
import io.github.salomax.neotool.comms.events.CommsEventType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

class EmailSendProcessorTest {
    private val registry = mockk<EmailProviderRegistry>()
    private val processor = EmailSendProcessor(registry)

    @Test
    fun `throws validation error for template content`() {
        val event =
            createEvent(
                EmailContent(
                    kind = EmailContentKind.TEMPLATE,
                    templateKey = "welcome",
                    locale = "en-US",
                    variables = mapOf("name" to "User"),
                ),
            )

        assertThatThrownBy { processor.process(event) }
            .isInstanceOf(ValidationException::class.java)
            .hasMessage(EmailContentValidator.TEMPLATE_MUST_BE_RENDERED)
    }

    @Test
    fun `throws validation error for RAW content with blank subject`() {
        val event =
            createEvent(
                EmailContent(
                    kind = EmailContentKind.RAW,
                    subject = "  ",
                    body = "World",
                ),
            )

        assertThatThrownBy { processor.process(event) }
            .isInstanceOf(ValidationException::class.java)
            .hasMessage(EmailContentValidator.SUBJECT_REQUIRED)
    }

    @Test
    fun `throws validation error for RAW content with null subject`() {
        val event =
            createEvent(
                EmailContent(
                    kind = EmailContentKind.RAW,
                    subject = null,
                    body = "World",
                ),
            )

        assertThatThrownBy { processor.process(event) }
            .isInstanceOf(ValidationException::class.java)
            .hasMessage(EmailContentValidator.SUBJECT_REQUIRED)
    }

    @Test
    fun `throws validation error for RAW content with blank body`() {
        val event =
            createEvent(
                EmailContent(
                    kind = EmailContentKind.RAW,
                    subject = "Hello",
                    body = "",
                ),
            )

        assertThatThrownBy { processor.process(event) }
            .isInstanceOf(ValidationException::class.java)
            .hasMessage(EmailContentValidator.BODY_REQUIRED)
    }

    @Test
    fun `sends raw email via resolved provider`() {
        val provider = mockk<EmailProvider>(relaxed = true)
        every { registry.resolve() } returns provider

        val event =
            createEvent(
                EmailContent(
                    kind = EmailContentKind.RAW,
                    subject = "Hello",
                    body = "World",
                ),
            )

        processor.process(event)

        verify(exactly = 1) { provider.send(event.payload) }
    }

    private fun createEvent(content: EmailContent): EmailSendRequestedEvent =
        EmailSendRequestedEvent(
            id = "req-${System.currentTimeMillis()}",
            type = CommsEventType.EMAIL_SEND_REQUESTED,
            traceId = "trace-1",
            payload =
                EmailSendRequestedPayload(
                    to = "user@example.com",
                    content = content,
                ),
            createdAt = Instant.now(),
        )
}
