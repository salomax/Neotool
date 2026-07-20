package io.github.salomax.neotool.security.service.jwt

import io.github.salomax.neotool.common.security.config.JwtConfig
import io.github.salomax.neotool.common.security.exception.AuthenticationRequiredException
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.tracing.TraceExecution
import io.github.salomax.neotool.security.model.RefreshTokenEntity
import io.github.salomax.neotool.security.model.TokenPair
import io.github.salomax.neotool.security.repo.PrincipalRepository
import io.github.salomax.neotool.security.repo.RefreshTokenRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.authentication.AuthContextFactory
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
@TraceExecution
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
    @Transactional(dontRollbackOn = [AuthenticationRequiredException::class])
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
                "Refresh token reuse detected for user ${tokenRecord.userId} - " +
                    "revoking token family ${tokenRecord.familyId}"
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

        val grantedScopes = tokenRecord.grantedScopes?.split(" ")?.filter { it.isNotBlank() }
        val clientId = tokenRecord.clientId

        // Generate new token pair
        val newAccessToken =
            if (clientId != null && grantedScopes != null) {
                jwtTokenIssuer.generateOAuthAccessToken(
                    userId = user.id!!,
                    email = user.email,
                    clientId = clientId,
                    scopes = grantedScopes,
                    resource = tokenRecord.resource ?: "oauth2",
                )
            } else {
                // First-party app refresh tokens still rehydrate the full user auth context.
                val authContext = authContextFactory.build(user)
                jwtTokenIssuer.generateAccessToken(authContext)
            }
        val newRefreshToken = jwtTokenIssuer.generateRefreshToken(user.id!!)
        val newRefreshTokenHash = hashToken(newRefreshToken)

        // Create new refresh token record (same family)
        val now = Instant.now()
        val newTokenRecord =
            RefreshTokenEntity(
                userId = user.id!!,
                tokenHash = newRefreshTokenHash,
                // Same family
                familyId = tokenRecord.familyId,
                clientId = tokenRecord.clientId,
                grantedScopes = tokenRecord.grantedScopes,
                resource = tokenRecord.resource,
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
            scope = tokenRecord.grantedScopes,
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

    @Transactional
    open fun createOAuthRefreshToken(
        userId: UUID,
        clientId: String,
        grantedScopes: List<String>,
        resource: String,
    ): Pair<String, UUID> {
        userRepository.findById(userId).orElseThrow {
            IllegalStateException("User not found with id: $userId")
        }
        val refreshToken = jwtTokenIssuer.generateRefreshToken(userId)
        val familyId = UUID.randomUUID()
        val now = Instant.now()
        refreshTokenRepository.save(
            RefreshTokenEntity(
                userId = userId,
                tokenHash = hashToken(refreshToken),
                familyId = familyId,
                clientId = clientId,
                grantedScopes = grantedScopes.joinToString(" "),
                resource = resource,
                issuedAt = now,
                expiresAt = now.plusSeconds(jwtConfig.refreshTokenExpirationSeconds),
            ),
        )
        return refreshToken to familyId
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
    @Transactional(Transactional.TxType.REQUIRES_NEW)
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
    fun hashToken(token: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /**
     * Check if a refresh token exists in the RefreshTokenService.
     * Used by AuthenticationService to determine if token is managed by new system.
     *
     * @param tokenHash The hashed token
     * @return true if token exists (regardless of validity), false otherwise
     */
    fun tokenExists(tokenHash: String): Boolean {
        return refreshTokenRepository.findByTokenHash(tokenHash) != null
    }

    /**
     * Check whether a plaintext refresh token belongs to an OAuth grant.
     */
    fun isOAuthRefreshToken(refreshToken: String): Boolean {
        val tokenRecord = refreshTokenRepository.findByTokenHash(hashToken(refreshToken))
        return tokenRecord?.clientId != null
    }

    /**
     * Check if a refresh token is valid (exists and not revoked).
     * Used by AuthenticationService for backward compatibility.
     *
     * @param tokenHash The hashed token
     * @return true if token is valid, false otherwise
     */
    fun isTokenValid(tokenHash: String): Boolean {
        val tokenRecord = refreshTokenRepository.findByTokenHash(tokenHash)
        return tokenRecord != null &&
            tokenRecord.revokedAt == null &&
            tokenRecord.replacedBy == null &&
            tokenRecord.expiresAt.isAfter(Instant.now())
    }
}
