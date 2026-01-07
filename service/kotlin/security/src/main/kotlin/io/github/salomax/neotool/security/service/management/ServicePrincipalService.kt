package io.github.salomax.neotool.security.service.management

import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.security.model.PrincipalEntity
import io.github.salomax.neotool.security.model.PrincipalPermissionEntity
import io.github.salomax.neotool.security.model.ServiceCredentialEntity
import io.github.salomax.neotool.security.repo.PermissionRepository
import io.github.salomax.neotool.security.repo.PrincipalPermissionRepository
import io.github.salomax.neotool.security.repo.PrincipalRepository
import io.github.salomax.neotool.security.repo.ServiceCredentialRepository
import io.github.salomax.neotool.security.service.authentication.AuthenticationService
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import mu.KotlinLogging
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Service for managing service principals and their credentials.
 * Handles registration, credential validation, and permission management for services.
 */
@Singleton
open class ServicePrincipalService(
    private val principalRepository: PrincipalRepository,
    private val serviceCredentialRepository: ServiceCredentialRepository,
    private val principalPermissionRepository: PrincipalPermissionRepository,
    private val permissionRepository: PermissionRepository,
    private val authenticationService: AuthenticationService,
) {
    private val logger = KotlinLogging.logger {}
    private val secureRandom = SecureRandom()

    /**
     * Generate a cryptographically secure random secret.
     * Uses 32 bytes (256 bits) of entropy, base64url-encoded to 44 characters.
     */
    private fun generateSecureSecret(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * Result of service registration.
     */
    data class ServiceRegistrationResult(
        val principalId: UUID,
        val serviceId: String,
        // One-time clear secret (only returned at creation)
        val clientSecret: String,
        val permissions: List<String>,
    )

    /**
     * Register a new service principal with credentials and permissions.
     * Client secret is auto-generated server-side for security.
     *
     * @param serviceId The service identifier (used as external_id)
     * @param permissions List of permission names to assign to the service
     * @return ServiceRegistrationResult with principal ID and one-time clear secret
     * @throws IllegalArgumentException if service already exists or permission not found
     */
    @Transactional
    open fun registerService(
        serviceId: String,
        permissions: List<String>,
    ): ServiceRegistrationResult {
        // Check if service already exists
        val existingPrincipal =
            principalRepository.findByPrincipalTypeAndExternalId(PrincipalType.SERVICE, serviceId)

        if (existingPrincipal.isPresent) {
            throw IllegalArgumentException(
                "Service with ID '$serviceId' already exists. " +
                    "Use rotateServiceCredentials() to update credentials or " +
                    "updateServicePermissions() to modify permissions.",
            )
        }

        logger.info { "Creating new service principal: $serviceId" }

        // Generate secure secret server-side
        val clientSecret = generateSecureSecret()

        // Create principal
        val principal =
            PrincipalEntity(
                id = null,
                principalType = PrincipalType.SERVICE,
                externalId = serviceId,
                enabled = true,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                version = 0,
            )
        val savedPrincipal = principalRepository.save(principal)
        val principalId = savedPrincipal.id ?: throw IllegalStateException("Principal ID is null after save")

        // Hash and store credential
        val credentialHash = authenticationService.hashPassword(clientSecret)
        val credential =
            ServiceCredentialEntity(
                id = principalId,
                credentialHash = credentialHash,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        serviceCredentialRepository.save(credential)
        logger.info { "Created credential for service: $serviceId" }

        // Assign permissions
        val assignedPermissions = mutableListOf<String>()
        for (permissionName in permissions) {
            val permission =
                permissionRepository
                    .findByName(permissionName)
                    .orElseThrow {
                        IllegalArgumentException("Permission not found: $permissionName")
                    }

            // Check if permission already assigned (avoid duplicates)
            val permissionId = permission.id ?: throw IllegalStateException("Permission ID is null")
            val alreadyAssigned =
                principalPermissionRepository.existsByPrincipalIdAndPermissionIdWithNullPattern(
                    principalId,
                    permissionId,
                )

            if (!alreadyAssigned) {
                // No resource pattern for service permissions
                val principalPermission =
                    PrincipalPermissionEntity(
                        principalId = principalId,
                        permissionId = permissionId,
                        resourcePattern = null,
                    )
                principalPermissionRepository.save(principalPermission)
                assignedPermissions.add(permissionName)
                logger.debug { "Assigned permission '$permissionName' to service: $serviceId" }
            } else {
                logger.debug { "Permission '$permissionName' already assigned to service: $serviceId" }
                assignedPermissions.add(permissionName)
            }
        }

        logger.info { "Registered service '$serviceId' with ${assignedPermissions.size} permissions" }

        // Return one-time clear secret (only at creation)
        return ServiceRegistrationResult(
            principalId = principalId,
            serviceId = serviceId,
            clientSecret = clientSecret,
            permissions = assignedPermissions,
        )
    }

    /**
     * Rotate service credentials.
     * Requires knowledge of the current secret to prevent unauthorized takeover.
     *
     * @param serviceId The service identifier
     * @param currentSecret The current client secret (must be valid)
     * @return ServiceRegistrationResult with new secret
     * @throws IllegalArgumentException if current credentials are invalid
     */
    @Transactional
    open fun rotateServiceCredentials(
        serviceId: String,
        currentSecret: String,
    ): ServiceRegistrationResult {
        // Validate current secret first
        val principal =
            validateServiceCredentials(serviceId, currentSecret)
                ?: throw IllegalArgumentException("Invalid current credentials for service: $serviceId")

        val principalId = principal.id ?: throw IllegalStateException("Principal ID is null")

        // Generate new secret
        val newSecret = generateSecureSecret()
        val credentialHash = authenticationService.hashPassword(newSecret)

        // Update credential
        val existingCredential =
            serviceCredentialRepository
                .findById(principalId)
                .orElseThrow { IllegalStateException("Credential not found for service: $serviceId") }

        existingCredential.credentialHash = credentialHash
        existingCredential.updatedAt = Instant.now()
        serviceCredentialRepository.update(existingCredential)

        logger.info { "Rotated credentials for service: $serviceId" }

        // Return one-time clear secret
        return ServiceRegistrationResult(
            principalId = principalId,
            serviceId = serviceId,
            clientSecret = newSecret,
            permissions = getServicePermissions(principalId),
        )
    }

    /**
     * Update service permissions (add or remove).
     *
     * @param serviceId The service identifier
     * @param permissionsToAdd List of permission names to add
     * @param permissionsToRemove List of permission names to remove
     * @return List of current permissions after update
     * @throws IllegalArgumentException if service not found or permission not found
     */
    @Transactional
    open fun updateServicePermissions(
        serviceId: String,
        permissionsToAdd: List<String> = emptyList(),
        permissionsToRemove: List<String> = emptyList(),
    ): List<String> {
        val principal =
            principalRepository
                .findByPrincipalTypeAndExternalId(PrincipalType.SERVICE, serviceId)
                .orElseThrow { IllegalArgumentException("Service not found: $serviceId") }

        val principalId = principal.id ?: throw IllegalStateException("Principal ID is null")

        // Add permissions
        for (permissionName in permissionsToAdd) {
            val permission =
                permissionRepository
                    .findByName(permissionName)
                    .orElseThrow {
                        IllegalArgumentException("Permission not found: $permissionName")
                    }

            val permissionId = permission.id ?: throw IllegalStateException("Permission ID is null")
            val alreadyAssigned =
                principalPermissionRepository.existsByPrincipalIdAndPermissionIdWithNullPattern(
                    principalId,
                    permissionId,
                )

            if (!alreadyAssigned) {
                val principalPermission =
                    PrincipalPermissionEntity(
                        principalId = principalId,
                        permissionId = permissionId,
                        resourcePattern = null,
                    )
                principalPermissionRepository.save(principalPermission)
                logger.debug { "Added permission '$permissionName' to service: $serviceId" }
            }
        }

        // Remove permissions
        for (permissionName in permissionsToRemove) {
            val permission =
                permissionRepository
                    .findByName(permissionName)
                    .orElseThrow {
                        IllegalArgumentException("Permission not found: $permissionName")
                    }

            val permissionId = permission.id ?: throw IllegalStateException("Permission ID is null")

            // Find and delete permission assignments
            val assignments =
                principalPermissionRepository
                    .findByPrincipalId(principalId)
                    .filter { it.permissionId == permissionId && it.resourcePattern == null }

            assignments.forEach {
                principalPermissionRepository.delete(it)
                logger.debug { "Removed permission '$permissionName' from service: $serviceId" }
            }
        }

        logger.info {
            "Updated permissions for service '$serviceId': " +
                "added ${permissionsToAdd.size}, removed ${permissionsToRemove.size}"
        }

        return getServicePermissions(principalId)
    }

    /**
     * Validate service credentials.
     *
     * @param serviceId The service identifier
     * @param clientSecret The client secret to validate
     * @return Principal if credentials are valid and principal is enabled, null otherwise
     */
    fun validateServiceCredentials(
        serviceId: String,
        clientSecret: String,
    ): PrincipalEntity? {
        // Find principal
        val principal =
            principalRepository
                .findByPrincipalTypeAndExternalId(PrincipalType.SERVICE, serviceId)
                .orElse(null)
                ?: run {
                    logger.debug { "Service principal not found: $serviceId" }
                    return null
                }

        // Check if principal is enabled
        if (!principal.enabled) {
            logger.debug { "Service principal is disabled: $serviceId" }
            return null
        }

        val principalId = principal.id ?: return null

        // Load credential
        val credential =
            serviceCredentialRepository
                .findById(principalId)
                .orElse(null)
                ?: run {
                    logger.debug { "Service credential not found for: $serviceId" }
                    return null
                }

        // Verify secret
        val isValid =
            authenticationService.verifyPassword(clientSecret, credential.credentialHash)
        if (!isValid) {
            logger.debug { "Invalid client secret for service: $serviceId" }
            return null
        }

        logger.debug { "Credentials validated for service: $serviceId" }
        return principal
    }

    /**
     * Get all permissions assigned to a service principal.
     *
     * @param principalId The principal ID
     * @return List of permission names
     */
    fun getServicePermissions(principalId: UUID): List<String> {
        // Load principal permissions
        val principalPermissions = principalPermissionRepository.findByPrincipalId(principalId)

        if (principalPermissions.isEmpty()) {
            return emptyList()
        }

        // Extract permission IDs
        val permissionIds = principalPermissions.map { it.permissionId }

        // Load permissions
        val permissions = permissionRepository.findByIdIn(permissionIds)

        // Map to permission names
        return permissions.map { it.name }
    }
}
