package io.github.salomax.neotool.security.graphql.dataloader

import io.github.salomax.neotool.security.graphql.dto.UserDTO
import io.github.salomax.neotool.security.graphql.resolver.GroupManagementResolver
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderOptions
import java.util.concurrent.CompletableFuture

/**
 * DataLoader for batch loading group members.
 * This prevents N+1 queries when multiple groups request their members.
 */
object GroupMembersDataLoader {
    const val KEY = "groupMembers"

    fun create(groupManagementResolver: GroupManagementResolver): DataLoader<String, List<UserDTO>> {
        return DataLoaderFactory.newDataLoader<String, List<UserDTO>>(
            { groupIds ->
                val batchResult = groupManagementResolver.resolveGroupMembersBatch(groupIds)
                // Return results in the same order as requested
                CompletableFuture.completedFuture(
                    groupIds.map { groupId -> batchResult[groupId] ?: emptyList() },
                )
            },
            DataLoaderOptions.newOptions().setBatchingEnabled(true),
        )
    }
}
