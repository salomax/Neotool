package io.github.salomax.neotool.comms.email.provider

import io.github.salomax.neotool.comms.email.dto.EmailContent
import io.github.salomax.neotool.comms.email.dto.EmailContentKind
import io.github.salomax.neotool.comms.email.events.EmailSendRequestedPayload
import io.micronaut.email.Email
import io.micronaut.email.EmailSender
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class MicronautEmailProviderTest {
    private val sender = mockk<EmailSender<Email.Builder, Any>>(relaxed = true)
    private val config = EmailProviderConfig(from = "from@example.com")
    private val provider = MicronautEmailProvider(sender, config)

    @Test
    fun `sends raw email`() {
        val payload =
            EmailSendRequestedPayload(
                to = "user@example.com",
                content =
                    EmailContent(
                        kind = EmailContentKind.RAW,
                        subject = "Hello",
                        body = "World",
                    ),
            )

        provider.send(payload)

        verify(exactly = 1) { sender.send(any<Email.Builder>()) }
    }

    @Test
    fun `throws on template content`() {
        val payload =
            EmailSendRequestedPayload(
                to = "user@example.com",
                content =
                    EmailContent(
                        kind = EmailContentKind.TEMPLATE,
                        templateKey = "welcome",
                        locale = "en-US",
                    ),
            )

        assertThatThrownBy { provider.send(payload) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("TEMPLATE content should be rendered before reaching provider")
    }
}
