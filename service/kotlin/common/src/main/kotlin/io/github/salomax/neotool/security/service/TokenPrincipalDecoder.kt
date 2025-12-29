package io.github.salomax.neotool.security.service

import io.github.salomax.neotool.security.service.exception.AuthenticationRequiredException

/**
 * Contract for decoding a token into a [RequestPrincipal].
 *
 * Implementations own the token validation logic (e.g., JWT signature and claims checks).
 * This lives in :common so other modules depend only on the interface, not on a concrete JWT implementation.
 */
fun interface TokenPrincipalDecoder {
    @Throws(AuthenticationRequiredException::class)
    fun fromToken(token: String): RequestPrincipal
}
