package io.github.salomax.neotool.security.domain

import java.time.Instant
import java.util.UUID

/**
 * Domain model for email verification (returned from initiateVerification).
 */
data class EmailVerification(
    val id: UUID,
    val userId: UUID,
    val expiresAt: Instant,
    val verifiedAt: Instant?,
    val attempts: Int,
    val maxAttempts: Int,
    val canRetry: Boolean,
    val isExpired: Boolean,
    val isVerified: Boolean,
)
