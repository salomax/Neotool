package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.security.config.EmailConfig
import io.github.salomax.neotool.security.service.email.EmailService
import io.github.salomax.neotool.security.service.email.EmailServiceFactory
import io.github.salomax.neotool.security.service.email.MockEmailService
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Replaces
import jakarta.inject.Singleton

/**
 * Factory that creates a MockEmailService for tests.
 *
 * This replaces the real EmailServiceFactory in test environments,
 * ensuring that tests never send actual emails.
 *
 * Use @Replaces(EmailServiceFactory::class) to ensure this factory
 * is used instead of the production factory in tests.
 */
@Factory
class MockEmailServiceFactory(
    private val emailConfig: EmailConfig,
) {
    /**
     * Creates a MockEmailService instance for testing.
     * This is marked as @Primary and @Replaces to ensure it's used
     * instead of the production EmailServiceFactory.
     */
    @Singleton
    @Primary
    @Replaces(EmailServiceFactory::class)
    fun emailService(): EmailService {
        return MockEmailService(emailConfig)
    }
}
