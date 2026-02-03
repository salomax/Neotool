package io.github.salomax.neotool.security.service.email

import io.github.salomax.neotool.security.config.EmailConfig
import mu.KotlinLogging
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Abstract service for sending emails.
 *
 * Provides common functionality for email template loading and subject generation.
 * Implementations should provide the actual email sending mechanism.
 *
 * Supports i18n email templates loaded from resources.
 */
abstract class EmailService(
    protected val emailConfig: EmailConfig,
) {
    protected val logger = KotlinLogging.logger {}

    /**
     * Send password reset email to user.
     *
     * @param email User's email address
     * @param token Password reset token
     * @param locale Locale for email template (default: "en")
     */
    abstract fun sendPasswordResetEmail(
        email: String,
        token: String,
        locale: String = "en",
    )

    /**
     * Send email verification (magic link) after signup.
     *
     * @param to Recipient email
     * @param userName Display name for greeting
     * @param token UUID for magic link
     * @param expiresAt When the link expires
     * @param locale Locale for template (default: "en")
     */
    abstract fun sendVerificationEmail(
        to: String,
        userName: String,
        token: java.util.UUID,
        expiresAt: java.time.Instant,
        locale: String = "en",
    )

    /**
     * Load email template for given locale.
     * Falls back to English if locale template not found.
     *
     * @deprecated Use Comms template engine (auth.password-reset via TemplateService) instead.
     *             Kept for MockEmailService and tests. Will be removed in a future release.
     */
    @Deprecated("Use TemplateService with auth.password-reset template. Kept for MockEmailService.")
    protected fun loadEmailTemplate(locale: String): String {
        val templatePath = "/emails/password-reset/$locale.html"
        val fallbackPath = "/emails/password-reset/en.html"

        return try {
            val resource =
                javaClass.getResourceAsStream(templatePath)
                    ?: javaClass.getResourceAsStream(fallbackPath)
                    ?: throw IllegalStateException("Email template not found")
            resource.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to load email template for locale: $locale, using fallback" }
            getDefaultTemplate()
        }
    }

    /**
     * Get email subject for given locale.
     *
     * @deprecated Subject now comes from template.yml (auth.password-reset). Kept for MockEmailService.
     */
    @Deprecated("Use TemplateService; subject is in template.yml. Kept for MockEmailService.")
    protected fun getSubject(locale: String): String =
        when (locale.lowercase()) {
            "pt" -> "Redefinir sua senha"
            else -> "Reset your password"
        }

    /**
     * Build reset URL from token.
     */
    protected fun buildResetUrl(token: String): String {
        val frontendUrl = emailConfig.resolveFrontendUrl()
        return "$frontendUrl/reset-password?token=${URLEncoder.encode(token, StandardCharsets.UTF_8)}"
    }

    /**
     * Default email template (fallback).
     *
     * @deprecated Use Comms template engine. Kept for MockEmailService fallback.
     */
    @Deprecated("Use TemplateService. Kept for MockEmailService fallback.")
    protected fun getDefaultTemplate(): String =
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <title>Reset Your Password</title>
        </head>
        <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
            <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                <h1 style="color: #2c3e50;">Reset Your Password</h1>
                <p>You requested to reset your password. Click the link below to reset it:</p>
                <p style="margin: 30px 0;">
                    <a href="{{RESET_URL}}" style="background-color: #3498db; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px; display: inline-block;">Reset Password</a>
                </p>
                <p>Or copy and paste this link into your browser:</p>
                <p style="word-break: break-all; color: #7f8c8d;">{{RESET_URL}}</p>
                <p style="margin-top: 30px; font-size: 12px; color: #95a5a6;">
                    This link will expire in 1 hour. If you didn't request this, please ignore this email.
                </p>
            </div>
        </body>
        </html>
        """.trimIndent()
}
