package io.github.salomax.neotool.security.service.email

import io.github.salomax.neotool.security.config.EmailConfig
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton
import mu.KotlinLogging

/**
 * Factory for creating EmailService instances.
 *
 * Always uses MockEmailService which logs emails to the console/logs.
 * Reset links are logged for easy testing.
 */
@Factory
class EmailServiceFactory(
    private val emailConfig: EmailConfig,
) {
    private val logger = KotlinLogging.logger {}

    @Singleton
    fun emailService(): EmailService {
        logger.info { "Using MockEmailService for email delivery (emails logged to console)" }
        return MockEmailService(emailConfig)
    }
}
