package io.github.salomax.neotool.security.graphql.dataloader

import io.github.salomax.neotool.common.graphql.DataLoaderRegistryFactory
import io.github.salomax.neotool.security.graphql.resolver.GroupManagementResolver
import io.github.salomax.neotool.security.graphql.resolver.PermissionManagementResolver
import io.github.salomax.neotool.security.graphql.resolver.RoleManagementResolver
import io.github.salomax.neotool.security.graphql.resolver.UserManagementResolver
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.dataloader.DataLoaderRegistry

/**
 * Factory for creating DataLoader registries in the security module.
 * Registers all security-related DataLoaders.
 */
@Singleton
@Named("security")
class SecurityDataLoaderRegistryFactory(
    private val groupManagementResolver: GroupManagementResolver,
    private val userManagementResolver: UserManagementResolver,
    private val roleManagementResolver: RoleManagementResolver,
    private val permissionManagementResolver: PermissionManagementResolver,
) : DataLoaderRegistryFactory {
    override fun createDataLoaderRegistry(): DataLoaderRegistry {
        val registry = DataLoaderRegistry()
        registry.register(
            GroupMembersDataLoader.KEY,
            GroupMembersDataLoader.create(groupManagementResolver),
        )
        registry.register(
            UserRolesDataLoader.KEY,
            UserRolesDataLoader.create(userManagementResolver),
        )
        registry.register(
            UserGroupsDataLoader.KEY,
            UserGroupsDataLoader.create(userManagementResolver),
        )
        registry.register(
            UserPermissionsDataLoader.KEY,
            UserPermissionsDataLoader.create(userManagementResolver),
        )
        registry.register(
            RolePermissionsDataLoader.KEY,
            RolePermissionsDataLoader.create(roleManagementResolver),
        )
        registry.register(
            RoleGroupsDataLoader.KEY,
            RoleGroupsDataLoader.create(roleManagementResolver),
        )
        registry.register(
            GroupRolesDataLoader.KEY,
            GroupRolesDataLoader.create(groupManagementResolver),
        )
        registry.register(
            PermissionRolesDataLoader.KEY,
            PermissionRolesDataLoader.create(permissionManagementResolver),
        )
        return registry
    }
}
