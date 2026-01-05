package io.github.salomax.neotool.common.security.exception

/**
 * Exception thrown when authorization is denied.
 * This exception should be thrown when a user attempts to perform an action
 * for which they do not have the required permissions.
 */
class AuthorizationDeniedException(
    message: String,
) : RuntimeException(message)
