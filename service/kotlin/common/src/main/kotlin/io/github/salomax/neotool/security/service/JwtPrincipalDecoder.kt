package io.github.salomax.neotool.security.service

import io.github.salomax.neotool.security.service.exception.AuthenticationRequiredException
import jakarta.inject.Singleton

/**
 * JWT-based implementation of [TokenPrincipalDecoder].
 */
@Singleton
class JwtPrincipalDecoder(
    private val jwtTokenValidator: JwtTokenValidator,
) : TokenPrincipalDecoder {

    override fun fromToken(token: String): RequestPrincipal {
        // Service token handling
        if (jwtTokenValidator.isServiceToken(token)) {
            val serviceId =
                jwtTokenValidator.getServiceIdFromToken(token)
                    ?: throw AuthenticationRequiredException("Invalid service token: missing service ID")

            val servicePermissions = jwtTokenValidator.getPermissionsFromToken(token) ?: emptyList()
            val userId = jwtTokenValidator.getUserIdFromServiceToken(token)
            val userPermissions = jwtTokenValidator.getUserPermissionsFromServiceToken(token)

            return RequestPrincipal(
                principalType = PrincipalType.SERVICE,
                userId = userId,
                serviceId = serviceId,
                token = token,
                permissionsFromToken = servicePermissions,
                userPermissions = userPermissions,
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

        return RequestPrincipal(
            principalType = PrincipalType.USER,
            userId = userId,
            serviceId = null,
            token = token,
            permissionsFromToken = permissions,
            userPermissions = null,
        )
    }
}
