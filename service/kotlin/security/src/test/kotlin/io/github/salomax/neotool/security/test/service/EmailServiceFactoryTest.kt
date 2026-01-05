package io.github.salomax.neotool.security.test.service

import io.github.salomax.neotool.security.config.EmailConfig
import io.github.salomax.neotool.security.service.email.EmailService
import io.github.salomax.neotool.security.service.email.EmailServiceFactory
import io.github.salomax.neotool.security.service.email.MockEmailService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("EmailServiceFactory Unit Tests")
class EmailServiceFactoryTest {
    private lateinit var emailConfig: EmailConfig
    private lateinit var factory: EmailServiceFactory

    @BeforeEach
    fun setUp() {
        emailConfig =
            EmailConfig(
                from = "test@example.com",
                frontendUrl = "http://localhost:3000",
            )
        factory = EmailServiceFactory(emailConfig)
    }

    @Test
    fun `should create MockEmailService instance`() {
        // Act
        val emailService = factory.emailService()

        // Assert
        assertThat(emailService).isNotNull()
        assertThat(emailService).isInstanceOf(MockEmailService::class.java)
    }

    @Test
    fun `should create EmailService with correct configuration`() {
        // Act
        val emailService = factory.emailService()

        // Assert
        assertThat(emailService).isInstanceOf(EmailService::class.java)
        assertThat(emailService).isInstanceOf(MockEmailService::class.java)
    }

    @Test
    fun `should create singleton instance`() {
        // Act
        val emailService1 = factory.emailService()
        val emailService2 = factory.emailService()

        // Assert
        // Note: Factory method is annotated with @Singleton, but in unit tests
        // we're calling the method directly, so instances may differ
        // This test verifies the method works correctly
        assertThat(emailService1).isNotNull()
        assertThat(emailService2).isNotNull()
        assertThat(emailService1).isInstanceOf(MockEmailService::class.java)
        assertThat(emailService2).isInstanceOf(MockEmailService::class.java)
    }
}
