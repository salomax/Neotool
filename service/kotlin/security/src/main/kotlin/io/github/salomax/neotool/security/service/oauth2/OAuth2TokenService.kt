package io.github.salomax.neotool.security.service.oauth2

import io.github.salomax.neotool.common.security.config.JwtConfig
import io.github.salomax.neotool.common.security.jwt.JwtTokenValidator
import io.github.salomax.neotool.security.http.dto.OAuth2TokenRequest
import io.github.salomax.neotool.security.http.dto.OAuth2TokenResponse
import io.github.salomax.neotool.security.model.OAuth2AuthorizationRequestStatus
import io.github.salomax.neotool.security.repo.OAuth2AuthorizationCodeRepository
import io.github.salomax.neotool.security.repo.OAuth2AuthorizationRequestRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.authentication.AuthContextFactory
import io.github.salomax.neotool.security.service.jwt.JwtTokenIssuer
import io.github.salomax.neotool.security.service.jwt.RefreshTokenService
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import java.time.Instant

@Singleton
open class OAuth2TokenService(
    private val authorizationCodeRepository: OAuth2AuthorizationCodeRepository,
    private val authorizationRequestRepository: OAuth2AuthorizationRequestRepository,
    private val userRepository: UserRepository,
    private val jwtTokenIssuer: JwtTokenIssuer,
    private val jwtTokenValidator: JwtTokenValidator,
    private val refreshTokenService: RefreshTokenService,
    private val tokenHashing: OAuth2TokenHashing,
    private val clientRegistry: OAuth2ClientRegistry,
    private val jwtConfig: JwtConfig,
    private val tokenExchangeService: OAuth2TokenExchangeService,
    private val authContextFactory: AuthContextFactory,
    private val tokenRateLimitService: OAuth2TokenRateLimitService,
) {
    @Transactional
    open fun issue(request: OAuth2TokenRequest): OAuth2TokenResponse {
        resolveRateLimitClientId(request)?.let { clientId ->
            val rateLimitDecision = tokenRateLimitService.check(clientId)
            if (!rateLimitDecision.allowed) {
                throw OAuth2RateLimitException(
                    message = "OAuth2 token rate limit exceeded",
                    retryAfterSeconds = rateLimitDecision.retryAfterSeconds,
                )
            }
        }

        return when (request.grant_type) {
            "authorization_code" -> exchangeAuthorizationCode(request)
            "refresh_token" -> refresh(request)
            OAuth2TokenExchangeService.TOKEN_EXCHANGE_GRANT_TYPE -> tokenExchangeService.exchange(request)
            else -> throw OAuth2InvalidRequestException("Unsupported grant_type")
        }
    }

    private fun exchangeAuthorizationCode(request: OAuth2TokenRequest): OAuth2TokenResponse {
        val code =
            request.code?.takeIf { it.isNotBlank() }
                ?: throw OAuth2InvalidGrantException("code is required")
        val clientId =
            request.client_id?.takeIf { it.isNotBlank() }
                ?: throw OAuth2InvalidGrantException("client_id is required")
        val redirectUri =
            request.redirect_uri?.takeIf { it.isNotBlank() }
                ?: throw OAuth2InvalidGrantException("redirect_uri is required")
        val codeVerifier =
            request.code_verifier?.takeIf { it.isNotBlank() }
                ?: throw OAuth2InvalidRequestException("code_verifier is required")
        val codeHash = tokenHashing.sha256Hex(code)
        val codeRecord =
            authorizationCodeRepository.findByCodeHash(codeHash)
                ?: throw OAuth2InvalidGrantException("Invalid authorization code")
        val now = Instant.now()
        if (codeRecord.consumedAt != null) {
            codeRecord.issuedRefreshTokenFamilyId?.let { refreshTokenService.revokeFamily(it) }
            throw OAuth2InvalidGrantException("Authorization code has already been used")
        }
        val consumed = authorizationCodeRepository.consumeIfActive(codeHash, now)
        if (consumed != 1L) {
            throw OAuth2InvalidGrantException("Authorization code expired")
        }
        val authorizationRequest =
            authorizationRequestRepository.findById(codeRecord.authorizationRequestId).orElse(null)
                ?: throw OAuth2InvalidGrantException("Authorization request not found")
        if (authorizationRequest.status != OAuth2AuthorizationRequestStatus.APPROVED) {
            throw OAuth2InvalidGrantException("Authorization request was not approved")
        }
        if (authorizationRequest.clientId != clientId || authorizationRequest.redirectUri != redirectUri) {
            throw OAuth2InvalidGrantException("Authorization code is not valid for this client")
        }
        val expectedChallenge = tokenHashing.pkceS256(codeVerifier)
        if (!tokenHashing.constantTimeEquals(expectedChallenge, authorizationRequest.codeChallenge)) {
            throw OAuth2InvalidGrantException("Invalid code_verifier")
        }
        val userId =
            authorizationRequest.userId ?: throw OAuth2InvalidGrantException(
                "Authorization request has no user",
            )
        val user =
            userRepository.findById(userId).orElse(null)
                ?: throw OAuth2InvalidGrantException("User not found")
        val grantedScopes =
            authorizationRequest.grantedScopes
                ?.split(" ")
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        val accessToken =
            jwtTokenIssuer.generateOAuthAccessToken(
                userId = userId,
                email = user.email,
                clientId = clientId,
                scopes = grantedScopes,
                resource = authorizationRequest.resource,
                currentAccountId =
                    authorizationRequest.accountId
                        ?: authContextFactory.build(user).currentAccountId,
            )
        val (refreshToken, familyId) =
            refreshTokenService.createOAuthRefreshToken(
                userId = userId,
                clientId = clientId,
                grantedScopes = grantedScopes,
                resource = authorizationRequest.resource,
            )
        authorizationCodeRepository.markIssuedTokens(
            codeHash = codeHash,
            accessTokenJti = jwtTokenValidator.getJwtIdFromToken(accessToken),
            refreshTokenFamilyId = familyId,
            updatedAt = Instant.now(),
        )
        clientRegistry.markUsed(clientId)
        return OAuth2TokenResponse(
            access_token = accessToken,
            expires_in = jwtConfig.accessTokenExpirationSeconds,
            refresh_token = refreshToken,
            scope = grantedScopes.joinToString(" "),
        )
    }

    private fun refresh(request: OAuth2TokenRequest): OAuth2TokenResponse {
        val refreshToken =
            request.refresh_token?.takeIf { it.isNotBlank() }
                ?: throw OAuth2InvalidRequestException("refresh_token is required")
        val clientId = refreshTokenService.findOAuthClientId(refreshToken)
        if (clientId == null) {
            throw OAuth2InvalidGrantException("Refresh token is not valid for OAuth2")
        }
        val tokenPair = refreshTokenService.refreshAccessToken(refreshToken)
        return OAuth2TokenResponse(
            access_token = tokenPair.accessToken,
            expires_in = jwtConfig.accessTokenExpirationSeconds,
            refresh_token = tokenPair.refreshToken,
            scope = tokenPair.scope,
        )
    }

    private fun resolveRateLimitClientId(request: OAuth2TokenRequest): String? =
        when (request.grant_type) {
            "authorization_code" -> request.client_id?.takeIf { it.isNotBlank() }
            "refresh_token" -> request.refresh_token?.takeIf { it.isNotBlank() }?.let(refreshTokenService::findOAuthClientId)
            else -> null
        }
}
