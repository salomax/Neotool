package io.github.salomax.neotool.security.graphql.dataloader

import io.github.salomax.neotool.security.graphql.dto.RoleDTO
import io.github.salomax.neotool.security.graphql.resolver.GroupManagementResolver
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderOptions
import java.util.concurrent.CompletableFuture

/**
 * DataLoader for batch loading group roles.
 * This prevents N+1 queries when multiple groups request their roles.
 */
object GroupRolesDataLoader {
    const val KEY = "groupRoles"

    fun create(groupManagementResolver: GroupManagementResolver): DataLoader<String, List<RoleDTO>> {
        return DataLoaderFactory.newDataLoader<String, List<RoleDTO>>(
            { groupIds ->
                val batchResult = groupManagementResolver.resolveGroupRolesBatch(groupIds)
                // Return results in the same order as requested
                CompletableFuture.completedFuture(
                    groupIds.map { groupId -> batchResult[groupId] ?: emptyList() },
                )
            },
            DataLoaderOptions.newOptions().setBatchingEnabled(true),
        )
    }
}
