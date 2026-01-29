package io.github.salomax.neotool.comms.email.provider

import io.github.salomax.neotool.comms.email.dto.EmailContentKind
import io.github.salomax.neotool.comms.email.events.EmailSendRequestedPayload
import io.github.salomax.neotool.comms.email.validation.EmailContentValidator
import io.micronaut.context.annotation.Requires
import io.micronaut.email.Email
import io.micronaut.email.EmailSender
import jakarta.inject.Singleton

@Singleton
@Requires(property = "comms.email.provider", value = "micronaut")
class MicronautEmailProvider(
    private val emailSender: EmailSender<Any, Any>,
    private val config: EmailProviderConfig,
) : EmailProvider {
    override val id: String = "micronaut"

    override fun send(request: EmailSendRequestedPayload): EmailProviderResult {
        val content = request.content
        val email =
            when (content.kind) {
                EmailContentKind.RAW -> {
                    // subject and body are guaranteed non-null after validation
                    val subject = requireNotNull(content.subject) { EmailContentValidator.SUBJECT_REQUIRED }
                    val body = requireNotNull(content.body) { EmailContentValidator.BODY_REQUIRED }

                    val builder =
                        Email.builder()
                            .from(config.from)
                            .to(request.to)
                            .subject(subject)
                    // HTML handling will be added with the template engine
                    builder.body(body)
                    builder
                }
                EmailContentKind.TEMPLATE -> {
                    throw IllegalStateException(EmailContentValidator.TEMPLATE_NOT_SUPPORTED)
                }
            }

        emailSender.send(email)
        return EmailProviderResult(providerId = id, messageId = null)
    }
}
