package io.github.salomax.neotool.security.graphql.dataloader

import io.github.salomax.neotool.security.graphql.dto.GroupDTO
import io.github.salomax.neotool.security.graphql.resolver.RoleManagementResolver
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderOptions
import java.util.concurrent.CompletableFuture

/**
 * DataLoader for batch loading groups assigned to roles.
 * Prevents N+1 queries when resolving Role.groups.
 */
object RoleGroupsDataLoader {
    const val KEY = "roleGroups"

    fun create(roleManagementResolver: RoleManagementResolver): DataLoader<String, List<GroupDTO>> {
        return DataLoaderFactory.newDataLoader<String, List<GroupDTO>>(
            { roleIds ->
                val batchResult = roleManagementResolver.resolveRoleGroupsBatch(roleIds)
                CompletableFuture.completedFuture(
                    roleIds.map { roleId -> batchResult[roleId] ?: emptyList() },
                )
            },
            DataLoaderOptions.newOptions().setBatchingEnabled(true),
        )
    }
}
