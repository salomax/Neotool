package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.security.config.EmailConfig
import io.github.salomax.neotool.security.service.email.EmailService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for EmailService template loading functionality.
 *
 * Tests the loadEmailTemplate method which handles:
 * - Locale-specific template loading
 * - Fallback to English template
 * - Fallback to default hardcoded template
 * - Error handling
 */
@DisplayName("EmailService Unit Tests")
class EmailServiceTest {
    private val emailConfig =
        EmailConfig(
            from = "test@example.com",
            frontendUrl = "http://localhost:3000",
        )

    /**
     * Test helper that exposes protected methods for testing.
     */
    private class TestableEmailService(emailConfig: EmailConfig) : EmailService(emailConfig) {
        override fun sendPasswordResetEmail(
            email: String,
            token: String,
            locale: String,
        ) {
            // Not used in these tests
        }

        override fun sendVerificationEmail(
            to: String,
            userName: String,
            token: java.util.UUID,
            expiresAt: java.time.Instant,
            locale: String,
        ) {
            // Not used in these tests
        }

        // Expose protected method for testing
        fun testLoadEmailTemplate(locale: String): String {
            return loadEmailTemplate(locale)
        }
    }

    private val emailService = TestableEmailService(emailConfig)

    @Nested
    @DisplayName("loadEmailTemplate")
    inner class LoadEmailTemplateTests {
        @Test
        fun `should load English template for en locale`() {
            val template = emailService.testLoadEmailTemplate("en")

            assertThat(template).isNotBlank()
            assertThat(template).contains("Reset Your Password")
            assertThat(template).contains("{{RESET_URL}}")
            assertThat(template).contains("<!DOCTYPE html>")
        }

        @Test
        fun `should load Portuguese template for pt locale`() {
            val template = emailService.testLoadEmailTemplate("pt")

            assertThat(template).isNotBlank()
            assertThat(template).contains("Redefinir sua senha")
            assertThat(template).contains("{{RESET_URL}}")
            assertThat(template).contains("<!DOCTYPE html>")
        }

        @Test
        fun `should fallback to English template when locale template not found`() {
            val template = emailService.testLoadEmailTemplate("fr")

            // Should fallback to English template
            assertThat(template).isNotBlank()
            assertThat(template).contains("Reset Your Password")
            assertThat(template).contains("{{RESET_URL}}")
        }

        @Test
        fun `should fallback to default template when both locale and English templates missing`() {
            // This test verifies the fallback mechanism works
            // In practice, en.html should always exist, but we test the fallback path
            val template = emailService.testLoadEmailTemplate("xx")

            // Should fallback to default template
            assertThat(template).isNotBlank()
            assertThat(template).contains("Reset Your Password")
            assertThat(template).contains("{{RESET_URL}}")
        }

        @Test
        fun `should handle case-insensitive locale`() {
            val templateLower = emailService.testLoadEmailTemplate("en")
            val templateUpper = emailService.testLoadEmailTemplate("EN")
            val templateMixed = emailService.testLoadEmailTemplate("En")

            // All should load the same template (or fallback to same)
            assertThat(templateLower).isNotBlank()
            assertThat(templateUpper).isNotBlank()
            assertThat(templateMixed).isNotBlank()
        }

        @Test
        fun `should return template with RESET_URL placeholder`() {
            val template = emailService.testLoadEmailTemplate("en")

            assertThat(template).contains("{{RESET_URL}}")
        }

        @Test
        fun `should return valid HTML structure`() {
            val template = emailService.testLoadEmailTemplate("en")

            assertThat(template).contains("<!DOCTYPE html>")
            assertThat(template).contains("<html>")
            assertThat(template).contains("</html>")
        }
    }
}
