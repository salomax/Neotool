package io.github.salomax.neotool.security.graphql.dataloader

import io.github.salomax.neotool.security.graphql.dto.RoleDTO
import io.github.salomax.neotool.security.graphql.resolver.UserManagementResolver
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderOptions
import java.util.concurrent.CompletableFuture

/**
 * DataLoader for batch loading user roles.
 * This prevents N+1 queries when multiple users request their roles.
 */
object UserRolesDataLoader {
    const val KEY = "userRoles"

    fun create(userManagementResolver: UserManagementResolver): DataLoader<String, List<RoleDTO>> {
        return DataLoaderFactory.newDataLoader<String, List<RoleDTO>>(
            { userIds ->
                val batchResult = userManagementResolver.resolveUserRolesBatch(userIds)
                // Return results in the same order as requested
                CompletableFuture.completedFuture(
                    userIds.map { userId -> batchResult[userId] ?: emptyList() },
                )
            },
            DataLoaderOptions.newOptions().setBatchingEnabled(true),
        )
    }
}
