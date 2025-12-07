package io.github.salomax.neotool.security.graphql.dataloader

import io.github.salomax.neotool.security.graphql.dto.RoleDTO
import io.github.salomax.neotool.security.graphql.resolver.PermissionManagementResolver
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderOptions
import java.util.concurrent.CompletableFuture

/**
 * DataLoader for batch loading permission roles.
 * This prevents N+1 queries when multiple permissions request their roles.
 */
object PermissionRolesDataLoader {
    const val KEY = "permissionRoles"

    fun create(permissionManagementResolver: PermissionManagementResolver): DataLoader<String, List<RoleDTO>> {
        return DataLoaderFactory.newDataLoader<String, List<RoleDTO>>(
            { permissionIds ->
                val batchResult = permissionManagementResolver.resolvePermissionRolesBatch(permissionIds)
                // Return results in the same order as requested
                CompletableFuture.completedFuture(
                    permissionIds.map { permissionId -> batchResult[permissionId] ?: emptyList() },
                )
            },
            DataLoaderOptions.newOptions().setBatchingEnabled(true),
        )
    }
}
