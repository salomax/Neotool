package io.github.salomax.neotool.security.http.dto

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class OAuth2TokenRequest(
    val grant_type: String,
    val code: String? = null,
    val redirect_uri: String? = null,
    val client_id: String? = null,
    val client_secret: String? = null,
    val code_verifier: String? = null,
    val refresh_token: String? = null,
    val audience: String? = null,
    val delegated_by: String? = null,
    val subject_token: String? = null,
    val subject_token_type: String? = null,
    val requested_token_type: String? = null,
)

@Serdeable
data class OAuth2TokenResponse(
    val access_token: String,
    val token_type: String = "Bearer",
    val expires_in: Long,
    val refresh_token: String? = null,
    val scope: String? = null,
)

@Serdeable
data class OAuth2ErrorResponse(
    val error: String,
    val error_description: String,
)
