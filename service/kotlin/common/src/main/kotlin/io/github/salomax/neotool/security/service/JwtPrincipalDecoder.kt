package io.github.salomax.neotool.security.service

import io.github.salomax.neotool.security.service.exception.AuthenticationRequiredException
import jakarta.inject.Singleton

/**
 * JWT-based implementation of [TokenPrincipalDecoder].
 */
@Singleton
class JwtPrincipalDecoder(
    private val jwtService: JwtService,
) : TokenPrincipalDecoder {

    override fun fromToken(token: String): RequestPrincipal {
        // Service token handling
        if (jwtService.isServiceToken(token)) {
            val serviceId =
                jwtService.getServiceIdFromToken(token)
                    ?: throw AuthenticationRequiredException("Invalid service token: missing service ID")

            val servicePermissions = jwtService.getPermissionsFromToken(token) ?: emptyList()
            val userId = jwtService.getUserIdFromServiceToken(token)
            val userPermissions = jwtService.getUserPermissionsFromServiceToken(token)

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
        if (!jwtService.isAccessToken(token)) {
            throw AuthenticationRequiredException("Invalid or expired access token")
        }

        val userId =
            jwtService.getUserIdFromToken(token)
                ?: throw AuthenticationRequiredException("Invalid token: missing user ID")

        val permissions = jwtService.getPermissionsFromToken(token) ?: emptyList()

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
