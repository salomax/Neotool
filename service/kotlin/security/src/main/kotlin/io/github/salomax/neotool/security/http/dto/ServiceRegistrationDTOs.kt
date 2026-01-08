package io.github.salomax.neotool.security.http.dto

import io.micronaut.serde.annotation.Serdeable
import jakarta.validation.constraints.NotBlank

/**
 * Request DTO for service registration.
 * Client secret is auto-generated server-side for security.
 */
@Serdeable
data class ServiceRegistrationRequest(
    @field:NotBlank(message = "Service ID is required")
    val serviceId: String,
    val permissions: List<String> = emptyList(),
)

/**
 * Response DTO for service registration.
 * Returns the one-time clear client secret (only at creation time).
 */
@Serdeable
data class ServiceRegistrationResponse(
    val serviceId: String,
    val principalId: String,
    // One-time clear secret
    val clientSecret: String,
    val permissions: List<String>,
)
