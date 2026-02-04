package io.github.salomax.neotool.security.graphql

import io.github.salomax.neotool.security.domain.ResendResult
import io.github.salomax.neotool.security.domain.VerificationResult
import io.github.salomax.neotool.security.graphql.dto.ResendVerificationEmailPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.VerificationStatusDTO
import io.github.salomax.neotool.security.graphql.dto.VerifyEmailPayloadDTO
import io.github.salomax.neotool.security.graphql.mapper.SecurityGraphQLMapper
import io.github.salomax.neotool.security.repo.EmailVerificationRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.EmailVerificationService
import jakarta.inject.Singleton
import java.util.UUID

/**
 * GraphQL resolver for email verification (verify with token, resend, status).
 */
@Singleton
class EmailVerificationResolver(
    private val emailVerificationService: EmailVerificationService,
    private val emailVerificationRepository: EmailVerificationRepository,
    private val userRepository: UserRepository,
    private val mapper: SecurityGraphQLMapper,
) {
    fun verifyEmailWithToken(
        token: UUID,
        ipAddress: String,
    ): VerifyEmailPayloadDTO {
        return when (val result = emailVerificationService.verifyWithToken(token, ipAddress)) {
            is VerificationResult.Success ->
                VerifyEmailPayloadDTO(
                    success = true,
                    user = mapper.userToDTO(result.user),
                    message = "Email verified successfully!",
                )
            VerificationResult.Expired ->
                VerifyEmailPayloadDTO(
                    success = false,
                    message = "This link has expired. Please request a new link.",
                )
            VerificationResult.TooManyAttempts ->
                VerifyEmailPayloadDTO(
                    success = false,
                    message = "Too many failed attempts. Please request a new link.",
                )
            VerificationResult.AlreadyVerified ->
                VerifyEmailPayloadDTO(
                    success = true,
                    message = "Email already verified.",
                )
            VerificationResult.InvalidToken, VerificationResult.NotFound ->
                VerifyEmailPayloadDTO(
                    success = false,
                    message = "Invalid or expired link. Please request a new link.",
                )
        }
    }

    fun resendVerificationEmail(
        userId: UUID,
        locale: String = "en",
    ): ResendVerificationEmailPayloadDTO {
        val user = userRepository.findById(userId).orElseThrow { IllegalStateException("User not found: $userId") }
        return when (
            val result =
                emailVerificationService.resendVerification(
                    userId,
                    user.email,
                    user.displayName ?: "",
                    locale,
                )
        ) {
            ResendResult.Success ->
                ResendVerificationEmailPayloadDTO(
                    success = true,
                    message = "New verification link sent!",
                )
            is ResendResult.RateLimited ->
                ResendVerificationEmailPayloadDTO(
                    success = false,
                    message = "Please wait before requesting another link.",
                    canResendAt = result.canResendAt.toString(),
                )
        }
    }

    fun myVerificationStatus(userId: UUID): VerificationStatusDTO {
        val user =
            userRepository.findById(userId).orElse(null) ?: return VerificationStatusDTO(
                emailVerified = false,
                canResendCode = false,
            )
        val active = emailVerificationRepository.findActiveByUserId(userId)
        // Resend mutation enforces rate limit and returns canResendAt when limited
        return VerificationStatusDTO(
            emailVerified = user.emailVerified,
            emailVerifiedAt = user.emailVerifiedAt?.toString(),
            verificationCodeSentAt = active?.createdAt?.toString(),
            verificationCodeExpiresAt = active?.expiresAt?.toString(),
            canResendCode = true,
            nextResendAvailableAt = null,
        )
    }
}
