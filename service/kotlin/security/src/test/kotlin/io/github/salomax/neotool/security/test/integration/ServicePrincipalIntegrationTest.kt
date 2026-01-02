package io.github.salomax.neotool.security.test.integration

import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipalProvider
import io.github.salomax.neotool.security.service.authorization.AuthorizationService
import io.github.salomax.neotool.security.service.jwt.JwtTokenIssuer
import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.common.test.transaction.runTransaction
import io.github.salomax.neotool.security.repo.PermissionRepository
import io.github.salomax.neotool.security.repo.PrincipalRepository
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import java.util.UUID

@MicronautTest(startApplication = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Service Principal Integration Tests")
@Tag("integration")
@Tag("principal")
@Tag("security")
@TestMethodOrder(MethodOrderer.Random::class)
class ServicePrincipalIntegrationTest :
    BaseIntegrationTest(),
    PostgresIntegrationTest {
    @Inject
    lateinit var principalRepository: PrincipalRepository

    @Inject
    lateinit var permissionRepository: PermissionRepository

    @Inject
    lateinit var jwtTokenIssuer: JwtTokenIssuer

    @Inject
    lateinit var requestPrincipalProvider: RequestPrincipalProvider

    @Inject
    lateinit var authorizationService: AuthorizationService

    @Inject
    lateinit var servicePrincipalTestFixtures: ServicePrincipalTestFixtures

    @Inject
    lateinit var entityManager: EntityManager

    @Test
    fun `should create service principal and check permission`() {
        // Arrange
        val serviceId = UUID.randomUUID()
        val permissionName = "assets:upload_${UUID.randomUUID()}"

        // Create permission
        val permission =
            entityManager.runTransaction {
                permissionRepository.findByName(permissionName).orElseGet {
                    permissionRepository.save(
                        io.github.salomax.neotool.security.model.PermissionEntity(
                            id = null,
                            name = permissionName,
                        ),
                    )
                }
            }

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
        val permissionName = "assets:upload_${UUID.randomUUID()}"

        // Create permission
        val permission =
            entityManager.runTransaction {
                permissionRepository.findByName(permissionName).orElseGet {
                    permissionRepository.save(
                        io.github.salomax.neotool.security.model.PermissionEntity(
                            id = null,
                            name = permissionName,
                        ),
                    )
                }
            }

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

        val token = jwtTokenIssuer.generateServiceToken(serviceId, targetAudience, permissions)

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
            jwtTokenIssuer.generateServiceTokenWithUserContext(
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
