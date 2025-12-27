package io.github.salomax.neotool.security.test.integration

import io.github.salomax.neotool.security.model.PrincipalEntity
import io.github.salomax.neotool.security.model.PrincipalPermissionEntity
import io.github.salomax.neotool.security.repo.PermissionRepository
import io.github.salomax.neotool.security.repo.PrincipalPermissionRepository
import io.github.salomax.neotool.security.repo.PrincipalRepository
import io.github.salomax.neotool.security.service.JwtService
import io.github.salomax.neotool.security.service.PrincipalType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Instant
import java.util.UUID

/**
 * Test fixtures for creating service principals and generating service tokens for testing.
 * Used in integration tests to test interservice security scenarios.
 */
@Singleton
class ServicePrincipalTestFixtures(
    @Inject private val principalRepository: PrincipalRepository,
    @Inject private val permissionRepository: PermissionRepository,
    @Inject private val principalPermissionRepository: PrincipalPermissionRepository,
    @Inject private val jwtService: JwtService,
) {
    /**
     * Create a service principal with the given service ID and enabled flag.
     *
     * @param serviceId The service identifier (will be stored as external_id)
     * @param enabled Whether the principal is enabled (default: true)
     * @return The created PrincipalEntity
     */
    fun createServicePrincipal(
        serviceId: UUID,
        enabled: Boolean = true,
    ): PrincipalEntity {
        val principal =
            PrincipalEntity(
                id = null,
                principalType = PrincipalType.SERVICE,
                externalId = serviceId.toString(),
                enabled = enabled,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                version = 0,
            )
        return principalRepository.save(principal)
    }

    /**
     * Assign a permission to a service principal.
     *
     * @param principalId The principal ID
     * @param permissionName The permission name (e.g., "assets:upload")
     * @param resourcePattern Optional resource pattern for resource-specific permissions
     * @return The created PrincipalPermissionEntity
     */
    fun assignPermissionToService(
        principalId: UUID,
        permissionName: String,
        resourcePattern: String? = null,
    ): PrincipalPermissionEntity {
        val permission =
            permissionRepository.findByName(permissionName)
                .orElseThrow { IllegalArgumentException("Permission not found: $permissionName") }

        val principalPermission =
            PrincipalPermissionEntity(
                principalId = principalId,
                permissionId = permission.id!!,
                resourcePattern = resourcePattern,
            )
        return principalPermissionRepository.save(principalPermission)
    }

    /**
     * Generate a service token for testing.
     *
     * @param serviceId The service ID
     * @param targetAudience The target service identifier (aud claim)
     * @param permissions List of permission names for the service
     * @return A signed JWT service token string
     */
    fun generateServiceToken(
        serviceId: UUID,
        targetAudience: String,
        permissions: List<String>,
    ): String {
        return jwtService.generateServiceToken(
            serviceId = serviceId,
            targetAudience = targetAudience,
            permissions = permissions,
        )
    }

    /**
     * Generate a service token with user context for testing.
     *
     * @param serviceId The service ID
     * @param targetAudience The target service identifier (aud claim)
     * @param permissions List of permission names for the service
     * @param userId The user ID being propagated
     * @param userPermissions List of permission names for the user
     * @return A signed JWT service token string with user context
     */
    fun generateServiceTokenWithUserContext(
        serviceId: UUID,
        targetAudience: String,
        permissions: List<String>,
        userId: UUID,
        userPermissions: List<String>,
    ): String {
        return jwtService.generateServiceTokenWithUserContext(
            serviceId = serviceId,
            targetAudience = targetAudience,
            permissions = permissions,
            userId = userId,
            userPermissions = userPermissions,
        )
    }

    /**
     * Create a complete service principal setup with permissions.
     * Helper method that creates the principal and assigns permissions in one call.
     *
     * @param serviceId The service identifier
     * @param permissions List of permission names to assign
     * @param enabled Whether the principal is enabled (default: true)
     * @return Pair of (PrincipalEntity, List<PrincipalPermissionEntity>)
     */
    fun createServicePrincipalWithPermissions(
        serviceId: UUID,
        permissions: List<String>,
        enabled: Boolean = true,
    ): Pair<PrincipalEntity, List<PrincipalPermissionEntity>> {
        val principal = createServicePrincipal(serviceId, enabled)
        val permissionEntities =
            permissions.map { permissionName ->
                assignPermissionToService(principal.id!!, permissionName)
            }
        return Pair(principal, permissionEntities)
    }
}
