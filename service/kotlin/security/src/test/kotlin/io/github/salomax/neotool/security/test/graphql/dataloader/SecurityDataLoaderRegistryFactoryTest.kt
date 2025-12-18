package io.github.salomax.neotool.security.test.graphql.dataloader

import io.github.salomax.neotool.security.graphql.dataloader.GroupMembersDataLoader
import io.github.salomax.neotool.security.graphql.dataloader.GroupRolesDataLoader
import io.github.salomax.neotool.security.graphql.dataloader.PermissionRolesDataLoader
import io.github.salomax.neotool.security.graphql.dataloader.RoleGroupsDataLoader
import io.github.salomax.neotool.security.graphql.dataloader.RolePermissionsDataLoader
import io.github.salomax.neotool.security.graphql.dataloader.SecurityDataLoaderRegistryFactory
import io.github.salomax.neotool.security.graphql.dataloader.UserGroupsDataLoader
import io.github.salomax.neotool.security.graphql.dataloader.UserPermissionsDataLoader
import io.github.salomax.neotool.security.graphql.dataloader.UserRolesDataLoader
import io.github.salomax.neotool.security.graphql.resolver.GroupManagementResolver
import io.github.salomax.neotool.security.graphql.resolver.PermissionManagementResolver
import io.github.salomax.neotool.security.graphql.resolver.RoleManagementResolver
import io.github.salomax.neotool.security.graphql.resolver.UserManagementResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

@DisplayName("SecurityDataLoaderRegistryFactory Tests")
class SecurityDataLoaderRegistryFactoryTest {
    private lateinit var groupManagementResolver: GroupManagementResolver
    private lateinit var userManagementResolver: UserManagementResolver
    private lateinit var roleManagementResolver: RoleManagementResolver
    private lateinit var permissionManagementResolver: PermissionManagementResolver
    private lateinit var factory: SecurityDataLoaderRegistryFactory

    @BeforeEach
    fun setUp() {
        groupManagementResolver = mock()
        userManagementResolver = mock()
        roleManagementResolver = mock()
        permissionManagementResolver = mock()
        factory =
            SecurityDataLoaderRegistryFactory(
                groupManagementResolver,
                userManagementResolver,
                roleManagementResolver,
                permissionManagementResolver,
            )
    }

    @Test
    fun `should register all DataLoaders with correct keys`() {
        // Act
        val registry = factory.createDataLoaderRegistry()

        // Assert
        assertThat(registry.keys).hasSize(8)
        assertThat(registry.getDataLoader<String, Any?>(GroupMembersDataLoader.KEY)).isNotNull
        assertThat(registry.getDataLoader<String, Any?>(UserRolesDataLoader.KEY)).isNotNull
        assertThat(registry.getDataLoader<String, Any?>(UserGroupsDataLoader.KEY)).isNotNull
        assertThat(registry.getDataLoader<String, Any?>(UserPermissionsDataLoader.KEY)).isNotNull
        assertThat(registry.getDataLoader<String, Any?>(RolePermissionsDataLoader.KEY)).isNotNull
        assertThat(registry.getDataLoader<String, Any?>(RoleGroupsDataLoader.KEY)).isNotNull
        assertThat(registry.getDataLoader<String, Any?>(GroupRolesDataLoader.KEY)).isNotNull
        assertThat(registry.getDataLoader<String, Any?>(PermissionRolesDataLoader.KEY)).isNotNull
    }

    @Test
    fun `should create DataLoaders with correct resolvers`() {
        // Act
        val registry = factory.createDataLoaderRegistry()

        // Assert - Verify DataLoaders are created (they should not be null)
        assertThat(registry.getDataLoader<String, Any?>(GroupMembersDataLoader.KEY)).isNotNull
        assertThat(registry.getDataLoader<String, Any?>(UserRolesDataLoader.KEY)).isNotNull
        assertThat(registry.getDataLoader<String, Any?>(UserGroupsDataLoader.KEY)).isNotNull
        assertThat(registry.getDataLoader<String, Any?>(UserPermissionsDataLoader.KEY)).isNotNull
        assertThat(registry.getDataLoader<String, Any?>(RolePermissionsDataLoader.KEY)).isNotNull
        assertThat(registry.getDataLoader<String, Any?>(RoleGroupsDataLoader.KEY)).isNotNull
        assertThat(registry.getDataLoader<String, Any?>(GroupRolesDataLoader.KEY)).isNotNull
        assertThat(registry.getDataLoader<String, Any?>(PermissionRolesDataLoader.KEY)).isNotNull
    }

    @Test
    fun `should use correct DataLoader keys`() {
        // Act
        val registry = factory.createDataLoaderRegistry()

        // Assert - Verify keys match constants
        assertThat(registry.keys).contains(GroupMembersDataLoader.KEY)
        assertThat(registry.keys).contains(UserRolesDataLoader.KEY)
        assertThat(registry.keys).contains(UserGroupsDataLoader.KEY)
        assertThat(registry.keys).contains(UserPermissionsDataLoader.KEY)
        assertThat(registry.keys).contains(RolePermissionsDataLoader.KEY)
        assertThat(registry.keys).contains(RoleGroupsDataLoader.KEY)
        assertThat(registry.keys).contains(GroupRolesDataLoader.KEY)
        assertThat(registry.keys).contains(PermissionRolesDataLoader.KEY)
    }
}
