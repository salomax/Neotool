package io.github.salomax.neotool.security.graphql

import io.github.salomax.neotool.common.graphql.GraphQLPayloadException
import io.github.salomax.neotool.security.domain.ResendResult
import io.github.salomax.neotool.security.domain.VerificationResult
import io.github.salomax.neotool.security.error.SecurityErrorCode
import io.github.salomax.neotool.security.graphql.dto.ResendVerificationEmailPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.VerificationStatusDTO
import io.github.salomax.neotool.security.graphql.dto.VerifyEmailPayloadDTO
import io.github.salomax.neotool.security.graphql.mapper.SecurityGraphQLMapper
import io.github.salomax.neotool.security.repo.EmailVerificationRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.EmailVerificationService
import jakarta.inject.Singleton
import java.util.UUID
import io.github.salomax.neotool.common.graphql.payload.GraphQLError as PayloadGraphQLError

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
    private fun verificationError(
        code: SecurityErrorCode,
        field: List<String> = listOf("token"),
        message: String = code.defaultMessage,
    ): Nothing =
        throw GraphQLPayloadException(
            listOf(
                PayloadGraphQLError(
                    field = field,
                    message = message,
                    code = code.code,
                ),
            ),
        )

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
            VerificationResult.Expired -> verificationError(SecurityErrorCode.EMAIL_VERIFICATION_LINK_EXPIRED)
            VerificationResult.TooManyAttempts ->
                verificationError(
                    SecurityErrorCode.EMAIL_VERIFICATION_TOO_MANY_ATTEMPTS,
                )
            VerificationResult.AlreadyVerified ->
                VerifyEmailPayloadDTO(
                    success = true,
                    message = "Email already verified.",
                )
            VerificationResult.InvalidToken, VerificationResult.NotFound ->
                verificationError(SecurityErrorCode.EMAIL_VERIFICATION_INVALID_LINK)
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
                verificationError(
                    code = SecurityErrorCode.EMAIL_VERIFICATION_RESEND_RATE_LIMITED,
                    field = listOf("resendVerificationEmail"),
                )
        }
    }

    fun myVerificationStatus(userId: UUID): VerificationStatusDTO {
        val user =
            userRepository.findById(userId).orElse(null)
                ?: return VerificationStatusDTO(
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
