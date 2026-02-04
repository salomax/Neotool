package io.github.salomax.neotool.comms.email.service

import io.github.salomax.neotool.comms.email.dto.EmailContent
import io.github.salomax.neotool.comms.email.dto.EmailContentKind
import io.github.salomax.neotool.comms.email.dto.EmailSendRequest
import io.github.salomax.neotool.comms.email.dto.EmailSendResult
import io.github.salomax.neotool.comms.email.events.EmailSendRequestedEvent
import io.github.salomax.neotool.comms.email.events.EmailSendRequestedPayload
import io.github.salomax.neotool.comms.email.kafka.EmailSendProducer
import io.github.salomax.neotool.comms.events.CommsEventType
import io.github.salomax.neotool.comms.template.domain.Channel
import io.github.salomax.neotool.comms.template.service.TemplateService
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.time.Instant
import java.util.Locale
import java.util.UUID

@Singleton
class KafkaEmailSendService(
    private val producer: EmailSendProducer,
    private val templateService: TemplateService,
) : EmailSendService {
    private val logger = KotlinLogging.logger {}

    override fun requestSend(request: EmailSendRequest): EmailSendResult {
        val requestId = UUID.randomUUID().toString()

        // Render template if kind is TEMPLATE
        val content =
            if (request.content.kind == EmailContentKind.TEMPLATE) {
                renderTemplateContent(request.content)
            } else {
                request.content
            }

        val event =
            EmailSendRequestedEvent(
                id = requestId,
                type = CommsEventType.EMAIL_SEND_REQUESTED,
                traceId = requestId,
                payload =
                    EmailSendRequestedPayload(
                        to = request.to,
                        content = content,
                    ),
                createdAt = Instant.now(),
            )
        producer.send(requestId, event)
        return EmailSendResult(requestId = requestId)
    }

    /**
     * Render template content using TemplateService.
     */
    private fun renderTemplateContent(content: EmailContent): EmailContent {
        val templateKey =
            content.templateKey
                ?: throw IllegalArgumentException("templateKey is required for TEMPLATE content kind")

        val localeStr =
            content.locale ?: "en"
        val locale =
            parseLocale(localeStr)

        val variables = content.variables

        logger.debug { "Rendering template: key=$templateKey, locale=$locale" }

        val rendered =
            templateService.renderTemplate(
                templateKey = templateKey,
                locale = locale,
                channel = Channel.EMAIL,
                variables = variables,
            )

        // Convert rendered template back to EmailContent
        // After rendering, it's RAW content
        val format =
            if (rendered.body.contains("<html", ignoreCase = true)) {
                io.github.salomax.neotool.comms.email.dto.EmailBodyFormat.HTML
            } else {
                io.github.salomax.neotool.comms.email.dto.EmailBodyFormat.TEXT
            }
        return content.copy(
            kind = EmailContentKind.RAW,
            subject = rendered.subject,
            body = rendered.body,
            format = format,
        )
    }

    /**
     * Parse locale string to Locale object.
     */
    private fun parseLocale(localeStr: String): Locale {
        // Normalize to BCP 47 format (use - instead of _)
        val normalized = localeStr.replace("_", "-")
        return try {
            Locale.forLanguageTag(normalized)
        } catch (e: Exception) {
            // Fallback to simple language tag if parsing fails
            Locale.Builder().setLanguageTag(normalized).build()
        }
    }
}
