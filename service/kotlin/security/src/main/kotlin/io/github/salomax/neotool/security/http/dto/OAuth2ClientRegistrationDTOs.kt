package io.github.salomax.neotool.security.http.dto

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class OAuth2ClientRegistrationRequest(
    val client_name: String? = null,
    val redirect_uris: List<String>? = null,
    val scope: String? = null,
    val resources: List<String>? = null,
    val grant_types: List<String>? = null,
    val response_types: List<String>? = null,
    val token_endpoint_auth_method: String? = null,
    val logo_uri: String? = null,
    val client_uri: String? = null,
)

@Serdeable
data class OAuth2ClientRegistrationResponse(
    val client_id: String,
    val client_id_issued_at: Long,
    val client_name: String? = null,
    val redirect_uris: List<String>,
    val scope: String,
    val resources: List<String>,
    val grant_types: List<String>,
    val response_types: List<String>,
    val token_endpoint_auth_method: String,
    val logo_uri: String? = null,
    val client_uri: String? = null,
)
