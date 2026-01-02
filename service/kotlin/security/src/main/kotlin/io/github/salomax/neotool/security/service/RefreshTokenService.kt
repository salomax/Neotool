package io.github.salomax.neotool.security.service.jwt

import io.github.salomax.neotool.common.security.config.JwtConfig
import io.github.salomax.neotool.common.security.exception.AuthenticationRequiredException
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.security.model.RefreshTokenEntity
import io.github.salomax.neotool.security.repo.PrincipalRepository
import io.github.salomax.neotool.security.repo.RefreshTokenRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.auth.AuthContextFactory
import io.github.salomax.neotool.security.service.jwt.JwtTokenIssuer
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import mu.KotlinLogging
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/**
 * Service for managing refresh tokens with rotation support.
 *
 * Implements OAuth 2.0 BCP-compliant refresh token rotation:
 * - Each refresh token can only be used once
 * - Automatic token rotation on each refresh
 * - Reuse detection (theft detection)
 * - Token family revocation on reuse
 * - SHA-256 token hashing for storage
 */
@Singleton
open class RefreshTokenService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val userRepository: UserRepository,
    private val principalRepository: PrincipalRepository,
    private val jwtTokenIssuer: JwtTokenIssuer,
    private val authContextFactory: AuthContextFactory,
    private val jwtConfig: JwtConfig,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Refresh access token using a refresh token.
     * Implements automatic token rotation (OAuth 2.0 best practice).
     *
     * @param refreshToken The refresh token to use
     * @return TokenPair containing new access token and new refresh token
     * @throws AuthenticationRequiredException if token is invalid, expired, reused, or user is disabled
     */
    @Transactional
    open fun refreshAccessToken(refreshToken: String): TokenPair {
        // Hash the incoming token
        val tokenHash = hashToken(refreshToken)

        // Find token record by hash
        val tokenRecord =
            refreshTokenRepository.findByTokenHash(tokenHash)
                ?: throw AuthenticationRequiredException("Invalid refresh token")

        // Check if token was already used (potential theft detection)
        if (tokenRecord.replacedBy != null) {
            logger.warn {
                "Refresh token reuse detected for user ${tokenRecord.userId} - revoking token family ${tokenRecord.familyId}"
            }
            // Revoke entire token family (security best practice)
            revokeFamily(tokenRecord.familyId)
            throw AuthenticationRequiredException("Token reuse detected - session invalidated")
        }

        // Check if token is revoked
        if (tokenRecord.revokedAt != null) {
            logger.debug { "Refresh token was revoked for user ${tokenRecord.userId}" }
            throw AuthenticationRequiredException("Refresh token was revoked")
        }

        // Validate expiration
        if (tokenRecord.expiresAt.isBefore(Instant.now())) {
            logger.debug { "Refresh token expired for user ${tokenRecord.userId}" }
            throw AuthenticationRequiredException("Refresh token expired")
        }

        // Validate user still exists and enabled
        val user =
            userRepository.findById(tokenRecord.userId).orElse(null)
                ?: throw AuthenticationRequiredException("User not found")

        // Check if user principal is enabled
        val principal =
            principalRepository
                .findByPrincipalTypeAndExternalId(
                    PrincipalType.USER,
                    tokenRecord.userId.toString(),
                ).orElse(null)

        if (principal == null || !principal.enabled) {
            logger.debug { "User principal is disabled for user ID: ${tokenRecord.userId}" }
            throw AuthenticationRequiredException("User disabled")
        }

        // Build authentication context (loads current roles and permissions)
        val authContext = authContextFactory.build(user)

        // Generate new token pair
        val newAccessToken = jwtTokenIssuer.generateAccessToken(authContext)
        val newRefreshToken = jwtTokenIssuer.generateRefreshToken(user.id!!)
        val newRefreshTokenHash = hashToken(newRefreshToken)

        // Create new refresh token record (same family)
        val now = Instant.now()
        val newTokenRecord =
            RefreshTokenEntity(
                userId = user.id!!,
                tokenHash = newRefreshTokenHash,
                familyId = tokenRecord.familyId, // Same family
                issuedAt = now,
                expiresAt = now.plusSeconds(jwtConfig.refreshTokenExpirationSeconds),
            )
        val savedNewToken = refreshTokenRepository.save(newTokenRecord)

        // Mark old token as replaced
        tokenRecord.replacedBy = savedNewToken
        tokenRecord.updatedAt = now
        refreshTokenRepository.save(tokenRecord)

        logger.info { "Access token refreshed successfully for user: ${user.email}" }

        return TokenPair(
            accessToken = newAccessToken,
            refreshToken = newRefreshToken,
        )
    }

    /**
     * Create a new refresh token for a user.
     * Creates a new token family for the refresh token.
     *
     * @param userId The user ID
     * @return The plaintext refresh token (client should store this securely)
     */
    @Transactional
    open fun createRefreshToken(userId: UUID): String {
        val user =
            userRepository.findById(userId).orElseThrow {
                IllegalStateException("User not found with id: $userId")
            }

        // Generate JWT refresh token
        val refreshToken = jwtTokenIssuer.generateRefreshToken(userId)
        val tokenHash = hashToken(refreshToken)

        // Create new family ID for this token chain
        val familyId = UUID.randomUUID()

        // Calculate expiration
        val now = Instant.now()
        val expiresAt = now.plusSeconds(jwtConfig.refreshTokenExpirationSeconds)

        // Create refresh token record
        val tokenRecord =
            RefreshTokenEntity(
                userId = userId,
                tokenHash = tokenHash,
                familyId = familyId,
                issuedAt = now,
                expiresAt = expiresAt,
            )

        refreshTokenRepository.save(tokenRecord)

        logger.debug { "Created refresh token for user: ${user.email}" }

        return refreshToken
    }

    /**
     * Revoke a specific refresh token by its hash.
     *
     * @param tokenHash The hash of the token to revoke
     */
    @Transactional
    open fun revokeRefreshToken(tokenHash: String) {
        val tokenRecord = refreshTokenRepository.findByTokenHash(tokenHash)
        if (tokenRecord != null && tokenRecord.revokedAt == null) {
            tokenRecord.revokedAt = Instant.now()
            tokenRecord.updatedAt = Instant.now()
            refreshTokenRepository.save(tokenRecord)
            logger.debug { "Revoked refresh token for user ${tokenRecord.userId}" }
        }
    }

    /**
     * Revoke all refresh tokens for a user.
     * Used when user logs out or account is disabled.
     *
     * @param userId The user ID
     */
    @Transactional
    open fun revokeAllTokensForUser(userId: UUID) {
        val tokens = refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId)
        val now = Instant.now()
        tokens.forEach { token ->
            token.revokedAt = now
            token.updatedAt = now
            refreshTokenRepository.save(token)
        }
        if (tokens.isNotEmpty()) {
            logger.info { "Revoked ${tokens.size} refresh tokens for user $userId" }
        }
    }

    /**
     * Revoke all tokens in a token family.
     * Used when token reuse is detected (theft detection).
     *
     * @param familyId The token family ID
     */
    @Transactional
    open fun revokeFamily(familyId: UUID) {
        val tokens = refreshTokenRepository.findByFamilyId(familyId)
        val now = Instant.now()
        tokens.forEach { token ->
            if (token.revokedAt == null) {
                token.revokedAt = now
                token.updatedAt = now
                refreshTokenRepository.save(token)
            }
        }
        if (tokens.isNotEmpty()) {
            logger.warn { "Revoked ${tokens.size} tokens in family $familyId due to reuse detection" }
        }
    }

    /**
     * Hash a token using SHA-256.
     * Tokens are never stored in plaintext.
     *
     * @param token The plaintext token
     * @return SHA-256 hash as hexadecimal string
     */
    private fun hashToken(token: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
