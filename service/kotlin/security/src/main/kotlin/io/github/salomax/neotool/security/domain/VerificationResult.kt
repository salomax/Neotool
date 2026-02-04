package io.github.salomax.neotool.security.domain

import io.github.salomax.neotool.security.model.UserEntity

/**
 * Result of email verification (magic link token).
 */
sealed class VerificationResult {
    data class Success(val user: UserEntity) : VerificationResult()

    object InvalidToken : VerificationResult()

    object NotFound : VerificationResult()

    object Expired : VerificationResult()

    object TooManyAttempts : VerificationResult()

    object AlreadyVerified : VerificationResult()
}
