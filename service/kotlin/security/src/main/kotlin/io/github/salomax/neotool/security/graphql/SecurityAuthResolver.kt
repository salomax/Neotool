package io.github.salomax.neotool.security.graphql

import io.github.salomax.neotool.common.graphql.InputValidator
import io.github.salomax.neotool.common.graphql.payload.GraphQLPayload
import io.github.salomax.neotool.common.graphql.payload.GraphQLPayloadFactory
import io.github.salomax.neotool.security.graphql.dto.RequestPasswordResetInputDTO
import io.github.salomax.neotool.security.graphql.dto.RequestPasswordResetPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.ResetPasswordPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.SignInPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.SignUpPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.UserDTO
import io.github.salomax.neotool.security.graphql.mapper.SecurityGraphQLMapper
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.authentication.AuthContextFactory
import io.github.salomax.neotool.security.service.authentication.AuthenticationService
import io.github.salomax.neotool.security.service.authorization.AuthorizationService
import io.github.salomax.neotool.security.service.jwt.RefreshTokenService
import jakarta.inject.Singleton
import jakarta.validation.ConstraintViolationException
import mu.KotlinLogging

/**
 * GraphQL resolver for authentication operations in security module.
 *
 * Uses JWT tokens for authentication:
 * - Access tokens: Short-lived (configurable via jwt.access-token-expiration-seconds, default: 15 minutes),
 *   stateless, used for API requests
 * - Refresh tokens: Long-lived (configurable via jwt.refresh-token-expiration-seconds, default: 7 days),
 *   stored in database, used to obtain new access tokens
 *
 * @see io.github.salomax.neotool.security.config.JwtConfig
 */
@Singleton
class SecurityAuthResolver(
    private val authenticationService: AuthenticationService,
    private val authorizationService: AuthorizationService,
    private val authContextFactory: AuthContextFactory,
    private val userRepository: UserRepository,
    private val inputValidator: InputValidator,
    private val mapper: SecurityGraphQLMapper,
    private val refreshTokenService: RefreshTokenService,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Sign in mutation resolver
     */
    fun signIn(input: Map<String, Any?>): GraphQLPayload<SignInPayloadDTO> =
        try {
            val email = input["email"] as? String ?: throw IllegalArgumentException("Email is required")
            val password = input["password"] as? String ?: throw IllegalArgumentException("Password is required")
            val rememberMe = input["rememberMe"] as? Boolean ?: false

            logger.debug { "Sign in attempt for email: $email" }

            val user =
                authenticationService.authenticate(email, password)
                    ?: throw IllegalArgumentException("Invalid email or password")

            // Build authentication context (loads roles and permissions)
            val authContext = authContextFactory.build(user)

            // Generate JWT access token (short-lived, stateless) with permissions
            val token = authenticationService.generateAccessToken(authContext)

            // Handle remember me - generate and store refresh token
            var refreshToken: String? = null
            if (rememberMe) {
                val userId = requireNotNull(user.id) { "User ID is required for refresh token generation" }
                // Use new refresh token service with rotation support
                refreshToken = refreshTokenService.createRefreshToken(userId)
            }

            logger.info { "User signed in successfully: ${user.email}" }

            val payload =
                SignInPayloadDTO(
                    token = token,
                    refreshToken = refreshToken,
                    user = mapper.userToDTO(user),
                )

            GraphQLPayloadFactory.success(payload)
        } catch (e: Exception) {
            logger.warn { "Sign in failed: ${e.message}" }
            GraphQLPayloadFactory.error(e)
        }

    /**
     * Get current user from JWT access token.
     *
     * Validates the JWT token and returns the authenticated user.
     *
     * @param token The JWT access token from Authorization header
     * @return UserDTO if token is valid, null otherwise
     */
    fun getCurrentUser(token: String?): UserDTO? {
        if (token == null) {
            logger.debug { "No token provided" }
            return null
        }

        // Validate JWT access token
        val user =
            authenticationService.validateAccessToken(token)
                ?: run {
                    logger.debug { "Invalid or expired access token" }
                    return null
                }

        return mapper.userToDTO(user)
    }

    /**
     * Sign in with OAuth mutation resolver
     */
    fun signInWithOAuth(input: Map<String, Any?>): GraphQLPayload<SignInPayloadDTO> =
        try {
            val provider = input["provider"] as? String ?: throw IllegalArgumentException("Provider is required")
            val idToken = input["idToken"] as? String ?: throw IllegalArgumentException("ID token is required")
            val rememberMe = input["rememberMe"] as? Boolean ?: false

            logger.debug { "OAuth sign in attempt with provider: $provider" }

            // Authenticate with OAuth (validates token and creates/finds user)
            val user =
                authenticationService.authenticateWithOAuth(provider, idToken)
                    ?: throw IllegalArgumentException("OAuth authentication failed")

            // Build authentication context (loads roles and permissions)
            val authContext = authContextFactory.build(user)

            // Generate JWT access token (short-lived, stateless) with permissions
            val token = authenticationService.generateAccessToken(authContext)

            // Handle remember me - generate and store refresh token
            var refreshToken: String? = null
            if (rememberMe) {
                val userId = requireNotNull(user.id) { "User ID is required for refresh token generation" }
                // Use new refresh token service with rotation support
                refreshToken = refreshTokenService.createRefreshToken(userId)
            }

            logger.info { "User signed in with OAuth successfully: ${user.email} (provider: $provider)" }

            val payload =
                SignInPayloadDTO(
                    token = token,
                    refreshToken = refreshToken,
                    user = mapper.userToDTO(user),
                )

            GraphQLPayloadFactory.success(payload)
        } catch (e: Exception) {
            logger.warn { "OAuth sign in failed: ${e.message}" }
            GraphQLPayloadFactory.error(e)
        }

    /**
     * Sign up mutation resolver
     */
    fun signUp(input: Map<String, Any?>): GraphQLPayload<SignUpPayloadDTO> =
        try {
            val name = input["name"] as? String ?: throw IllegalArgumentException("Name is required")
            val email = input["email"] as? String ?: throw IllegalArgumentException("Email is required")
            val password = input["password"] as? String ?: throw IllegalArgumentException("Password is required")

            logger.debug { "Sign up attempt for email: $email" }

            // Register user (validates email uniqueness and password strength)
            val user = authenticationService.registerUser(name, email, password)

            // Build authentication context (loads roles and permissions)
            val authContext = authContextFactory.build(user)

            // Generate JWT access token with permissions
            val token = authenticationService.generateAccessToken(authContext)

            // Generate refresh token (for automatic sign-in after signup)
            val userId = requireNotNull(user.id) { "User ID is required for refresh token generation" }
            // Use new refresh token service with rotation support
            val refreshToken = refreshTokenService.createRefreshToken(userId)

            logger.info { "User signed up successfully: ${user.email}" }

            val payload =
                SignUpPayloadDTO(
                    token = token,
                    refreshToken = refreshToken,
                    user = mapper.userToDTO(user),
                )

            GraphQLPayloadFactory.success(payload)
        } catch (e: Exception) {
            logger.warn { "Sign up failed: ${e.message}" }
            GraphQLPayloadFactory.error(e)
        }

    /**
     * Request password reset mutation resolver
     */
    fun requestPasswordReset(input: Map<String, Any?>): GraphQLPayload<RequestPasswordResetPayloadDTO> {
        return try {
            logger.debug { "Password reset request received with input: $input" }

            // Extract and validate email
            val email = input["email"] as? String
            if (email.isNullOrBlank()) {
                logger.warn { "Password reset request with empty email" }
                // Return success for security (don't reveal if email exists)
                return GraphQLPayloadFactory.success(
                    RequestPasswordResetPayloadDTO(
                        success = true,
                        message = "If an account with that email exists, a password reset link has been sent.",
                    ),
                )
            }

            // Deserialize input to DTO
            val dto =
                RequestPasswordResetInputDTO(
                    email = email.trim(),
                    locale = (input["locale"] as? String)?.takeIf { it.isNotBlank() } ?: "en",
                )

            // Validate DTO
            try {
                inputValidator.validate(dto)
            } catch (e: ConstraintViolationException) {
                logger.warn { "Password reset request validation failed: ${e.message}" }
                // Return success for security (don't reveal if email exists)
                return GraphQLPayloadFactory.success(
                    RequestPasswordResetPayloadDTO(
                        success = true,
                        message = "If an account with that email exists, a password reset link has been sent.",
                    ),
                )
            }

            logger.debug { "Password reset request for email: ${dto.email}, locale: ${dto.locale}" }

            // Request password reset (always returns true for security)
            authenticationService.requestPasswordReset(dto.email, dto.locale ?: "en")

            val payload =
                RequestPasswordResetPayloadDTO(
                    success = true,
                    message = "If an account with that email exists, a password reset link has been sent.",
                )

            GraphQLPayloadFactory.success(payload)
        } catch (e: Exception) {
            logger.error(e) { "Password reset request failed: ${e.message}" }
            // Still return success for security (don't reveal if email exists)
            val payload =
                RequestPasswordResetPayloadDTO(
                    success = true,
                    message = "If an account with that email exists, a password reset link has been sent.",
                )
            GraphQLPayloadFactory.success(payload)
        }
    }

    /**
     * Refresh access token mutation resolver.
     *
     * Validates a refresh token and issues a new access token with current permissions.
     * Implements token rotation - returns both new access token and new refresh token.
     */
    fun refreshAccessToken(input: Map<String, Any?>): GraphQLPayload<SignInPayloadDTO> =
        try {
            val refreshToken =
                input["refreshToken"] as? String
                    ?: throw IllegalArgumentException("Refresh token is required")

            logger.debug { "Refresh access token attempt" }

            // Use RefreshTokenService for token rotation
            val tokenPair = refreshTokenService.refreshAccessToken(refreshToken)

            // Get user from the new access token to build response
            val user =
                authenticationService.validateAccessToken(tokenPair.accessToken)
                    ?: throw IllegalArgumentException("Failed to validate new access token")

            logger.info { "Access token refreshed successfully for user: ${user.email}" }

            val payload =
                SignInPayloadDTO(
                    token = tokenPair.accessToken,
                    // Return new refresh token (rotation)
                    refreshToken = tokenPair.refreshToken,
                    user = mapper.userToDTO(user),
                )

            GraphQLPayloadFactory.success(payload)
        } catch (e: Exception) {
            logger.warn { "Refresh access token failed: ${e.message}" }
            GraphQLPayloadFactory.error(e)
        }

    /**
     * Reset password mutation resolver
     */
    fun resetPassword(input: Map<String, Any?>): GraphQLPayload<ResetPasswordPayloadDTO> =
        try {
            val token = input["token"] as? String ?: throw IllegalArgumentException("Token is required")
            val newPassword =
                input["newPassword"] as? String ?: throw IllegalArgumentException(
                    "New password is required",
                )

            logger.debug { "Password reset attempt with token" }

            // Reset password
            authenticationService.resetPassword(token, newPassword)

            logger.info { "Password reset successful" }

            val payload =
                ResetPasswordPayloadDTO(
                    success = true,
                    message = "Password has been reset successfully. You can now sign in with your new password.",
                )

            GraphQLPayloadFactory.success(payload)
        } catch (e: Exception) {
            logger.warn { "Password reset failed: ${e.message}" }
            GraphQLPayloadFactory.error(e)
        }
}
