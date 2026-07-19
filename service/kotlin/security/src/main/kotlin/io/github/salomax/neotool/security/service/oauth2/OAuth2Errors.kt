package io.github.salomax.neotool.security.service.oauth2

class OAuth2InvalidRequestException(
    message: String,
    val error: String = "invalid_request",
) : RuntimeException(message)

class OAuth2InvalidGrantException(
    message: String,
    val error: String = "invalid_grant",
) : RuntimeException(message)
