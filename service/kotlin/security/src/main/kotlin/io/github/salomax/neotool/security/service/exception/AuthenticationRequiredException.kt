package io.github.salomax.neotool.security.service.exception

/**
 * Exception thrown when authentication is required but not provided.
 * This exception should be thrown when a request requires authentication
 * but no valid token is present.
 */
class AuthenticationRequiredException(message: String) : RuntimeException(message)
