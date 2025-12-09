package io.github.salomax.neotool.security.graphql.dataloader

import io.github.salomax.neotool.security.graphql.dto.PermissionDTO
import io.github.salomax.neotool.security.graphql.resolver.RoleManagementResolver
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderOptions
import java.util.concurrent.CompletableFuture

/**
 * DataLoader for batch loading role permissions.
 * This prevents N+1 queries when multiple roles request their permissions.
 */
object RolePermissionsDataLoader {
    const val KEY = "rolePermissions"

    fun create(roleManagementResolver: RoleManagementResolver): DataLoader<String, List<PermissionDTO>> {
        return DataLoaderFactory.newDataLoader<String, List<PermissionDTO>>(
            { roleIds ->
                val batchResult = roleManagementResolver.resolveRolePermissionsBatch(roleIds)
                // Return results in the same order as requested
                CompletableFuture.completedFuture(
                    roleIds.map { roleId -> batchResult[roleId] ?: emptyList() },
                )
            },
            DataLoaderOptions.newOptions().setBatchingEnabled(true),
        )
    }
}
