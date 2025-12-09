package io.github.salomax.neotool.security.service

import io.github.salomax.neotool.security.model.PasswordResetAttemptEntity
import io.github.salomax.neotool.security.repo.PasswordResetAttemptRepository
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import mu.KotlinLogging
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Service for rate limiting password reset requests.
 *
 * Enforces a limit of 3 password reset requests per hour per email address.
 */
@Singleton
open class RateLimitService(
    private val passwordResetAttemptRepository: PasswordResetAttemptRepository,
) {
    private val logger = KotlinLogging.logger {}

    private val maxAttemptsPerHour = 3
    private val rateLimitWindowHours = 1L

    /**
     * Check if email is rate limited for password reset requests.
     *
     * @param email Email address to check
     * @return true if rate limited (too many requests), false otherwise
     */
    @Transactional
    open fun isRateLimited(email: String): Boolean {
        val now = Instant.now()
        val windowStart = now.minus(rateLimitWindowHours, ChronoUnit.HOURS)

        // Find existing attempts within the rate limit window
        val attempts = passwordResetAttemptRepository.findByEmailAndWindowStartGreaterThan(email, windowStart)

        // Get the most recent attempt (if any)
        val activeAttempt = attempts.maxByOrNull { it.windowStart }

        return if (activeAttempt != null && activeAttempt.windowStart.isAfter(windowStart)) {
            // We have an active attempt within the window
            if (activeAttempt.attemptCount >= maxAttemptsPerHour) {
                logger.warn { "Rate limit exceeded for email: $email (${activeAttempt.attemptCount} attempts)" }
                true
            } else {
                // Increment attempt count
                activeAttempt.attemptCount++
                passwordResetAttemptRepository.save(activeAttempt)
                false
            }
        } else {
            // No active attempt or window expired - create new attempt record
            val newAttempt =
                PasswordResetAttemptEntity(
                    email = email,
                    attemptCount = 1,
                    windowStart = now,
                )
            passwordResetAttemptRepository.save(newAttempt)
            false
        }
    }

    /**
     * Record a password reset attempt.
     *
     * @param email Email address
     * @return true if rate limited, false otherwise
     */
    @Transactional
    open fun recordAttempt(email: String): Boolean {
        return isRateLimited(email)
    }

    /**
     * Clean up old attempt records (older than 24 hours).
     * Should be called periodically (e.g., via scheduled task).
     */
    @Transactional
    open fun cleanupOldAttempts() {
        val cutoff = Instant.now().minus(24, ChronoUnit.HOURS)
        val oldAttempts = passwordResetAttemptRepository.findByCreatedAtLessThan(cutoff)
        if (oldAttempts.isNotEmpty()) {
            passwordResetAttemptRepository.deleteAll(oldAttempts)
            logger.debug { "Cleaned up ${oldAttempts.size} old password reset attempt records" }
        }
    }
}
