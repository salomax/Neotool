package io.github.salomax.neotool.security.test.http

import io.github.salomax.neotool.common.test.http.exchangeAsString
import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.common.test.transaction.runTransaction
import io.github.salomax.neotool.security.http.dto.ServiceRegistrationRequest
import io.github.salomax.neotool.security.http.dto.ServiceRegistrationResponse
import io.github.salomax.neotool.security.model.PermissionEntity
import io.github.salomax.neotool.security.repo.PermissionRepository
import io.github.salomax.neotool.security.repo.PrincipalPermissionRepository
import io.github.salomax.neotool.security.repo.PrincipalRepository
import io.github.salomax.neotool.security.repo.ServiceCredentialRepository
import io.github.salomax.neotool.security.service.authentication.AuthenticationService
import io.github.salomax.neotool.security.service.management.ServicePrincipalService
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
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
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("Service Registration Controller Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("security")
@Tag("http")
open class ServiceRegistrationControllerIntegrationTest :
    BaseIntegrationTest(),
    PostgresIntegrationTest {
    @Inject
    lateinit var servicePrincipalService: ServicePrincipalService

    @Inject
    lateinit var principalRepository: PrincipalRepository

    @Inject
    lateinit var serviceCredentialRepository: ServiceCredentialRepository

    @Inject
    lateinit var permissionRepository: PermissionRepository

    @Inject
    lateinit var principalPermissionRepository: PrincipalPermissionRepository

    @Inject
    lateinit var authenticationService: AuthenticationService

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

    @Nested
    @DisplayName("POST /api/internal/services/register - Success Cases")
    inner class SuccessCasesTests {
        @Test
        fun `should register new service successfully`() {
            // Arrange
            val serviceId = "test-service-${UUID.randomUUID()}"
            val requestBody =
                mapOf(
                    "serviceId" to serviceId,
                    "permissions" to emptyList<String>(),
                )

            val request =
                HttpRequest
                    .POST("/api/internal/services/register", requestBody)
                    .contentType(MediaType.APPLICATION_JSON)

            // Act
            val response = httpClient.exchangeAsString(request)

            // Assert
            assertThat(response.status()).isEqualTo(HttpStatus.CREATED)
            val body = response.body()
            assertThat(body).isNotNull()

            val registrationResponse: ServiceRegistrationResponse =
                json.readValue(body, ServiceRegistrationResponse::class.java)
            assertThat(registrationResponse.serviceId).isEqualTo(serviceId)
            assertThat(registrationResponse.principalId).isNotBlank()
            assertThat(registrationResponse.clientSecret).isNotBlank()
            assertThat(registrationResponse.permissions).isEmpty()

            registeredServices.add(serviceId)
        }

        @Test
        fun `should register service with permissions`() {
            // Arrange
            val serviceId = "test-service-${UUID.randomUUID()}"
            val permissionName1 = "assets:upload-${UUID.randomUUID()}"
            val permissionName2 = "assets:read-${UUID.randomUUID()}"

            // Create permissions first
            entityManager.runTransaction {
                permissionRepository.save(
                    PermissionEntity(
                        id = null,
                        name = permissionName1,
                    ),
                )
                permissionRepository.save(
                    PermissionEntity(
                        id = null,
                        name = permissionName2,
                    ),
                )
            }

            val requestBody =
                mapOf(
                    "serviceId" to serviceId,
                    "permissions" to listOf(permissionName1, permissionName2),
                )

            val request =
                HttpRequest
                    .POST("/api/internal/services/register", requestBody)
                    .contentType(MediaType.APPLICATION_JSON)

            // Act
            val response = httpClient.exchangeAsString(request)

            // Assert
            assertThat(response.status()).isEqualTo(HttpStatus.CREATED)
            val body = response.body()
            val registrationResponse: ServiceRegistrationResponse =
                json.readValue(body, ServiceRegistrationResponse::class.java)
            assertThat(registrationResponse.serviceId).isEqualTo(serviceId)
            assertThat(registrationResponse.clientSecret).isNotBlank()
            assertThat(registrationResponse.permissions).containsExactlyInAnyOrder(
                permissionName1,
                permissionName2,
            )

            registeredServices.add(serviceId)
        }

        @Test
        fun `should return one-time clear secret`() {
            // Arrange
            val serviceId = "test-service-${UUID.randomUUID()}"
            val requestBody =
                mapOf(
                    "serviceId" to serviceId,
                    "permissions" to emptyList<String>(),
                )

            val request =
                HttpRequest
                    .POST("/api/internal/services/register", requestBody)
                    .contentType(MediaType.APPLICATION_JSON)

            // Act
            val response = httpClient.exchangeAsString(request)

            // Assert
            assertThat(response.status()).isEqualTo(HttpStatus.CREATED)
            val body = response.body()
            val registrationResponse: ServiceRegistrationResponse =
                json.readValue(body, ServiceRegistrationResponse::class.java)

            // Verify the secret can be used to authenticate
            val principal =
                servicePrincipalService.validateServiceCredentials(
                    serviceId,
                    registrationResponse.clientSecret,
                )
            assertThat(principal).isNotNull()
            assertThat(principal?.externalId).isEqualTo(serviceId)

            registeredServices.add(serviceId)
        }

        @Test
        fun `should generate unique secrets for different services`() {
            // Arrange
            val serviceId1 = "test-service-1-${UUID.randomUUID()}"
            val serviceId2 = "test-service-2-${UUID.randomUUID()}"

            val requestBody1 =
                mapOf(
                    "serviceId" to serviceId1,
                    "permissions" to emptyList<String>(),
                )
            val requestBody2 =
                mapOf(
                    "serviceId" to serviceId2,
                    "permissions" to emptyList<String>(),
                )

            val request1 =
                HttpRequest
                    .POST("/api/internal/services/register", requestBody1)
                    .contentType(MediaType.APPLICATION_JSON)
            val request2 =
                HttpRequest
                    .POST("/api/internal/services/register", requestBody2)
                    .contentType(MediaType.APPLICATION_JSON)

            // Act
            val response1 = httpClient.exchangeAsString(request1)
            val response2 = httpClient.exchangeAsString(request2)

            // Assert
            assertThat(response1.status()).isEqualTo(HttpStatus.CREATED)
            assertThat(response2.status()).isEqualTo(HttpStatus.CREATED)

            val registrationResponse1: ServiceRegistrationResponse =
                json.readValue(response1.body(), ServiceRegistrationResponse::class.java)
            val registrationResponse2: ServiceRegistrationResponse =
                json.readValue(response2.body(), ServiceRegistrationResponse::class.java)

            assertThat(registrationResponse1.clientSecret).isNotEqualTo(registrationResponse2.clientSecret)

            registeredServices.add(serviceId1)
            registeredServices.add(serviceId2)
        }
    }

    @Nested
    @DisplayName("POST /api/internal/services/register - Error Cases")
    inner class ErrorCasesTests {
        @Test
        fun `should return 400 for duplicate service ID`() {
            // Arrange
            val serviceId = "test-service-${UUID.randomUUID()}"
            val requestBody =
                mapOf(
                    "serviceId" to serviceId,
                    "permissions" to emptyList<String>(),
                )

            val request =
                HttpRequest
                    .POST("/api/internal/services/register", requestBody)
                    .contentType(MediaType.APPLICATION_JSON)

            // Register first time
            val response1 = httpClient.exchangeAsString(request)
            assertThat(response1.status()).isEqualTo(HttpStatus.CREATED)
            registeredServices.add(serviceId)

            // Act & Assert - Try to register again
            val exception =
                assertThrows<io.micronaut.http.client.exceptions.HttpClientResponseException> {
                    httpClient.exchangeAsString(request)
                }

            assertThat(exception.status).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @Test
        fun `should return 400 for nonexistent permission`() {
            // Arrange
            val serviceId = "test-service-${UUID.randomUUID()}"
            val requestBody =
                mapOf(
                    "serviceId" to serviceId,
                    "permissions" to listOf("nonexistent:permission"),
                )

            val request =
                HttpRequest
                    .POST("/api/internal/services/register", requestBody)
                    .contentType(MediaType.APPLICATION_JSON)

            // Act & Assert
            val exception =
                assertThrows<io.micronaut.http.client.exceptions.HttpClientResponseException> {
                    httpClient.exchangeAsString(request)
                }

            assertThat(exception.status).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @Test
        fun `should return 400 for missing service ID`() {
            // Arrange
            val requestBody = mapOf("permissions" to emptyList<String>())

            val request =
                HttpRequest
                    .POST("/api/internal/services/register", requestBody)
                    .contentType(MediaType.APPLICATION_JSON)

            // Act & Assert
            val exception =
                assertThrows<io.micronaut.http.client.exceptions.HttpClientResponseException> {
                    httpClient.exchangeAsString(request)
                }

            assertThat(exception.status).isEqualTo(HttpStatus.BAD_REQUEST)
        }

    }
}

