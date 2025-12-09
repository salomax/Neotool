package io.github.salomax.neotool.security.graphql.dataloader

import io.github.salomax.neotool.security.graphql.dto.PermissionDTO
import io.github.salomax.neotool.security.graphql.resolver.UserManagementResolver
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderOptions
import java.util.concurrent.CompletableFuture

/**
 * DataLoader for batch loading user permissions.
 * This prevents N+1 queries when multiple users request their permissions.
 */
object UserPermissionsDataLoader {
    const val KEY = "userPermissions"

    fun create(userManagementResolver: UserManagementResolver): DataLoader<String, List<PermissionDTO>> {
        return DataLoaderFactory.newDataLoader<String, List<PermissionDTO>>(
            { userIds ->
                val batchResult = userManagementResolver.resolveUserPermissionsBatch(userIds)
                // Return results in the same order as requested
                CompletableFuture.completedFuture(
                    userIds.map { userId -> batchResult[userId] ?: emptyList() },
                )
            },
            DataLoaderOptions.newOptions().setBatchingEnabled(true),
        )
    }
}
