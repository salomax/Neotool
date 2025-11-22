package io.github.salomax.neotool.security.service

import io.github.salomax.neotool.security.config.EmailConfig
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

/**
 * Mock implementation of EmailService for testing.
 *
 * Stores sent emails in memory instead of actually sending them.
 * Useful for unit tests and integration tests.
 */
@Singleton
class MockEmailService(
    emailConfig: EmailConfig,
) : EmailService(emailConfig) {
    /**
     * In-memory storage for sent emails.
     * Key: email address, Value: list of sent email data
     */
    private val sentEmails = ConcurrentHashMap<String, MutableList<SentEmail>>()

    /**
     * Data class to store email information.
     */
    data class SentEmail(
        val email: String,
        val token: String,
        val locale: String,
        val subject: String,
        val htmlContent: String,
    )

    override fun sendPasswordResetEmail(
        email: String,
        token: String,
        locale: String,
    ) {
        try {
            val template = loadEmailTemplate(locale)
            val resetUrl = buildResetUrl(token)
            val htmlContent = template.replace("{{RESET_URL}}", resetUrl)
            val subject = getSubject(locale)

            sentEmails.computeIfAbsent(email) { mutableListOf() }
                .add(SentEmail(email, token, locale, subject, htmlContent))

            // Log the reset link prominently for testing purposes
            logger.info {
                """
                |═══════════════════════════════════════════════════════════════
                |📧 MOCK EMAIL SENT (for testing)
                |═══════════════════════════════════════════════════════════════
                |To: $email
                |Subject: $subject
                |Locale: $locale
                |
                |🔗 PASSWORD RESET LINK (copy this for testing):
                |$resetUrl
                |
                |Token: $token
                |═══════════════════════════════════════════════════════════════
                """.trimMargin()
            }
        } catch (e: Exception) {
            logger.error(e) { "Error sending mock email to: $email" }
            // Don't throw - we want to return success even if email fails (security best practice)
        }
    }

    /**
     * Get all emails sent to a specific address.
     */
    fun getSentEmails(email: String): List<SentEmail> {
        return sentEmails[email]?.toList() ?: emptyList()
    }

    /**
     * Get the last email sent to a specific address.
     */
    fun getLastSentEmail(email: String): SentEmail? {
        return sentEmails[email]?.lastOrNull()
    }

    /**
     * Clear all sent emails (useful for test cleanup).
     */
    fun clearSentEmails() {
        sentEmails.clear()
    }

    /**
     * Get all sent emails across all addresses.
     */
    fun getAllSentEmails(): Map<String, List<SentEmail>> {
        return sentEmails.mapValues { it.value.toList() }
    }
}
