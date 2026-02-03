package io.github.salomax.neotool.security.service

import io.github.salomax.neotool.security.domain.EmailVerification
import io.github.salomax.neotool.security.domain.ResendResult
import io.github.salomax.neotool.security.domain.VerificationResult
import io.github.salomax.neotool.security.model.EmailVerificationEntity
import io.github.salomax.neotool.security.model.UserEntity
import io.github.salomax.neotool.security.repo.EmailVerificationRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.email.EmailService
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Service for email verification (magic link only) after signup.
 * OWASP-aligned: 8h expiry, rate limit, hashed token.
 */
@Singleton
open class EmailVerificationService(
    private val emailVerificationRepository: EmailVerificationRepository,
    private val userRepository: UserRepository,
    private val emailService: EmailService,
    @param:Value("\${app.verification.code-expiration-hours:8}") private val linkExpirationHours: Long,
    @param:Value("\${app.verification.max-attempts:5}") private val maxAttempts: Int,
    @param:Value("\${app.verification.resend-rate-limit-per-hour:3}") private val resendRateLimitPerHour: Int,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Generate and send verification link (token) after user signup.
     */
    open fun initiateVerification(
        userId: UUID,
        userEmail: String,
        userName: String,
        locale: String,
        ipAddress: String? = null,
    ): EmailVerification {
        val token = UUID.randomUUID()
        val tokenHash = sha256Hex(token.toString())
        val expiresAt = Instant.now().plus(linkExpirationHours, ChronoUnit.HOURS)

        val entity =
            EmailVerificationEntity(
                user = userRepository.findById(userId).orElseThrow { IllegalStateException("User not found: $userId") },
                tokenHash = tokenHash,
                maxAttempts = maxAttempts,
                expiresAt = expiresAt,
                createdByIp = ipAddress,
            )

        val saved = emailVerificationRepository.save(entity)

        emailService.sendVerificationEmail(
            to = userEmail,
            userName = userName,
            token = token,
            expiresAt = expiresAt,
            locale = locale,
        )

        logger.info { "Email verification initiated for user $userId (email: $userEmail)" }

        return toDomain(saved)
    }

    /**
     * Verify email using magic link token.
     */
    @jakarta.transaction.Transactional
    open fun verifyWithToken(
        token: UUID,
        ipAddress: String,
    ): VerificationResult {
        val tokenHash = sha256Hex(token.toString())
        val verification =
            emailVerificationRepository.findByTokenHash(tokenHash)
                ?: return VerificationResult.InvalidToken

        if (verification.isExpired()) {
            return VerificationResult.Expired
        }

        if (verification.isVerified()) {
            val user = userRepository.findById(verification.user.id!!).orElse(null)
            if (user != null && user.emailVerified) {
                return VerificationResult.AlreadyVerified
            }
        }

        return completeVerification(verification, ipAddress)
    }

    /**
     * Resend verification email (rate-limited).
     */
    open fun resendVerification(
        userId: UUID,
        userEmail: String,
        userName: String,
        locale: String,
    ): ResendResult {
        val since = Instant.now().minus(1, ChronoUnit.HOURS)
        val recent = emailVerificationRepository.findRecentByUserId(userId, since)

        if (recent.size >= resendRateLimitPerHour) {
            val oldest = recent.minByOrNull { it.createdAt }!!
            val canResendAt = oldest.createdAt.plus(1, ChronoUnit.HOURS)
            logger.warn { "Resend rate limit for user $userId, canResendAt=$canResendAt" }
            return ResendResult.RateLimited(canResendAt)
        }

        val active = emailVerificationRepository.findActiveByUserId(userId)
        if (active != null) {
            active.markInvalidated()
            emailVerificationRepository.save(active)
        }

        initiateVerification(userId, userEmail, userName, locale, null)
        logger.info { "Verification email resent for user $userId" }
        return ResendResult.Success
    }

    private fun completeVerification(
        verification: EmailVerificationEntity,
        ipAddress: String,
    ): VerificationResult {
        val user = verification.user
        val userId = requireNotNull(user.id) { "User ID required" }

        user.emailVerified = true
        user.emailVerifiedAt = Instant.now()
        userRepository.save(user)

        verification.markVerified(ipAddress)
        emailVerificationRepository.save(verification)

        logger.info { "Email verified for user $userId from ip=$ipAddress" }
        return VerificationResult.Success(user)
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun toDomain(entity: EmailVerificationEntity): EmailVerification {
        val user = entity.user
        return EmailVerification(
            id = entity.id!!,
            userId = user.id!!,
            expiresAt = entity.expiresAt,
            verifiedAt = entity.verifiedAt,
            attempts = entity.attempts,
            maxAttempts = entity.maxAttempts,
            canRetry = entity.canRetry(),
            isExpired = entity.isExpired(),
            isVerified = entity.isVerified(),
        )
    }
}
