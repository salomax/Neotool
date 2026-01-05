package io.github.salomax.neotool.security.repo

import jakarta.inject.Singleton
import java.util.UUID

interface PrincipalPermissionRepositoryCustom {
    fun existsByPrincipalIdAndPermissionIdAndResourcePattern(
        principalId: UUID,
        permissionId: UUID,
        resourcePattern: String?,
    ): Boolean
}

@Singleton
class PrincipalPermissionRepositoryImpl(
    private val repository: PrincipalPermissionRepository,
) : PrincipalPermissionRepositoryCustom {
    override fun existsByPrincipalIdAndPermissionIdAndResourcePattern(
        principalId: UUID,
        permissionId: UUID,
        resourcePattern: String?,
    ): Boolean {
        return if (resourcePattern == null) {
            repository.existsByPrincipalIdAndPermissionIdWithNullPattern(principalId, permissionId)
        } else {
            repository.existsByPrincipalIdAndPermissionIdWithPattern(principalId, permissionId, resourcePattern)
        }
    }
}
