package io.github.salomax.neotool.security.graphql.dataloader

import io.github.salomax.neotool.security.graphql.dto.GroupDTO
import io.github.salomax.neotool.security.graphql.resolver.UserManagementResolver
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderOptions
import java.util.concurrent.CompletableFuture

/**
 * DataLoader for batch loading user groups.
 * This prevents N+1 queries when multiple users request their groups.
 */
object UserGroupsDataLoader {
    const val KEY = "userGroups"

    fun create(userManagementResolver: UserManagementResolver): DataLoader<String, List<GroupDTO>> {
        return DataLoaderFactory.newDataLoader<String, List<GroupDTO>>(
            { userIds ->
                val batchResult = userManagementResolver.resolveUserGroupsBatch(userIds)
                // Return results in the same order as requested
                CompletableFuture.completedFuture(
                    userIds.map { userId -> batchResult[userId] ?: emptyList() },
                )
            },
            DataLoaderOptions.newOptions().setBatchingEnabled(true),
        )
    }
}
