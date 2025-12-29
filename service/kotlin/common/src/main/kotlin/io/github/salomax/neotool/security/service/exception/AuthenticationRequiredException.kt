package io.github.salomax.neotool.security.service.exception

/**
 * Exception thrown when authentication is required but not provided or invalid.
 */
class AuthenticationRequiredException(message: String) : RuntimeException(message)
