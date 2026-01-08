package io.github.salomax.neotool.security.http

import io.github.salomax.neotool.security.http.dto.ServiceRegistrationRequest
import io.github.salomax.neotool.security.http.dto.ServiceRegistrationResponse
import io.github.salomax.neotool.security.service.management.ServicePrincipalService
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.hateoas.JsonError
import jakarta.inject.Singleton
import jakarta.validation.Valid
import mu.KotlinLogging

/**
 * Controller for service registration.
 * Allows administrators to register new services with credentials and permissions.
 *
 * Endpoint: POST /api/internal/services/register
 */
@Singleton
@Controller("/api/internal/services")
class ServiceRegistrationController(
    private val servicePrincipalService: ServicePrincipalService,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Register a new service principal.
     *
     * Creates a service principal with auto-generated credentials and assigned permissions.
     * Returns the one-time clear client secret (only at creation time).
     *
     * Security: This endpoint is intentionally unauthenticated for bootstrap scenarios.
     * Access is restricted via K8s NetworkPolicy to internal services only.
     * See infrastructure documentation for NetworkPolicy configuration.
     *
     * @param request Service registration request
     * @return Service registration response with one-time clear secret
     */
    @Post("/register")
    fun registerService(
        @Valid @Body request: ServiceRegistrationRequest,
    ): HttpResponse<*> {
        return try {
            logger.info { "Registering service: ${request.serviceId}" }

            val result =
                servicePrincipalService.registerService(
                    serviceId = request.serviceId,
                    permissions = request.permissions,
                )

            // One-time clear secret
            val response =
                ServiceRegistrationResponse(
                    serviceId = result.serviceId,
                    principalId = result.principalId.toString(),
                    clientSecret = result.clientSecret,
                    permissions = result.permissions,
                )

            logger.info { "Service registered successfully: ${request.serviceId}" }
            HttpResponse.status<ServiceRegistrationResponse>(HttpStatus.CREATED).body(response)
        } catch (e: IllegalArgumentException) {
            logger.warn(e) { "Invalid service registration request: ${e.message}" }
            HttpResponse.status<JsonError>(HttpStatus.BAD_REQUEST)
                .body(JsonError(e.message ?: "Invalid request"))
        } catch (e: Exception) {
            logger.error(e) { "Error registering service: ${e.message}" }
            HttpResponse.status<JsonError>(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(JsonError("Internal server error"))
        }
    }
}
