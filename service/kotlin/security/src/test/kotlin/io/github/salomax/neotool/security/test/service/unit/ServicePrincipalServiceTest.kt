package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.common.security.principal.PrincipalType
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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.Optional
import java.util.UUID

@DisplayName("ServicePrincipalService Unit Tests")
class ServicePrincipalServiceTest {
    private lateinit var principalRepository: PrincipalRepository
    private lateinit var serviceCredentialRepository: ServiceCredentialRepository
    private lateinit var principalPermissionRepository: PrincipalPermissionRepository
    private lateinit var permissionRepository: PermissionRepository
    private lateinit var authenticationService: AuthenticationService
    private lateinit var servicePrincipalService: ServicePrincipalService

    @BeforeEach
    fun setUp() {
        principalRepository = mock()
        serviceCredentialRepository = mock()
        principalPermissionRepository = mock()
        permissionRepository = mock()
        authenticationService = mock()
        servicePrincipalService =
            ServicePrincipalService(
                principalRepository,
                serviceCredentialRepository,
                principalPermissionRepository,
                permissionRepository,
                authenticationService,
            )
    }

    @Nested
    @DisplayName("registerService")
    inner class RegisterServiceTests {
        @Test
        fun `should register new service with permissions`() {
            // Arrange
            val serviceId = "test-service"
            val permissionName = "assets:upload"
            val permissionId = UUID.randomUUID()
            val principalId = UUID.randomUUID()

            val permission =
                PermissionEntity(
                    id = permissionId,
                    name = permissionName,
                )

            val savedPrincipal =
                PrincipalEntity(
                    id = principalId,
                    principalType = PrincipalType.SERVICE,
                    externalId = serviceId,
                    enabled = true,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                    version = 0,
                )

            whenever(principalRepository.findByPrincipalTypeAndExternalId(PrincipalType.SERVICE, serviceId))
                .thenReturn(Optional.empty())
            whenever(principalRepository.save(any())).thenReturn(savedPrincipal)
            whenever(permissionRepository.findByName(permissionName))
                .thenReturn(Optional.of(permission))
            whenever(
                principalPermissionRepository.existsByPrincipalIdAndPermissionIdWithNullPattern(
                    any(),
                    any(),
                ),
            ).thenReturn(false)
            whenever(authenticationService.hashPassword(any())).thenReturn("hashed-secret")

            // Act
            val result = servicePrincipalService.registerService(serviceId, listOf(permissionName))

            // Assert
            assertThat(result.serviceId).isEqualTo(serviceId)
            assertThat(result.principalId).isEqualTo(principalId)
            assertThat(result.permissions).containsExactly(permissionName)
            assertThat(result.clientSecret).isNotBlank()

            verify(principalRepository).save(any())
            verify(serviceCredentialRepository).save(any())
            verify(principalPermissionRepository).save(any())
            verify(authenticationService).hashPassword(any())
        }

        @Test
        fun `should throw exception when service already exists`() {
            // Arrange
            val serviceId = "existing-service"
            val existingPrincipal =
                PrincipalEntity(
                    id = UUID.randomUUID(),
                    principalType = PrincipalType.SERVICE,
                    externalId = serviceId,
                    enabled = true,
                )

            whenever(principalRepository.findByPrincipalTypeAndExternalId(PrincipalType.SERVICE, serviceId))
                .thenReturn(Optional.of(existingPrincipal))

            // Act & Assert
            val exception =
                assertThrows<IllegalArgumentException> {
                    servicePrincipalService.registerService(serviceId, emptyList())
                }

            assertThat(exception.message).contains("already exists")
            verify(principalRepository, never()).save(any())
        }

        @Test
        fun `should throw exception when permission not found`() {
            // Arrange
            val serviceId = "test-service"
            val permissionName = "nonexistent:permission"
            val principalId = UUID.randomUUID()

            val savedPrincipal =
                PrincipalEntity(
                    id = principalId,
                    principalType = PrincipalType.SERVICE,
                    externalId = serviceId,
                    enabled = true,
                )

            whenever(principalRepository.findByPrincipalTypeAndExternalId(PrincipalType.SERVICE, serviceId))
                .thenReturn(Optional.empty())
            whenever(principalRepository.save(any())).thenReturn(savedPrincipal)
            whenever(permissionRepository.findByName(permissionName))
                .thenReturn(Optional.empty())
            whenever(authenticationService.hashPassword(any())).thenReturn("hashed-secret")

            // Act & Assert
            val exception =
                assertThrows<IllegalArgumentException> {
                    servicePrincipalService.registerService(serviceId, listOf(permissionName))
                }

            assertThat(exception.message).contains("Permission not found")
        }

        @Test
        fun `should not duplicate permissions when already assigned`() {
            // Arrange
            val serviceId = "test-service"
            val permissionName = "assets:upload"
            val permissionId = UUID.randomUUID()
            val principalId = UUID.randomUUID()

            val permission =
                PermissionEntity(
                    id = permissionId,
                    name = permissionName,
                )

            val savedPrincipal =
                PrincipalEntity(
                    id = principalId,
                    principalType = PrincipalType.SERVICE,
                    externalId = serviceId,
                    enabled = true,
                )

            whenever(principalRepository.findByPrincipalTypeAndExternalId(PrincipalType.SERVICE, serviceId))
                .thenReturn(Optional.empty())
            whenever(principalRepository.save(any())).thenReturn(savedPrincipal)
            whenever(permissionRepository.findByName(permissionName))
                .thenReturn(Optional.of(permission))
            whenever(
                principalPermissionRepository.existsByPrincipalIdAndPermissionIdWithNullPattern(
                    any(),
                    any(),
                ),
            ).thenReturn(true) // Already assigned
            whenever(authenticationService.hashPassword(any())).thenReturn("hashed-secret")

            // Act
            val result = servicePrincipalService.registerService(serviceId, listOf(permissionName))

            // Assert
            assertThat(result.permissions).containsExactly(permissionName)
            verify(principalPermissionRepository, never()).save(any())
        }

        @Test
        fun `should register service with empty permissions list`() {
            // Arrange
            val serviceId = "test-service"
            val principalId = UUID.randomUUID()

            val savedPrincipal =
                PrincipalEntity(
                    id = principalId,
                    principalType = PrincipalType.SERVICE,
                    externalId = serviceId,
                    enabled = true,
                )

            whenever(principalRepository.findByPrincipalTypeAndExternalId(PrincipalType.SERVICE, serviceId))
                .thenReturn(Optional.empty())
            whenever(principalRepository.save(any())).thenReturn(savedPrincipal)
            whenever(authenticationService.hashPassword(any())).thenReturn("hashed-secret")

            // Act
            val result = servicePrincipalService.registerService(serviceId, emptyList())

            // Assert
            assertThat(result.serviceId).isEqualTo(serviceId)
            assertThat(result.permissions).isEmpty()
            verify(principalPermissionRepository, never()).save(any())
        }
    }

    @Nested
    @DisplayName("validateServiceCredentials")
    inner class ValidateServiceCredentialsTests {
        @Test
        fun `should return principal when credentials are valid`() {
            // Arrange
            val serviceId = "test-service"
            val clientSecret = "valid-secret"
            val principalId = UUID.randomUUID()

            val principal =
                PrincipalEntity(
                    id = principalId,
                    principalType = PrincipalType.SERVICE,
                    externalId = serviceId,
                    enabled = true,
                )

            val credential =
                ServiceCredentialEntity(
                    id = principalId,
                    credentialHash = "hashed-secret",
                )

            whenever(principalRepository.findByPrincipalTypeAndExternalId(PrincipalType.SERVICE, serviceId))
                .thenReturn(Optional.of(principal))
            whenever(serviceCredentialRepository.findById(principalId))
                .thenReturn(Optional.of(credential))
            whenever(authenticationService.verifyPassword(clientSecret, credential.credentialHash))
                .thenReturn(true)

            // Act
            val result = servicePrincipalService.validateServiceCredentials(serviceId, clientSecret)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result?.id).isEqualTo(principalId)
            assertThat(result?.externalId).isEqualTo(serviceId)
        }

        @Test
        fun `should return null when service not found`() {
            // Arrange
            val serviceId = "nonexistent-service"
            val clientSecret = "secret"

            whenever(principalRepository.findByPrincipalTypeAndExternalId(PrincipalType.SERVICE, serviceId))
                .thenReturn(Optional.empty())

            // Act
            val result = servicePrincipalService.validateServiceCredentials(serviceId, clientSecret)

            // Assert
            assertThat(result).isNull()
        }

        @Test
        fun `should return null when principal is disabled`() {
            // Arrange
            val serviceId = "disabled-service"
            val clientSecret = "secret"
            val principalId = UUID.randomUUID()

            val principal =
                PrincipalEntity(
                    id = principalId,
                    principalType = PrincipalType.SERVICE,
                    externalId = serviceId,
                    enabled = false,
                )

            whenever(principalRepository.findByPrincipalTypeAndExternalId(PrincipalType.SERVICE, serviceId))
                .thenReturn(Optional.of(principal))

            // Act
            val result = servicePrincipalService.validateServiceCredentials(serviceId, clientSecret)

            // Assert
            assertThat(result).isNull()
        }

        @Test
        fun `should return null when credential not found`() {
            // Arrange
            val serviceId = "test-service"
            val clientSecret = "secret"
            val principalId = UUID.randomUUID()

            val principal =
                PrincipalEntity(
                    id = principalId,
                    principalType = PrincipalType.SERVICE,
                    externalId = serviceId,
                    enabled = true,
                )

            whenever(principalRepository.findByPrincipalTypeAndExternalId(PrincipalType.SERVICE, serviceId))
                .thenReturn(Optional.of(principal))
            whenever(serviceCredentialRepository.findById(principalId))
                .thenReturn(Optional.empty())

            // Act
            val result = servicePrincipalService.validateServiceCredentials(serviceId, clientSecret)

            // Assert
            assertThat(result).isNull()
        }

        @Test
        fun `should return null when secret is invalid`() {
            // Arrange
            val serviceId = "test-service"
            val clientSecret = "invalid-secret"
            val principalId = UUID.randomUUID()

            val principal =
                PrincipalEntity(
                    id = principalId,
                    principalType = PrincipalType.SERVICE,
                    externalId = serviceId,
                    enabled = true,
                )

            val credential =
                ServiceCredentialEntity(
                    id = principalId,
                    credentialHash = "hashed-secret",
                )

            whenever(principalRepository.findByPrincipalTypeAndExternalId(PrincipalType.SERVICE, serviceId))
                .thenReturn(Optional.of(principal))
            whenever(serviceCredentialRepository.findById(principalId))
                .thenReturn(Optional.of(credential))
            whenever(authenticationService.verifyPassword(clientSecret, credential.credentialHash))
                .thenReturn(false)

            // Act
            val result = servicePrincipalService.validateServiceCredentials(serviceId, clientSecret)

            // Assert
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("rotateServiceCredentials")
    inner class RotateServiceCredentialsTests {
        @Test
        fun `should rotate credentials with valid current secret`() {
            // Arrange
            val serviceId = "test-service"
            val currentSecret = "old-secret"
            val principalId = UUID.randomUUID()

            val principal =
                PrincipalEntity(
                    id = principalId,
                    principalType = PrincipalType.SERVICE,
                    externalId = serviceId,
                    enabled = true,
                )

            val credential =
                ServiceCredentialEntity(
                    id = principalId,
                    credentialHash = "old-hash",
                )

            whenever(principalRepository.findByPrincipalTypeAndExternalId(PrincipalType.SERVICE, serviceId))
                .thenReturn(Optional.of(principal))
            whenever(serviceCredentialRepository.findById(principalId))
                .thenReturn(Optional.of(credential))
            whenever(authenticationService.verifyPassword(currentSecret, credential.credentialHash))
                .thenReturn(true)
            whenever(authenticationService.hashPassword(any())).thenReturn("new-hash")
            whenever(principalPermissionRepository.findByPrincipalId(principalId))
                .thenReturn(emptyList())

            // Act
            val result = servicePrincipalService.rotateServiceCredentials(serviceId, currentSecret)

            // Assert
            assertThat(result.serviceId).isEqualTo(serviceId)
            assertThat(result.principalId).isEqualTo(principalId)
            assertThat(result.clientSecret).isNotBlank()
            verify(serviceCredentialRepository).update(any())
        }

        @Test
        fun `should throw exception when current secret is invalid`() {
            // Arrange
            val serviceId = "test-service"
            val currentSecret = "invalid-secret"
            val principalId = UUID.randomUUID()

            val principal =
                PrincipalEntity(
                    id = principalId,
                    principalType = PrincipalType.SERVICE,
                    externalId = serviceId,
                    enabled = true,
                )

            val credential =
                ServiceCredentialEntity(
                    id = principalId,
                    credentialHash = "hashed-secret",
                )

            whenever(principalRepository.findByPrincipalTypeAndExternalId(PrincipalType.SERVICE, serviceId))
                .thenReturn(Optional.of(principal))
            whenever(serviceCredentialRepository.findById(principalId))
                .thenReturn(Optional.of(credential))
            whenever(authenticationService.verifyPassword(currentSecret, credential.credentialHash))
                .thenReturn(false)

            // Act & Assert
            val exception =
                assertThrows<IllegalArgumentException> {
                    servicePrincipalService.rotateServiceCredentials(serviceId, currentSecret)
                }

            assertThat(exception.message).contains("Invalid current credentials")
        }
    }

    @Nested
    @DisplayName("updateServicePermissions")
    inner class UpdateServicePermissionsTests {
        @Test
        fun `should add permissions to service`() {
            // Arrange
            val serviceId = "test-service"
            val permissionName = "assets:upload"
            val permissionId = UUID.randomUUID()
            val principalId = UUID.randomUUID()

            val principal =
                PrincipalEntity(
                    id = principalId,
                    principalType = PrincipalType.SERVICE,
                    externalId = serviceId,
                    enabled = true,
                )

            val permission =
                PermissionEntity(
                    id = permissionId,
                    name = permissionName,
                )

            whenever(principalRepository.findByPrincipalTypeAndExternalId(PrincipalType.SERVICE, serviceId))
                .thenReturn(Optional.of(principal))
            whenever(permissionRepository.findByName(permissionName))
                .thenReturn(Optional.of(permission))
            whenever(
                principalPermissionRepository.existsByPrincipalIdAndPermissionIdWithNullPattern(
                    any(),
                    any(),
                ),
            ).thenReturn(false)
            whenever(principalPermissionRepository.findByPrincipalId(principalId))
                .thenReturn(emptyList())
            whenever(permissionRepository.findByIdIn(any())).thenReturn(emptyList())

            // Act
            val result = servicePrincipalService.updateServicePermissions(
                serviceId,
                permissionsToAdd = listOf(permissionName),
            )

            // Assert
            verify(principalPermissionRepository).save(any())
        }

        @Test
        fun `should remove permissions from service`() {
            // Arrange
            val serviceId = "test-service"
            val permissionName = "assets:upload"
            val permissionId = UUID.randomUUID()
            val principalId = UUID.randomUUID()

            val principal =
                PrincipalEntity(
                    id = principalId,
                    principalType = PrincipalType.SERVICE,
                    externalId = serviceId,
                    enabled = true,
                )

            val permission =
                PermissionEntity(
                    id = permissionId,
                    name = permissionName,
                )

            val principalPermission =
                PrincipalPermissionEntity(
                    principalId = principalId,
                    permissionId = permissionId,
                    resourcePattern = null,
                )

            whenever(principalRepository.findByPrincipalTypeAndExternalId(PrincipalType.SERVICE, serviceId))
                .thenReturn(Optional.of(principal))
            whenever(permissionRepository.findByName(permissionName))
                .thenReturn(Optional.of(permission))
            whenever(principalPermissionRepository.findByPrincipalId(principalId))
                .thenReturn(listOf(principalPermission))
            whenever(permissionRepository.findByIdIn(any())).thenReturn(emptyList())

            // Act
            val result = servicePrincipalService.updateServicePermissions(
                serviceId,
                permissionsToRemove = listOf(permissionName),
            )

            // Assert
            verify(principalPermissionRepository).delete(any())
        }

        @Test
        fun `should throw exception when service not found`() {
            // Arrange
            val serviceId = "nonexistent-service"

            whenever(principalRepository.findByPrincipalTypeAndExternalId(PrincipalType.SERVICE, serviceId))
                .thenReturn(Optional.empty())

            // Act & Assert
            val exception =
                assertThrows<IllegalArgumentException> {
                    servicePrincipalService.updateServicePermissions(serviceId)
                }

            assertThat(exception.message).contains("Service not found")
        }
    }

    @Nested
    @DisplayName("getServicePermissions")
    inner class GetServicePermissionsTests {
        @Test
        fun `should return permissions for service`() {
            // Arrange
            val principalId = UUID.randomUUID()
            val permissionId = UUID.randomUUID()
            val permissionName = "assets:upload"

            val principalPermission =
                PrincipalPermissionEntity(
                    principalId = principalId,
                    permissionId = permissionId,
                    resourcePattern = null,
                )

            val permission =
                PermissionEntity(
                    id = permissionId,
                    name = permissionName,
                )

            whenever(principalPermissionRepository.findByPrincipalId(principalId))
                .thenReturn(listOf(principalPermission))
            whenever(permissionRepository.findByIdIn(listOf(permissionId)))
                .thenReturn(listOf(permission))

            // Act
            val result = servicePrincipalService.getServicePermissions(principalId)

            // Assert
            assertThat(result).containsExactly(permissionName)
        }

        @Test
        fun `should return empty list when no permissions assigned`() {
            // Arrange
            val principalId = UUID.randomUUID()

            whenever(principalPermissionRepository.findByPrincipalId(principalId))
                .thenReturn(emptyList())

            // Act
            val result = servicePrincipalService.getServicePermissions(principalId)

            // Assert
            assertThat(result).isEmpty()
        }
    }
}

