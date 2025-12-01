package io.github.salomax.neotool.security.service

import com.password4j.Password
import io.github.salomax.neotool.security.model.UserEntity
import io.github.salomax.neotool.security.repo.UserRepository
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import mu.KotlinLogging
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Service for handling authentication operations including password hashing,
 * signin validation, and JWT token generation.
 *
 * Uses Argon2id for password hashing (OWASP recommended standard).
 * Uses JWT for access and refresh tokens.
 */
@Singleton
open class AuthenticationService(
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
    private val emailService: EmailService,
    private val rateLimitService: RateLimitService,
    private val oauthProviderRegistry: OAuthProviderRegistry,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Hash a password using Argon2id (OWASP recommended).
     *
     * Argon2id is the winner of the Password Hashing Competition and provides
     * superior resistance to GPU/ASIC attacks with memory-hard properties.
     *
     * @see https://owasp.org/
     * @param password The plain text password to hash
     * @return The hashed password string (includes salt and parameters)
     */
    fun hashPassword(password: String): String {
        return Password.hash(password)
            .addRandomSalt()
            .withArgon2()
            .result
    }

    /**
     * Verify a password against a stored hash.
     *
     * Uses Argon2id for password verification. The hash format is automatically
     * detected by the Password4j library.
     *
     * @param password The plain text password to verify
     * @param hash The stored Argon2id hash
     * @return true if password matches, false otherwise
     */
    fun verifyPassword(
        password: String,
        hash: String?,
    ): Boolean {
        return try {
            Password.check(password, hash).withArgon2()
        } catch (e: Exception) {
            logger.warn { "Error verifying password: ${e.message}" }
            false
        }
    }

    /**
     * Authenticate a user with email and password.
     *
     * @return UserEntity if authentication succeeds, null otherwise
     */
    fun authenticate(
        email: String,
        password: String,
    ): UserEntity? {
        if (password.isBlank()) {
            logger.debug { "Password cannot be empty" }
            return null
        }

        val user =
            userRepository.findByEmail(email) ?: run {
                logger.debug { "User not found with email: $email" }
                return null
            }

        if (user.passwordHash == null) {
            logger.debug { "User has no password hash: $email" }
            return null
        }

        if (!verifyPassword(password, user.passwordHash!!)) {
            logger.debug { "Invalid password for user: $email" }
            return null
        }

        logger.info { "User authenticated successfully: $email" }
        return user
    }

    /**
     * Generate JWT access token for a user.
     *
     * Access tokens are short-lived and stateless.
     *
     * @param user The authenticated user
     * @return JWT access token string
     */
    fun generateAccessToken(user: UserEntity): String {
        val userId = requireNotNull(user.id) { "User ID is required for access token generation" }
        return jwtService.generateAccessToken(userId, user.email)
    }

    /**
     * Generate JWT refresh token for a user.
     *
     * Refresh tokens are long-lived and stored in the database for revocation.
     *
     * @param user The authenticated user
     * @return JWT refresh token string
     */
    fun generateRefreshToken(user: UserEntity): String {
        val userId = requireNotNull(user.id) { "User ID is required for refresh token generation" }
        return jwtService.generateRefreshToken(userId)
    }

    /**
     * Validate a JWT access token and return the user.
     *
     * @param token The JWT access token
     * @return UserEntity if token is valid, null otherwise
     */
    fun validateAccessToken(token: String): UserEntity? {
        val userId = jwtService.getUserIdFromToken(token) ?: return null

        // Verify it's an access token
        if (!jwtService.isAccessToken(token)) {
            logger.debug { "Token is not an access token" }
            return null
        }

        // Fetch user from database
        return userRepository.findById(userId).orElse(null)
    }

    /**
     * Validate a JWT refresh token and return the user.
     *
     * @param token The JWT refresh token
     * @return UserEntity if token is valid, null otherwise
     */
    fun validateRefreshToken(token: String): UserEntity? {
        val userId = jwtService.getUserIdFromToken(token) ?: return null

        // Verify it's a refresh token
        if (!jwtService.isRefreshToken(token)) {
            logger.debug { "Token is not a refresh token" }
            return null
        }

        // Fetch user from database
        val user = userRepository.findById(userId).orElse(null) ?: return null

        // Verify the refresh token matches the stored one (for revocation support)
        // If rememberMeToken is null, the refresh token was revoked
        if (user.rememberMeToken == null) {
            logger.debug { "Refresh token was revoked for user: ${user.email}" }
            return null
        }

        // For backward compatibility, we check if the stored token matches
        // In a production system, you might want to store token hashes instead
        if (user.rememberMeToken != token) {
            logger.debug { "Refresh token does not match stored token for user: ${user.email}" }
            return null
        }

        return user
    }

    /**
     * Generate a remember me token (legacy method, now uses JWT refresh token)
     * @deprecated Use generateRefreshToken instead
     */
    @Deprecated("Use generateRefreshToken instead", ReplaceWith("generateRefreshToken(user)"))
    fun generateRememberMeToken(): String {
        return UUID.randomUUID().toString() + "-" + System.currentTimeMillis()
    }

    /**
     * Save remember me token for a user
     */
    @Transactional
    open fun saveRememberMeToken(
        userId: UUID,
        token: String,
    ): UserEntity {
        // Fetch the entity and update it in place
        val user =
            userRepository.findById(userId).orElseThrow {
                IllegalStateException("User not found with id: $userId")
            }
        user.rememberMeToken = token
        return userRepository.save(user)
    }

    /**
     * Authenticate using remember me token (legacy method).
     * Also supports JWT refresh tokens for backward compatibility.
     *
     * @deprecated Use validateRefreshToken for JWT tokens
     */
    fun authenticateByToken(token: String): UserEntity? {
        // Try JWT refresh token first
        val jwtUser = validateRefreshToken(token)
        if (jwtUser != null) {
            return jwtUser
        }

        // Fall back to legacy remember me token
        return userRepository.findByRememberMeToken(token)
    }

    /**
     * Clear remember me token for a user
     */
    @Transactional
    open fun clearRememberMeToken(userId: UUID): UserEntity {
        // Fetch the entity and update it in place
        val user =
            userRepository.findById(userId).orElseThrow {
                IllegalStateException("User not found with id: $userId")
            }
        user.rememberMeToken = null
        return userRepository.save(user)
    }

    /**
     * Validate password strength.
     *
     * Password must be at least 8 characters with:
     * - At least one uppercase letter
     * - At least one lowercase letter
     * - At least one number
     * - At least one special character
     *
     * @param password The password to validate
     * @return true if password meets requirements, false otherwise
     */
    fun validatePasswordStrength(password: String): Boolean {
        if (password.length < 8) {
            return false
        }

        val hasUppercase = password.any { it.isUpperCase() }
        val hasLowercase = password.any { it.isLowerCase() }
        val hasNumber = password.any { it.isDigit() }
        val hasSpecialChar = password.any { !it.isLetterOrDigit() }

        return hasUppercase && hasLowercase && hasNumber && hasSpecialChar
    }

    /**
     * Register a new user with email and password.
     *
     * Validates:
     * - Email uniqueness
     * - Email format (handled by DTO validation)
     * - Password strength
     *
     * @param name The user's display name
     * @param email The user's email (must be unique)
     * @param password The plain text password (will be hashed)
     * @return UserEntity if registration succeeds
     * @throws IllegalArgumentException if email already exists or password is invalid
     */
    @Transactional
    open fun registerUser(
        name: String,
        email: String,
        password: String,
    ): UserEntity {
        // Check if email already exists
        val existingUser = userRepository.findByEmail(email)
        if (existingUser != null) {
            logger.warn { "Registration attempt with existing email: $email" }
            throw IllegalArgumentException("Email already exists")
        }

        // Validate password strength
        if (!validatePasswordStrength(password)) {
            logger.warn { "Registration attempt with weak password for email: $email" }
            throw IllegalArgumentException(
                "Password must be at least 8 characters with uppercase, lowercase, number and special character",
            )
        }

        // Hash password
        val passwordHash = hashPassword(password)

        // Create user entity
        val user =
            UserEntity(
                email = email,
                displayName = name,
                passwordHash = passwordHash,
            )

        // Save user
        val savedUser = userRepository.save(user)

        logger.info { "User registered successfully: $email" }
        return savedUser
    }

    @Transactional
    open fun saveUser(user: UserEntity): UserEntity {
        return userRepository.save(user)
    }

    /**
     * Request a password reset for a user.
     *
     * Generates a secure reset token, saves it to the database, and sends an email.
     * Always returns true for security (don't reveal if email exists).
     *
     * @param email User's email address
     * @param locale Locale for email template (default: "en")
     * @return true (always, for security)
     */
    @Transactional
    open fun requestPasswordReset(
        email: String,
        locale: String = "en",
    ): Boolean {
        // Check rate limit first
        if (rateLimitService.isRateLimited(email)) {
            logger.warn { "Password reset request rate limited for email: $email" }
            // Still return true for security (don't reveal rate limiting)
            return true
        }

        // Find user by email
        val user =
            userRepository.findByEmail(email) ?: run {
                logger.debug { "Password reset requested for non-existent email: $email" }
                // Return true even if user doesn't exist (security best practice)
                return true
            }

        // Check if user has a password (OAuth users might not)
        if (user.passwordHash == null) {
            logger.debug { "Password reset requested for user without password: $email" }
            // Return true even if user has no password (security best practice)
            return true
        }

        // Generate secure random token (UUID)
        val resetToken = UUID.randomUUID().toString()

        // Set expiration to 1 hour from now
        val expirationTime = Instant.now().plus(1, java.time.temporal.ChronoUnit.HOURS)

        // Invalidate any existing reset tokens for this user
        user.passwordResetToken = resetToken
        user.passwordResetExpiresAt = expirationTime
        user.passwordResetUsedAt = null

        // Save user
        userRepository.save(user)

        // Send email (don't throw if it fails - we want to return success)
        try {
            emailService.sendPasswordResetEmail(email, resetToken, locale)
        } catch (e: Exception) {
            logger.error(e) { "Failed to send password reset email to: $email" }
            // Continue - we still return true
        }

        logger.info { "Password reset token generated for email: $email" }
        return true
    }

    /**
     * Validate a password reset token.
     *
     * @param token The reset token
     * @return UserEntity if token is valid, null otherwise
     */
    fun validateResetToken(token: String): UserEntity? {
        if (token.isBlank()) {
            logger.debug { "Empty reset token provided" }
            return null
        }

        val user =
            userRepository.findByPasswordResetToken(token) ?: run {
                logger.debug { "Invalid reset token: token not found" }
                return null
            }

        // Check if token is expired
        val now = Instant.now()
        if (user.passwordResetExpiresAt == null || user.passwordResetExpiresAt!!.isBefore(now)) {
            logger.debug { "Reset token expired for user: ${user.email}" }
            return null
        }

        // Check if token was already used
        if (user.passwordResetUsedAt != null) {
            logger.debug { "Reset token already used for user: ${user.email}" }
            return null
        }

        logger.debug { "Reset token validated for user: ${user.email}" }
        return user
    }

    /**
     * Reset password using a valid reset token.
     *
     * @param token The reset token
     * @param newPassword The new password
     * @return UserEntity if successful
     * @throws IllegalArgumentException if token is invalid or password is weak
     */
    @Transactional
    open fun resetPassword(
        token: String,
        newPassword: String,
    ): UserEntity {
        // Validate token
        val user = validateResetToken(token) ?: throw IllegalArgumentException("Invalid or expired reset token")

        // Validate password strength
        if (!validatePasswordStrength(newPassword)) {
            logger.warn { "Password reset attempt with weak password for user: ${user.email}" }
            throw IllegalArgumentException(
                "Password must be at least 8 characters with uppercase, lowercase, number and special character",
            )
        }

        // Hash new password
        val passwordHash = hashPassword(newPassword)

        // Update user password
        user.passwordHash = passwordHash

        // Mark token as used
        user.passwordResetUsedAt = Instant.now()

        // Clear reset token fields (optional, but good practice)
        user.passwordResetToken = null
        user.passwordResetExpiresAt = null

        // Save user
        val savedUser = userRepository.save(user)

        logger.info { "Password reset successfully for user: ${user.email}" }
        return savedUser
    }

    /**
     * Authenticate a user using OAuth provider.
     *
     * Validates the OAuth ID token, extracts user claims, and either finds an existing user
     * by email or creates a new user account. OAuth users don't have a password hash.
     *
     * @param provider The OAuth provider name (e.g., "google", "microsoft")
     * @param idToken The OAuth ID token (JWT) from the provider
     * @return UserEntity if authentication succeeds, null otherwise
     * @throws IllegalArgumentException if provider is not supported or token is invalid
     */
    @Transactional
    open fun authenticateWithOAuth(
        provider: String,
        idToken: String,
    ): UserEntity? {
        logger.debug { "OAuth authentication attempt with provider: $provider" }

        // Get the OAuth provider
        val oauthProvider =
            oauthProviderRegistry.getProvider(provider)
                ?: throw IllegalArgumentException("Unsupported OAuth provider: $provider")

        // Validate token and extract claims
        val claims =
            oauthProvider.validateAndExtractClaims(idToken)
                ?: throw IllegalArgumentException("Invalid OAuth token")

        // Find existing user by email
        val existingUser = userRepository.findByEmail(claims.email)

        if (existingUser != null) {
            // Update display name if it's not set or if OAuth provides a name
            if (existingUser.displayName.isNullOrBlank() && !claims.name.isNullOrBlank()) {
                existingUser.displayName = claims.name
                userRepository.save(existingUser)
            }

            logger.info { "OAuth user signed in: ${claims.email} (existing user)" }
            return existingUser
        }

        // Create new user (OAuth users don't have a password)
        // OAuth users don't have passwords
        val newUser =
            UserEntity(
                email = claims.email,
                displayName = claims.name,
                passwordHash = null,
            )

        val savedUser = userRepository.save(newUser)

        logger.info { "OAuth user created and signed in: ${claims.email} (new user)" }
        return savedUser
    }
}
