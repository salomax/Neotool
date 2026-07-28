package io.github.salomax.neotool.common.security.exception

/**
 * Exception thrown when authentication is required but not provided or invalid.
 */
enum class AuthenticationFailureCause {
    TOKEN_EXPIRED,
    TOKEN_INVALID_SIGNATURE,
    TOKEN_INVALID_ISSUER,
    TOKEN_INVALID,
}

class AuthenticationRequiredException(
    message: String,
    val failureCause: AuthenticationFailureCause? = null,
) : RuntimeException(message)
