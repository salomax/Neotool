package io.github.salomax.neotool.comms.email.processor

import io.github.salomax.neotool.common.batch.MessageProcessor
import io.github.salomax.neotool.common.batch.exceptions.ValidationException
import io.github.salomax.neotool.comms.email.events.EmailSendRequestedEvent
import io.github.salomax.neotool.comms.email.provider.EmailProviderRegistry
import io.github.salomax.neotool.comms.email.validation.EmailContentValidator
import jakarta.inject.Singleton

@Singleton
class EmailSendProcessor(
    private val providerRegistry: EmailProviderRegistry,
) : MessageProcessor<EmailSendRequestedEvent> {
    override fun process(message: EmailSendRequestedEvent) {
        val payload = message.payload
        val content = payload.content

        if (!EmailContentValidator.isKindSupported(content.kind)) {
            throw ValidationException(EmailContentValidator.TEMPLATE_NOT_SUPPORTED)
        }

        val errors = EmailContentValidator.validate(content)
        if (errors.isNotEmpty()) {
            throw ValidationException(errors.first().message)
        }

        val provider = providerRegistry.resolve()
        provider.send(payload)
    }

    override fun getRecordId(message: EmailSendRequestedEvent): String {
        return message.id
    }
}
