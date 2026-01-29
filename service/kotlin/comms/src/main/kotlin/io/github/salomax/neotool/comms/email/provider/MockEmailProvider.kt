package io.github.salomax.neotool.comms.email.provider

import io.github.salomax.neotool.comms.email.dto.EmailContentKind
import io.github.salomax.neotool.comms.email.events.EmailSendRequestedPayload
import jakarta.inject.Singleton
import mu.KotlinLogging

@Singleton
class MockEmailProvider : EmailProvider {
    private val logger = KotlinLogging.logger {}

    override val id: String = "mock"

    override fun send(request: EmailSendRequestedPayload): EmailProviderResult {
        val content = request.content
        when (content.kind) {
            EmailContentKind.RAW -> {
                logger.info {
                    "[mock-email] to=${request.to} subject='${content.subject}' format=${content.format}"
                }
            }
            EmailContentKind.TEMPLATE -> {
                logger.info {
                    "[mock-email] to=${request.to} templateKey=${content.templateKey} locale=${content.locale}"
                }
            }
        }

        return EmailProviderResult(providerId = id, messageId = null)
    }
}
