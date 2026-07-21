package io.github.salomax.neotool.common.security.principal

import io.github.salomax.neotool.common.security.exception.AuthenticationRequiredException
import io.github.salomax.neotool.common.security.jwt.JwtTokenValidator
import jakarta.inject.Singleton

/**
 * JWT-based implementation of [TokenPrincipalDecoder].
 */
@Singleton
class JwtPrincipalDecoder(
    private val jwtTokenValidator: JwtTokenValidator,
) : TokenPrincipalDecoder {
    override fun fromToken(token: String): RequestPrincipal {
        try {
            // Service token handling
            if (jwtTokenValidator.isServiceToken(token)) {
                val serviceId =
                    jwtTokenValidator.getServiceIdFromToken(token)
                        ?: throw AuthenticationRequiredException("Invalid service token: missing service ID")

                val servicePermissions = jwtTokenValidator.getPermissionsFromToken(token) ?: emptyList()
                val userId = jwtTokenValidator.getUserIdFromServiceToken(token)
                val userPermissions = jwtTokenValidator.getUserPermissionsFromServiceToken(token)
                val delegatedBy = jwtTokenValidator.getDelegatedByFromServiceToken(token)
                val currentAccountId = jwtTokenValidator.getCurrentAccountIdFromToken(token)
                val tokenId =
                    jwtTokenValidator.getJwtIdFromToken(token)
                        ?: throw AuthenticationRequiredException("Invalid service token: missing token ID")

                return RequestPrincipal(
                    principalType = PrincipalType.SERVICE,
                    userId = userId,
                    serviceId = serviceId,
                    token = token,
                    tokenId = tokenId,
                    permissionsFromToken = servicePermissions,
                    userPermissions = userPermissions,
                    delegatedBy = delegatedBy,
                    currentAccountId = currentAccountId,
                )
            }

            // Access token handling
            if (!jwtTokenValidator.isAccessToken(token)) {
                throw AuthenticationRequiredException("Invalid or expired access token")
            }

            val userId =
                jwtTokenValidator.getUserIdFromToken(token)
                    ?: throw AuthenticationRequiredException("Invalid token: missing user ID")

            val permissions = jwtTokenValidator.getPermissionsFromToken(token) ?: emptyList()
            val currentAccountId = jwtTokenValidator.getCurrentAccountIdFromToken(token)
            val accounts = jwtTokenValidator.getAccountsFromToken(token)
            val sessionVersion = jwtTokenValidator.getSessionVersionFromToken(token)
            val tokenId =
                jwtTokenValidator.getJwtIdFromToken(token)
                    ?: throw AuthenticationRequiredException("Invalid token: missing token ID")

            return RequestPrincipal(
                principalType = PrincipalType.USER,
                userId = userId,
                serviceId = null,
                token = token,
                tokenId = tokenId,
                permissionsFromToken = permissions,
                userPermissions = null,
                currentAccountId = currentAccountId,
                accounts = accounts,
                sessionVersion = sessionVersion,
            )
        } catch (e: AuthenticationRequiredException) {
            throw e
        } catch (e: Exception) {
            throw AuthenticationRequiredException("Token validation failed: ${e.message}")
        }
    }
}
