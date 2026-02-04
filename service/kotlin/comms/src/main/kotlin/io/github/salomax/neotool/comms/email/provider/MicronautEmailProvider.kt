package io.github.salomax.neotool.comms.email.provider

import io.github.salomax.neotool.comms.email.dto.EmailBodyFormat
import io.github.salomax.neotool.comms.email.dto.EmailContentKind
import io.github.salomax.neotool.comms.email.events.EmailSendRequestedPayload
import io.github.salomax.neotool.comms.email.validation.EmailContentValidator
import io.micronaut.context.annotation.Requires
import io.micronaut.email.Email
import io.micronaut.email.EmailSender
import io.micronaut.email.MultipartBody
import jakarta.inject.Singleton

@Singleton
@Requires(property = "comms.email.provider", value = "micronaut")
class MicronautEmailProvider(
    private val emailSender: EmailSender<Email.Builder, Any>,
    private val config: EmailProviderConfig,
) : EmailProvider {
    override val id: String = "micronaut"

    override fun send(request: EmailSendRequestedPayload): EmailProviderResult {
        val content = request.content

        // Templates should be rendered before reaching the provider
        // But handle TEMPLATE kind gracefully in case rendering failed
        if (content.kind == EmailContentKind.TEMPLATE) {
            throw IllegalStateException(
                "TEMPLATE content should be rendered before reaching provider. " +
                    "This indicates a bug in the email send flow.",
            )
        }

        // subject and body are guaranteed non-null after validation for RAW content
        val subject = requireNotNull(content.subject) { EmailContentValidator.SUBJECT_REQUIRED }
        val body = requireNotNull(content.body) { EmailContentValidator.BODY_REQUIRED }

        // Build email with appropriate content type
        val emailBuilder =
            Email.builder()
                .from(config.from)
                .to(request.to)
                .subject(subject)

        // Set body based on format
        when (content.format) {
            EmailBodyFormat.HTML -> {
                // For HTML emails, use MultipartBody to specify content type
                emailBuilder.body(MultipartBody(body, "text/html"))
            }
            EmailBodyFormat.TEXT -> {
                emailBuilder.body(body)
            }
        }

        emailSender.send(emailBuilder)
        return EmailProviderResult(providerId = id, messageId = null)
    }
}
