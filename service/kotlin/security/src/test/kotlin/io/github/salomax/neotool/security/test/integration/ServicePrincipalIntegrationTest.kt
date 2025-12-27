package io.github.salomax.neotool.security.test.integration

import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.security.repo.PermissionRepository
import io.github.salomax.neotool.security.repo.PrincipalPermissionRepository
import io.github.salomax.neotool.security.repo.PrincipalRepository
import io.github.salomax.neotool.security.service.AuthorizationService
import io.github.salomax.neotool.security.service.JwtService
import io.github.salomax.neotool.security.service.PrincipalType
import io.github.salomax.neotool.security.service.RequestPrincipalProvider
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("Service Principal Integration Tests")
class ServicePrincipalIntegrationTest : BaseIntegrationTest(), PostgresIntegrationTest {
    @Inject
    lateinit var principalRepository: PrincipalRepository

    @Inject
    lateinit var permissionRepository: PermissionRepository

    @Inject
    lateinit var principalPermissionRepository: PrincipalPermissionRepository

    @Inject
    lateinit var jwtService: JwtService

    @Inject
    lateinit var requestPrincipalProvider: RequestPrincipalProvider

    @Inject
    lateinit var authorizationService: AuthorizationService

    @Inject
    lateinit var servicePrincipalTestFixtures: ServicePrincipalTestFixtures

    @Test
    fun `should create service principal and check permission`() {
        // Arrange
        val serviceId = UUID.randomUUID()
        val permissionName = "assets:upload"

        // Create permission
        val permission =
            permissionRepository.save(
                io.github.salomax.neotool.security.model.PermissionEntity(
                    id = null,
                    name = permissionName,
                ),
            )

        // Create service principal with permission
        val (principal, _) =
            servicePrincipalTestFixtures.createServicePrincipalWithPermissions(
                serviceId = serviceId,
                permissions = listOf(permissionName),
                enabled = true,
            )

        // Act
        val result = authorizationService.checkServicePermission(serviceId, permissionName)

        // Assert
        assertThat(result.allowed).isTrue()
        assertThat(principal.principalType).isEqualTo(PrincipalType.SERVICE)
        assertThat(principal.externalId).isEqualTo(serviceId.toString())
        assertThat(principal.enabled).isTrue()
    }

    @Test
    fun `should deny permission check when service principal is disabled`() {
        // Arrange
        val serviceId = UUID.randomUUID()
        val permissionName = "assets:upload"

        // Create permission
        val permission =
            permissionRepository.save(
                io.github.salomax.neotool.security.model.PermissionEntity(
                    id = null,
                    name = permissionName,
                ),
            )

        // Create disabled service principal with permission
        val (principal, _) =
            servicePrincipalTestFixtures.createServicePrincipalWithPermissions(
                serviceId = serviceId,
                permissions = listOf(permissionName),
                // Disabled principal
                enabled = false,
            )

        // Act
        val result = authorizationService.checkServicePermission(serviceId, permissionName)

        // Assert
        assertThat(result.allowed).isFalse()
        assertThat(result.reason).contains("Service principal is disabled")
        assertThat(principal.enabled).isFalse()
    }

    @Test
    fun `should parse service token and create principal`() {
        // Arrange
        val serviceId = UUID.randomUUID()
        val targetAudience = "assets-service"
        val permissions = listOf("assets:upload", "assets:read")

        val token = jwtService.generateServiceToken(serviceId, targetAudience, permissions)

        // Act
        val principal = requestPrincipalProvider.fromToken(token)

        // Assert
        assertThat(principal.principalType).isEqualTo(PrincipalType.SERVICE)
        assertThat(principal.serviceId).isEqualTo(serviceId)
        assertThat(principal.userId).isNull()
        assertThat(principal.permissionsFromToken).containsExactlyInAnyOrderElementsOf(permissions)
    }

    @Test
    fun `should parse service token with user context`() {
        // Arrange
        val serviceId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val targetAudience = "assets-service"
        val servicePermissions = listOf("assets:upload")
        val userPermissions = listOf("user:read")

        val token =
            jwtService.generateServiceTokenWithUserContext(
                serviceId,
                targetAudience,
                servicePermissions,
                userId,
                userPermissions,
            )

        // Act
        val principal = requestPrincipalProvider.fromToken(token)

        // Assert
        assertThat(principal.principalType).isEqualTo(PrincipalType.SERVICE)
        assertThat(principal.serviceId).isEqualTo(serviceId)
        assertThat(principal.userId).isEqualTo(userId)
        assertThat(principal.permissionsFromToken).containsExactlyInAnyOrderElementsOf(servicePermissions)
        assertThat(principal.userPermissions).containsExactlyInAnyOrderElementsOf(userPermissions)
    }
}
