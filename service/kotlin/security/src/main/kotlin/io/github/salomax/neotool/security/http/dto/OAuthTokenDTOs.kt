package io.github.salomax.neotool.security.http.dto

import io.micronaut.serde.annotation.Serdeable
import jakarta.validation.constraints.NotBlank

/**
 * Request DTO for OAuth2 token endpoint (client credentials flow).
 */
@Serdeable
data class TokenRequest(
    @field:NotBlank(message = "Grant type is required")
    val grant_type: String,
    @field:NotBlank(message = "Client ID is required")
    val client_id: String,
    @field:NotBlank(message = "Client secret is required")
    val client_secret: String,
    val audience: String? = null,
)

/**
 * Response DTO for OAuth2 token endpoint.
 */
@Serdeable
data class TokenResponse(
    val access_token: String,
    val token_type: String = "Bearer",
    val expires_in: Long,
)

