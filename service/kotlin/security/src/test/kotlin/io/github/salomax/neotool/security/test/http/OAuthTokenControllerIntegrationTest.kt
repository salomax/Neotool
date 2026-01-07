package io.github.salomax.neotool.security.test.http

import io.github.salomax.neotool.common.security.config.JwtConfig
import io.github.salomax.neotool.common.test.http.exchangeAsString
import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.common.test.transaction.runTransaction
import io.github.salomax.neotool.security.http.dto.TokenResponse
import io.github.salomax.neotool.security.model.PermissionEntity
import io.github.salomax.neotool.security.model.PrincipalEntity
import io.github.salomax.neotool.security.model.PrincipalPermissionEntity
import io.github.salomax.neotool.security.model.ServiceCredentialEntity
import io.github.salomax.neotool.security.repo.PermissionRepository
import io.github.salomax.neotool.security.repo.PrincipalPermissionRepository
import io.github.salomax.neotool.security.repo.PrincipalRepository
import io.github.salomax.neotool.security.repo.ServiceCredentialRepository
import io.github.salomax.neotool.security.service.authentication.AuthenticationService
import io.github.salomax.neotool.security.service.management.ServicePrincipalService
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("OAuth Token Controller Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("security")
@Tag("http")
open class OAuthTokenControllerIntegrationTest :
    BaseIntegrationTest(),
    PostgresIntegrationTest {
    @Inject
    lateinit var servicePrincipalService: ServicePrincipalService

    @Inject
    lateinit var authenticationService: AuthenticationService

    @Inject
    lateinit var principalRepository: PrincipalRepository

    @Inject
    lateinit var serviceCredentialRepository: ServiceCredentialRepository

    @Inject
    lateinit var permissionRepository: PermissionRepository

    @Inject
    lateinit var principalPermissionRepository: PrincipalPermissionRepository

    @Inject
    lateinit var jwtConfig: JwtConfig

    @Inject
    lateinit var entityManager: EntityManager

    private val registeredServices = mutableListOf<String>()

    @AfterEach
    fun cleanup() {
        entityManager.runTransaction {
            registeredServices.forEach { serviceId ->
                principalRepository
                    .findByPrincipalTypeAndExternalId(
                        io.github.salomax.neotool.common.security.principal.PrincipalType.SERVICE,
                        serviceId,
                    )
                    .ifPresent { principal ->
                        principal.id?.let { principalId ->
                            principalPermissionRepository
                                .findByPrincipalId(principalId)
                                .forEach { principalPermissionRepository.delete(it) }
                            serviceCredentialRepository.findById(principalId)
                                .ifPresent { serviceCredentialRepository.delete(it) }
                        }
                        principalRepository.delete(principal)
                    }
            }
            registeredServices.clear()
        }
    }

    private fun createTestService(
        serviceId: String,
        clientSecret: String,
        permissions: List<String> = emptyList(),
        enabled: Boolean = true,
    ): UUID {
        return entityManager.runTransaction {
            // Create permission entities if needed
            val permissionEntities =
                permissions.map { permissionName ->
                    permissionRepository.findByName(permissionName).orElseGet {
                        permissionRepository.save(
                            PermissionEntity(
                                id = null,
                                name = permissionName,
                            ),
                        )
                    }
                }

            // Create principal
            val principal =
                PrincipalEntity(
                    id = null,
                    principalType = io.github.salomax.neotool.common.security.principal.PrincipalType.SERVICE,
                    externalId = serviceId,
                    enabled = enabled,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                    version = 0,
                )
            val savedPrincipal = principalRepository.save(principal)
            val principalId = savedPrincipal.id ?: throw IllegalStateException("Principal ID is null")

            // Create credential
            val credentialHash = authenticationService.hashPassword(clientSecret)
            val credential =
                ServiceCredentialEntity(
                    id = principalId,
                    credentialHash = credentialHash,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            serviceCredentialRepository.save(credential)

            // Assign permissions
            permissionEntities.forEach { permission ->
                val principalPermission =
                    PrincipalPermissionEntity(
                        principalId = principalId,
                        permissionId = permission.id!!,
                        resourcePattern = null,
                    )
                principalPermissionRepository.save(principalPermission)
            }

            registeredServices.add(serviceId)
            principalId
        }
    }

    @Nested
    @DisplayName("POST /oauth/token - Success Cases")
    inner class SuccessCasesTests {
        @Test
        fun `should return token for valid credentials`() {
            // Arrange
            val serviceId = "test-service-${UUID.randomUUID()}"
            val clientSecret = "test-secret-123"
            createTestService(serviceId, clientSecret)

            val requestBody =
                mapOf(
                    "grant_type" to "client_credentials",
                    "client_id" to serviceId,
                    "client_secret" to clientSecret,
                )

            val request =
                HttpRequest
                    .POST("/oauth/token", requestBody)
                    .contentType(MediaType.APPLICATION_JSON)

            // Act
            val response = httpClient.exchangeAsString(request)

            // Assert
            assertThat(response.status()).isEqualTo(HttpStatus.OK)
            val body = response.body()
            assertThat(body).isNotNull()

            val tokenResponse: TokenResponse = json.readValue(body, TokenResponse::class.java)
            assertThat(tokenResponse.access_token).isNotBlank()
            assertThat(tokenResponse.token_type).isEqualTo("Bearer")
            assertThat(tokenResponse.expires_in).isEqualTo(jwtConfig.accessTokenExpirationSeconds)
        }

        @Test
        fun `should return token with custom audience`() {
            // Arrange
            val serviceId = "test-service-${UUID.randomUUID()}"
            val clientSecret = "test-secret-123"
            val audience = "target-service"
            createTestService(serviceId, clientSecret)

            val requestBody =
                mapOf(
                    "grant_type" to "client_credentials",
                    "client_id" to serviceId,
                    "client_secret" to clientSecret,
                    "audience" to audience,
                )

            val request =
                HttpRequest
                    .POST("/oauth/token", requestBody)
                    .contentType(MediaType.APPLICATION_JSON)

            // Act
            val response = httpClient.exchangeAsString(request)

            // Assert
            assertThat(response.status()).isEqualTo(HttpStatus.OK)
            val body = response.body()
            val tokenResponse: TokenResponse = json.readValue(body, TokenResponse::class.java)
            assertThat(tokenResponse.access_token).isNotBlank()
        }

        @Test
        fun `should return token with service permissions`() {
            // Arrange
            val serviceId = "test-service-${UUID.randomUUID()}"
            val clientSecret = "test-secret-123"
            val permissions = listOf("assets:upload", "assets:read")
            createTestService(serviceId, clientSecret, permissions)

            val requestBody =
                mapOf(
                    "grant_type" to "client_credentials",
                    "client_id" to serviceId,
                    "client_secret" to clientSecret,
                )

            val request =
                HttpRequest
                    .POST("/oauth/token", requestBody)
                    .contentType(MediaType.APPLICATION_JSON)

            // Act
            val response = httpClient.exchangeAsString(request)

            // Assert
            assertThat(response.status()).isEqualTo(HttpStatus.OK)
            val body = response.body()
            val tokenResponse: TokenResponse = json.readValue(body, TokenResponse::class.java)
            assertThat(tokenResponse.access_token).isNotBlank()
        }
    }

    @Nested
    @DisplayName("POST /oauth/token - Error Cases")
    inner class ErrorCasesTests {
        @Test
        fun `should return 400 for invalid grant type`() {
            // Arrange
            val requestBody =
                mapOf(
                    "grant_type" to "authorization_code",
                    "client_id" to "test-service",
                    "client_secret" to "secret",
                )

            val request =
                HttpRequest
                    .POST("/oauth/token", requestBody)
                    .contentType(MediaType.APPLICATION_JSON)

            // Act & Assert
            val exception =
                assertThrows<HttpClientResponseException> {
                    httpClient.exchangeAsString(request)
                }

            assertThat(exception.status).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @Test
        fun `should return 401 for invalid credentials`() {
            // Arrange
            val serviceId = "test-service-${UUID.randomUUID()}"
            val clientSecret = "correct-secret"
            createTestService(serviceId, clientSecret)

            val requestBody =
                mapOf(
                    "grant_type" to "client_credentials",
                    "client_id" to serviceId,
                    "client_secret" to "wrong-secret",
                )

            val request =
                HttpRequest
                    .POST("/oauth/token", requestBody)
                    .contentType(MediaType.APPLICATION_JSON)

            // Act & Assert
            val exception =
                assertThrows<HttpClientResponseException> {
                    httpClient.exchangeAsString(request)
                }

            assertThat(exception.status).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @Test
        fun `should return 401 for nonexistent service`() {
            // Arrange
            val requestBody =
                mapOf(
                    "grant_type" to "client_credentials",
                    "client_id" to "nonexistent-service",
                    "client_secret" to "secret",
                )

            val request =
                HttpRequest
                    .POST("/oauth/token", requestBody)
                    .contentType(MediaType.APPLICATION_JSON)

            // Act & Assert
            val exception =
                assertThrows<HttpClientResponseException> {
                    httpClient.exchangeAsString(request)
                }

            assertThat(exception.status).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @Test
        fun `should return 401 for disabled service principal`() {
            // Arrange
            val serviceId = "test-service-${UUID.randomUUID()}"
            val clientSecret = "test-secret-123"
            createTestService(serviceId, clientSecret, enabled = false)

            val requestBody =
                mapOf(
                    "grant_type" to "client_credentials",
                    "client_id" to serviceId,
                    "client_secret" to clientSecret,
                )

            val request =
                HttpRequest
                    .POST("/oauth/token", requestBody)
                    .contentType(MediaType.APPLICATION_JSON)

            // Act & Assert
            // Note: validateServiceCredentials returns null for disabled principals,
            // so the controller returns 401 (UNAUTHORIZED) rather than 403 (FORBIDDEN)
            val exception =
                assertThrows<HttpClientResponseException> {
                    httpClient.exchangeAsString(request)
                }

            assertThat(exception.status).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @Test
        fun `should return 400 for missing required fields`() {
            // Arrange
            val requestBody = mapOf("grant_type" to "client_credentials")

            val request =
                HttpRequest
                    .POST("/oauth/token", requestBody)
                    .contentType(MediaType.APPLICATION_JSON)

            // Act & Assert
            val exception =
                assertThrows<HttpClientResponseException> {
                    httpClient.exchangeAsString(request)
                }

            assertThat(exception.status).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }
}
