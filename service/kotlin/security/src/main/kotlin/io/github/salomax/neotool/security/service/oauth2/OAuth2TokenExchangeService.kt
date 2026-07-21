package io.github.salomax.neotool.security.service.oauth2

import io.github.salomax.neotool.common.security.config.JwtConfig
import io.github.salomax.neotool.common.security.exception.AuthenticationRequiredException
import io.github.salomax.neotool.common.security.jwt.JwtTokenValidator
import io.github.salomax.neotool.security.http.dto.OAuth2TokenRequest
import io.github.salomax.neotool.security.http.dto.OAuth2TokenResponse
import io.github.salomax.neotool.security.service.authorization.AuthorizationService
import io.github.salomax.neotool.security.service.jwt.JwtTokenIssuer
import io.github.salomax.neotool.security.service.management.ServicePrincipalService
import io.jsonwebtoken.Claims
import jakarta.inject.Singleton
import java.time.Instant
import java.util.UUID

@Singleton
open class OAuth2TokenExchangeService(
    private val servicePrincipalService: ServicePrincipalService,
    private val jwtTokenValidator: JwtTokenValidator,
    private val jwtTokenIssuer: JwtTokenIssuer,
    private val authorizationService: AuthorizationService,
    private val scopePermissionMapper: OAuth2ScopePermissionMapper,
    private val revocationChecker: OAuth2AccessTokenRevocationChecker,
    private val rateLimiter: OAuth2TokenExchangeRateLimiter,
    private val resourceResolver: OAuth2TokenExchangeResourceResolver,
    private val jwtConfig: JwtConfig,
) {
    open fun exchange(request: OAuth2TokenRequest): OAuth2TokenResponse {
        val clientId =
            request.client_id?.takeIf { it.isNotBlank() }
                ?: throw OAuth2InvalidRequestException("client_id is required")
        val clientSecret =
            request.client_secret?.takeIf { it.isNotBlank() }
                ?: throw OAuth2InvalidRequestException("client_secret is required")
        val targetAudience =
            request.audience?.takeIf { it.isNotBlank() }
                ?: throw OAuth2InvalidRequestException("audience is required")
        val subjectToken =
            request.subject_token?.takeIf { it.isNotBlank() }
                ?: throw OAuth2InvalidRequestException("subject_token is required")
        val subjectTokenType =
            request.subject_token_type?.takeIf { it.isNotBlank() }
                ?: throw OAuth2InvalidRequestException("subject_token_type is required")
        if (subjectTokenType != ACCESS_TOKEN_TYPE) {
            throw OAuth2InvalidRequestException("Unsupported subject_token_type")
        }
        val requestedTokenType = request.requested_token_type?.takeIf { it.isNotBlank() }
        if (requestedTokenType != null && requestedTokenType != ACCESS_TOKEN_TYPE) {
            throw OAuth2InvalidRequestException("Unsupported requested_token_type")
        }

        rateLimiter.check(clientId)
        val servicePrincipal =
            servicePrincipalService.validateServiceCredentials(clientId, clientSecret)
                ?: throw OAuth2InvalidGrantException("Invalid client credentials")
        val servicePrincipalId = servicePrincipal.id ?: throw OAuth2InvalidGrantException("Invalid client credentials")
        val subjectAudience =
            resourceResolver.resolveServiceResourceIdentifier(clientId)
                ?: throw OAuth2InvalidGrantException("No resource identifier configured for client")

        val claims = validateSubjectToken(subjectToken, expectedAudience = subjectAudience)
        val tokenId = claims.id ?: throw OAuth2InvalidGrantException("subject_token is missing jti")
        if (revocationChecker.isRevoked(tokenId)) {
            throw OAuth2InvalidGrantException("subject_token is revoked")
        }

        val userId = parseUuid(claims.subject, "subject")
        val currentAccountId =
            claims["current_account"]?.toString()?.let { parseUuid(it, "current_account") }
                ?: throw OAuth2InvalidGrantException("subject_token is missing current_account")
        val grantedScopes = parseScope(claims["scope"]?.toString())
        val mappedPermissions = scopePermissionMapper.mapScopes(grantedScopes)
        val actualPermissions = authorizationService.getUserPermissions(userId).map { it.name }.toSet()
        val delegatedUserPermissions = mappedPermissions.intersect(actualPermissions).sorted()
        val effectiveScopes =
            grantedScopes.filter { scope ->
                scopePermissionMapper.mapScopes(listOf(scope)).any { it in delegatedUserPermissions }
            }
        val servicePermissions = servicePrincipalService.getServicePermissions(servicePrincipalId)
        val subjectExpiresAt =
            claims.expiration?.toInstant()
                ?: throw OAuth2InvalidGrantException("subject_token is missing expiration")
        val now = Instant.now()
        if (!subjectExpiresAt.isAfter(now)) {
            throw OAuth2InvalidGrantException("subject_token is expired")
        }
        val issuerExpiresAt = now.plusSeconds(jwtConfig.accessTokenExpirationSeconds)
        val delegatedExpiresAt = minOf(subjectExpiresAt, issuerExpiresAt)
        val expiresIn = delegatedExpiresAt.epochSecond - now.epochSecond

        val accessToken =
            jwtTokenIssuer.generateServiceTokenWithUserContext(
                serviceId = servicePrincipalId,
                targetAudience = targetAudience,
                permissions = servicePermissions,
                userId = userId,
                userPermissions = delegatedUserPermissions,
                currentAccountId = currentAccountId,
                expiresAt = delegatedExpiresAt,
            )

        return OAuth2TokenResponse(
            access_token = accessToken,
            expires_in = expiresIn.coerceAtLeast(0),
            scope = effectiveScopes.joinToString(" "),
        )
    }

    private fun validateSubjectToken(
        token: String,
        expectedAudience: String,
    ): Claims =
        try {
            val claims = jwtTokenValidator.validateToken(token)
            if (claims["type"] != "access") {
                throw OAuth2InvalidGrantException("subject_token must be an access token")
            }
            if (!claims.audience.orEmpty().contains(expectedAudience)) {
                throw OAuth2InvalidGrantException("subject_token audience is invalid")
            }
            claims
        } catch (e: OAuth2InvalidGrantException) {
            throw e
        } catch (e: AuthenticationRequiredException) {
            throw OAuth2InvalidGrantException(e.message ?: "Invalid subject token")
        }

    private fun parseScope(scope: String?): List<String> =
        scope
            ?.split(" ")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?: emptyList()

    private fun parseUuid(
        value: String,
        field: String,
    ): UUID =
        try {
            UUID.fromString(value)
        } catch (e: IllegalArgumentException) {
            throw OAuth2InvalidGrantException("subject_token has invalid $field")
        }

    companion object {
        const val TOKEN_EXCHANGE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:token-exchange"
        const val ACCESS_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token"
    }
}
