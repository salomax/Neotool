package io.github.salomax.neotool.security.config

import io.micronaut.context.annotation.ConfigurationProperties

/**
 * Configuration properties for email service.
 * 
 * Properties can be set via environment variables or application.yml.
 * Environment variables take precedence (Micronaut handles this automatically via ${VAR:default} syntax).
 * 
 * Configuration options:
 * - email.from: From email address (can use ${EMAIL_FROM:noreply@neotool.com})
 * - email.frontend-url: Frontend URL for reset links (can use ${FRONTEND_URL:http://localhost:3000})
 * 
 * Note: Uses MockEmailService which logs emails to console/logs for testing.
 */
@ConfigurationProperties("email")
data class EmailConfig(
    /**
     * From email address.
     * Set via EMAIL_FROM environment variable or email.from in application.yml
     */
    val from: String = "noreply@neotool.com",
    
    /**
     * Frontend URL for generating reset links.
     * Set via FRONTEND_URL environment variable or email.frontend-url in application.yml
     */
    val frontendUrl: String = "http://localhost:3000"
) {
    /**
     * Get from email with environment variable override
     */
    fun resolveFrom(): String {
        return System.getenv("EMAIL_FROM") ?: from
    }
    
    /**
     * Get frontend URL with environment variable override
     */
    fun resolveFrontendUrl(): String {
        return System.getenv("FRONTEND_URL") ?: frontendUrl
    }
}

