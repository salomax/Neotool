package io.github.salomax.neotool.comms.email

import io.github.salomax.neotool.comms.email.dto.EmailContent
import io.github.salomax.neotool.comms.email.dto.EmailContentKind
import io.github.salomax.neotool.comms.email.dto.EmailSendRequest
import io.github.salomax.neotool.comms.email.events.EmailSendRequestedEvent
import io.github.salomax.neotool.comms.email.kafka.EmailSendProducer
import io.github.salomax.neotool.comms.email.service.KafkaEmailSendService
import io.github.salomax.neotool.comms.events.CommsEventType
import io.github.salomax.neotool.comms.template.domain.Channel
import io.github.salomax.neotool.comms.template.renderer.RenderedTemplate
import io.github.salomax.neotool.comms.template.service.TemplateService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.Locale

class KafkaEmailSendServiceTest {
    @Test
    fun `publishes email send requested event and returns request id`() {
        val producer = mockk<EmailSendProducer>()
        every { producer.send(any(), any()) } returns Unit

        val templateService = mockk<TemplateService>(relaxed = true)

        val service = KafkaEmailSendService(producer, templateService)
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

    @Test
    fun `requestSend renders template content and publishes RAW content with HTML format`() {
        val producer = mockk<EmailSendProducer>()
        every { producer.send(any(), any()) } returns Unit

        val templateService = mockk<TemplateService>()
        val rendered =
            RenderedTemplate(
                channel = Channel.EMAIL,
                subject = "Welcome!",
                body = "<html><body>Hello</body></html>",
                metadata = emptyMap(),
            )
        every {
            templateService.renderTemplate(
                templateKey = "user.welcome",
                locale = Locale.ENGLISH,
                channel = Channel.EMAIL,
                variables = mapOf("name" to "Alice"),
            )
        } returns rendered

        val service = KafkaEmailSendService(producer, templateService)
        val request =
            EmailSendRequest(
                to = "user@example.com",
                content =
                    EmailContent(
                        kind = EmailContentKind.TEMPLATE,
                        templateKey = "user.welcome",
                        locale = "en",
                        variables = mapOf("name" to "Alice"),
                    ),
            )

        val eventSlot = slot<EmailSendRequestedEvent>()
        val result = service.requestSend(request)

        verify(exactly = 1) { producer.send(any(), capture(eventSlot)) }
        val content = eventSlot.captured.payload.content
        assertThat(content.kind).isEqualTo(EmailContentKind.RAW)
        assertThat(content.subject).isEqualTo("Welcome!")
        assertThat(content.body).contains("Hello")
        assertThat(content.body).containsIgnoringCase("<html")
    }

    @Test
    fun `requestSend renders template content and uses TEXT format when body has no html tag`() {
        val producer = mockk<EmailSendProducer>()
        every { producer.send(any(), any()) } returns Unit

        val templateService = mockk<TemplateService>()
        val rendered =
            RenderedTemplate(
                channel = Channel.EMAIL,
                subject = "Plain",
                body = "Plain text body",
                metadata = emptyMap(),
            )
        every {
            templateService.renderTemplate(
                templateKey = "plain.template",
                locale = Locale.forLanguageTag("pt-BR"),
                channel = Channel.EMAIL,
                variables = emptyMap(),
            )
        } returns rendered

        val service = KafkaEmailSendService(producer, templateService)
        val request =
            EmailSendRequest(
                to = "user@example.com",
                content =
                    EmailContent(
                        kind = EmailContentKind.TEMPLATE,
                        templateKey = "plain.template",
                        locale = "pt-BR",
                        variables = emptyMap(),
                    ),
            )

        val eventSlot = slot<EmailSendRequestedEvent>()
        service.requestSend(request)

        verify(exactly = 1) { producer.send(any(), capture(eventSlot)) }
        val content = eventSlot.captured.payload.content
        assertThat(content.kind).isEqualTo(EmailContentKind.RAW)
        assertThat(content.subject).isEqualTo("Plain")
        assertThat(content.body).isEqualTo("Plain text body")
    }

    @Test
    fun `requestSend with TEMPLATE content uses default locale when locale is null`() {
        val producer = mockk<EmailSendProducer>()
        every { producer.send(any(), any()) } returns Unit

        val templateService = mockk<TemplateService>()
        val rendered =
            RenderedTemplate(
                channel = Channel.EMAIL,
                subject = "Hi",
                body = "Body",
                metadata = emptyMap(),
            )
        every {
            templateService.renderTemplate(
                templateKey = "welcome",
                locale = Locale.ENGLISH,
                channel = Channel.EMAIL,
                variables = emptyMap(),
            )
        } returns rendered

        val service = KafkaEmailSendService(producer, templateService)
        val request =
            EmailSendRequest(
                to = "user@example.com",
                content =
                    EmailContent(
                        kind = EmailContentKind.TEMPLATE,
                        templateKey = "welcome",
                        locale = null,
                        variables = emptyMap(),
                    ),
            )

        val eventSlot = slot<EmailSendRequestedEvent>()
        service.requestSend(request)

        verify(exactly = 1) { templateService.renderTemplate("welcome", Locale.ENGLISH, Channel.EMAIL, emptyMap()) }
        verify(exactly = 1) { producer.send(any(), capture(eventSlot)) }
    }

    @Test
    fun `requestSend with TEMPLATE content and null templateKey throws`() {
        val producer = mockk<EmailSendProducer>()
        val templateService = mockk<TemplateService>(relaxed = true)

        val service = KafkaEmailSendService(producer, templateService)
        val request =
            EmailSendRequest(
                to = "user@example.com",
                content =
                    EmailContent(
                        kind = EmailContentKind.TEMPLATE,
                        templateKey = null,
                        locale = "en",
                        variables = emptyMap(),
                    ),
            )

        assertThatThrownBy { service.requestSend(request) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("templateKey")

        verify(exactly = 0) { producer.send(any(), any()) }
    }
}
