package io.github.salomax.neotool.security.domain

import java.time.Instant

/**
 * Result of resend verification email.
 */
sealed class ResendResult {
    object Success : ResendResult()
    data class RateLimited(val canResendAt: Instant) : ResendResult()
}
