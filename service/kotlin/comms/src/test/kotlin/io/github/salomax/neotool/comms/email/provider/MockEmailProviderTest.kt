package io.github.salomax.neotool.comms.email.provider

import io.github.salomax.neotool.comms.email.dto.EmailContent
import io.github.salomax.neotool.comms.email.dto.EmailContentKind
import io.github.salomax.neotool.comms.email.events.EmailSendRequestedPayload
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MockEmailProviderTest {
    private val provider = MockEmailProvider()

    @Test
    fun `send with RAW content returns result`() {
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

        val result = provider.send(payload)

        assertThat(result.providerId).isEqualTo("mock")
        assertThat(result.messageId).isNull()
    }

    @Test
    fun `send with TEMPLATE content returns result`() {
        val payload =
            EmailSendRequestedPayload(
                to = "user@example.com",
                content =
                    EmailContent(
                        kind = EmailContentKind.TEMPLATE,
                        templateKey = "user.welcome",
                        locale = "en-US",
                        variables = mapOf("name" to "Alice"),
                    ),
            )

        val result = provider.send(payload)

        assertThat(result.providerId).isEqualTo("mock")
        assertThat(result.messageId).isNull()
    }
}
