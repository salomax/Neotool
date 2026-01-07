package io.github.salomax.neotool.security.http

import io.github.salomax.neotool.common.security.config.JwtConfig
import io.github.salomax.neotool.security.http.dto.TokenRequest
import io.github.salomax.neotool.security.http.dto.TokenResponse
import io.github.salomax.neotool.security.service.jwt.JwtTokenIssuer
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
 * Controller for OAuth2 token endpoint (client credentials flow).
 * Allows services to obtain JWT tokens using their client credentials.
 *
 * Endpoint: POST /oauth/token
 *
 * @see https://tools.ietf.org/html/rfc6749#section-4.4
 */
@Singleton
@Controller("/oauth")
class OAuthTokenController(
    private val servicePrincipalService: ServicePrincipalService,
    private val jwtTokenIssuer: JwtTokenIssuer,
    private val jwtConfig: JwtConfig,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * OAuth2 token endpoint (client credentials flow).
     *
     * Validates client credentials and issues a JWT service token.
     *
     * @param request Token request with client credentials
     * @return Token response with access token
     */
    @Post("/token")
    fun getToken(
        @Valid @Body request: TokenRequest,
    ): HttpResponse<*> {
        // Validate grant type
        if (request.grant_type != "client_credentials") {
            logger.warn { "Invalid grant type: ${request.grant_type}" }
            return HttpResponse.status<JsonError>(HttpStatus.BAD_REQUEST)
                .body(JsonError("Unsupported grant type: ${request.grant_type}"))
        }

        return try {
            // Validate credentials
            val principal =
                servicePrincipalService.validateServiceCredentials(
                    serviceId = request.client_id,
                    clientSecret = request.client_secret,
                )

            if (principal == null) {
                logger.warn { "Invalid credentials for service: ${request.client_id}" }
                return HttpResponse.status<JsonError>(HttpStatus.UNAUTHORIZED)
                    .body(JsonError("Invalid client credentials"))
            }

            val principalId = principal.id ?: throw IllegalStateException("Principal ID is null")

            // Check if principal is disabled
            if (!principal.enabled) {
                logger.warn { "Service principal is disabled: ${request.client_id}" }
                return HttpResponse.status<JsonError>(HttpStatus.FORBIDDEN)
                    .body(JsonError("Service principal is disabled"))
            }

            // Load permissions
            val permissions = servicePrincipalService.getServicePermissions(principalId)

            // Determine audience (default to requesting service if not specified)
            val audience = request.audience ?: request.client_id

            // Generate service token
            // Note: JWT token issuer expects UUID for serviceId, but we store serviceId as String in external_id
            // For service tokens, we use the principal ID as the service identifier in the token
            val accessToken =
                jwtTokenIssuer.generateServiceToken(
                    serviceId = principalId,
                    targetAudience = audience,
                    permissions = permissions,
                )

            val response =
                TokenResponse(
                    access_token = accessToken,
                    token_type = "Bearer",
                    expires_in = jwtConfig.accessTokenExpirationSeconds,
                )

            logger.debug { "Token issued for service: ${request.client_id}" }
            HttpResponse.ok(response)
        } catch (e: IllegalArgumentException) {
            logger.warn(e) { "Invalid token request: ${e.message}" }
            HttpResponse.status<JsonError>(HttpStatus.BAD_REQUEST)
                .body(JsonError(e.message ?: "Invalid request"))
        } catch (e: IllegalStateException) {
            logger.error(e) { "Error processing token request: ${e.message}" }
            HttpResponse.status<JsonError>(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(JsonError("Internal server error"))
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error in token endpoint: ${e.message}" }
            HttpResponse.status<JsonError>(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(JsonError("Internal server error"))
        }
    }
}
