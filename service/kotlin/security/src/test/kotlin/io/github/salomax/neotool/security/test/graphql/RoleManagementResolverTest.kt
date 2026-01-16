package io.github.salomax.neotool.security.test.graphql

import io.github.salomax.neotool.common.graphql.pagination.ConnectionBuilder
import io.github.salomax.neotool.common.graphql.pagination.PaginationConstants
import io.github.salomax.neotool.security.domain.rbac.Group
import io.github.salomax.neotool.security.domain.rbac.Permission
import io.github.salomax.neotool.security.domain.rbac.Role
import io.github.salomax.neotool.security.graphql.dto.CreateRoleInputDTO
import io.github.salomax.neotool.security.graphql.dto.GroupDTO
import io.github.salomax.neotool.security.graphql.dto.PermissionDTO
import io.github.salomax.neotool.security.graphql.dto.RoleConnectionDTO
import io.github.salomax.neotool.security.graphql.dto.RoleDTO
import io.github.salomax.neotool.security.graphql.dto.UpdateRoleInputDTO
import io.github.salomax.neotool.security.graphql.mapper.RoleManagementMapper
import io.github.salomax.neotool.security.graphql.resolver.RoleManagementResolver
import io.github.salomax.neotool.security.service.management.RoleManagementService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

@DisplayName("RoleManagementResolver Tests")
class RoleManagementResolverTest {
    private lateinit var roleManagementService: RoleManagementService
    private lateinit var mapper: RoleManagementMapper
    private lateinit var roleManagementResolver: RoleManagementResolver

    @BeforeEach
    fun setUp() {
        roleManagementService = mock()
        mapper = mock()
        roleManagementResolver = RoleManagementResolver(roleManagementService, mapper)
    }

    @Nested
    @DisplayName("resolveRolePermissionsBatch")
    inner class ResolveRolePermissionsBatchTests {
        @Test
        fun `should return permissions for multiple roles`() {
            // Arrange
            val roleId1 = UUID.randomUUID()
            val roleId2 = UUID.randomUUID()
            val permission1 =
                Permission(
                    id = UUID.randomUUID(),
                    name = "transaction:read",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            val permission2 =
                Permission(
                    id = UUID.randomUUID(),
                    name = "transaction:write",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            whenever(mapper.toRoleId(roleId1.toString())).thenReturn(roleId1)
            whenever(mapper.toRoleId(roleId2.toString())).thenReturn(roleId2)
            whenever(roleManagementService.listRolePermissionsBatch(any<List<UUID>>()))
                .thenReturn(
                    mapOf(
                        roleId1 to listOf(permission1),
                        roleId2 to listOf(permission2),
                    ),
                )
            whenever(mapper.toPermissionDTOList(any<List<Permission>>())).thenAnswer { invocation ->
                val permissions = invocation.getArgument<List<Permission>>(0)
                permissions.map { PermissionDTO(id = it.id!!.toString(), name = it.name) }
            }

            // Act
            val result =
                roleManagementResolver.resolveRolePermissionsBatch(
                    listOf(
                        roleId1.toString(),
                        roleId2.toString(),
                    ),
                )

            // Assert
            assertThat(result).hasSize(2)
            assertThat(result[roleId1.toString()]).hasSize(1)
            assertThat(result[roleId1.toString()]!![0].name).isEqualTo("transaction:read")
            assertThat(result[roleId2.toString()]).hasSize(1)
            assertThat(result[roleId2.toString()]!![0].name).isEqualTo("transaction:write")
            verify(roleManagementService).listRolePermissionsBatch(any<List<UUID>>())
        }

        @Test
        fun `should return empty map for empty role list`() {
            // Act
            val result = roleManagementResolver.resolveRolePermissionsBatch(emptyList())

            // Assert
            assertThat(result).isEmpty()
        }

        @Test
        fun `should filter out invalid role IDs`() {
            // Arrange
            val roleId1 = UUID.randomUUID()
            val invalidId = "not-a-uuid"
            val permission1 =
                Permission(
                    id = UUID.randomUUID(),
                    name = "transaction:read",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            whenever(mapper.toRoleId(roleId1.toString())).thenReturn(roleId1)
            whenever(mapper.toRoleId(invalidId)).thenThrow(IllegalArgumentException("Invalid role ID"))
            whenever(roleManagementService.listRolePermissionsBatch(any<List<UUID>>()))
                .thenReturn(mapOf(roleId1 to listOf(permission1)))
            whenever(mapper.toPermissionDTOList(any<List<Permission>>())).thenAnswer { invocation ->
                val permissions = invocation.getArgument<List<Permission>>(0)
                permissions.map { PermissionDTO(id = it.id!!.toString(), name = it.name) }
            }

            // Act
            val result = roleManagementResolver.resolveRolePermissionsBatch(listOf(roleId1.toString(), invalidId))

            // Assert
            assertThat(result).hasSize(1)
            assertThat(result[roleId1.toString()]).hasSize(1)
            assertThat(result).doesNotContainKey(invalidId)
        }

        @Test
        fun `should preserve order of requested role IDs`() {
            // Arrange
            val roleId1 = UUID.randomUUID()
            val roleId2 = UUID.randomUUID()
            val roleId3 = UUID.randomUUID()
            val permission1 =
                Permission(
                    id = UUID.randomUUID(),
                    name = "transaction:read",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            whenever(mapper.toRoleId(roleId1.toString())).thenReturn(roleId1)
            whenever(mapper.toRoleId(roleId2.toString())).thenReturn(roleId2)
            whenever(mapper.toRoleId(roleId3.toString())).thenReturn(roleId3)
            whenever(roleManagementService.listRolePermissionsBatch(any<List<UUID>>()))
                .thenReturn(
                    mapOf(
                        roleId1 to listOf(permission1),
                        roleId2 to emptyList(),
                        roleId3 to listOf(permission1),
                    ),
                )
            whenever(mapper.toPermissionDTOList(any<List<Permission>>())).thenAnswer { invocation ->
                val permissions = invocation.getArgument<List<Permission>>(0)
                permissions.map { PermissionDTO(id = it.id!!.toString(), name = it.name) }
            }

            // Act
            val result =
                roleManagementResolver.resolveRolePermissionsBatch(
                    listOf(roleId1.toString(), roleId2.toString(), roleId3.toString()),
                )

            // Assert
            assertThat(result.keys.toList())
                .containsExactly(
                    roleId1.toString(),
                    roleId2.toString(),
                    roleId3.toString(),
                )
        }
    }

    @Nested
    @DisplayName("role() - Single Role Query")
    inner class SingleRoleQueryTests {
        @Test
        fun `should return role when found`() {
            // Arrange
            val roleId = UUID.randomUUID()
            val role =
                Role(
                    id = roleId,
                    name = "admin",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            val roleDTO = RoleDTO(id = roleId.toString(), name = "admin")

            whenever(mapper.toRoleId(roleId.toString())).thenReturn(roleId)
            whenever(roleManagementService.getRoleById(roleId)).thenReturn(role)
            whenever(mapper.toRoleDTO(role)).thenReturn(roleDTO)

            // Act
            val result = roleManagementResolver.role(roleId.toString())

            // Assert
            assertThat(result).isNotNull
            assertThat(result?.id).isEqualTo(roleId.toString())
            assertThat(result?.name).isEqualTo("admin")
            verify(roleManagementService).getRoleById(roleId)
        }

        @Test
        fun `should return null when role not found`() {
            // Arrange
            val roleId = UUID.randomUUID()

            whenever(mapper.toRoleId(roleId.toString())).thenReturn(roleId)
            whenever(roleManagementService.getRoleById(roleId)).thenReturn(null)

            // Act
            val result = roleManagementResolver.role(roleId.toString())

            // Assert
            assertThat(result).isNull()
            verify(roleManagementService).getRoleById(roleId)
        }

        @Test
        fun `should throw IllegalArgumentException for invalid role ID`() {
            // Arrange
            val invalidId = "not-a-uuid"

            whenever(mapper.toRoleId(invalidId)).thenThrow(IllegalArgumentException("Invalid UUID format"))

            // Act & Assert
            assertThatThrownBy { roleManagementResolver.role(invalidId) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Invalid role ID format")
        }
    }

    @Nested
    @DisplayName("roles() - List/Search Query")
    inner class RolesQueryTests {
        @Test
        fun `should validate first parameter minimum value`() {
            // Act & Assert
            assertThatThrownBy { roleManagementResolver.roles(first = 0, after = null, query = null, orderBy = null) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Parameter 'first' must be at least 1")
        }

        @Test
        fun `should validate first parameter maximum value`() {
            // Act & Assert
            assertThatThrownBy {
                roleManagementResolver.roles(
                    first = PaginationConstants.MAX_PAGE_SIZE + 1,
                    after = null,
                    query = null,
                    orderBy = null,
                )
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Parameter 'first' must be at most")
        }

        @Test
        fun `should use default page size when first is null`() {
            // Arrange
            val connection =
                ConnectionBuilder.buildConnection<Role>(
                    items = emptyList(),
                    hasMore = false,
                    encodeCursor = { it.id?.toString() ?: "" },
                    totalCount = 0L,
                )
            val pageInfo =
                io.github.salomax.neotool.security.graphql.dto.PageInfoDTO(
                    hasNextPage = false,
                    hasPreviousPage = false,
                )
            val connectionDTO = RoleConnectionDTO(edges = emptyList(), pageInfo = pageInfo, totalCount = 0)

            whenever(mapper.toRoleOrderByList(null)).thenReturn(emptyList())
            whenever(roleManagementService.searchRoles(null, PaginationConstants.DEFAULT_PAGE_SIZE, null, emptyList()))
                .thenReturn(connection)
            whenever(mapper.toRoleConnectionDTO(connection)).thenReturn(connectionDTO)

            // Act
            val result = roleManagementResolver.roles(first = null, after = null, query = null, orderBy = null)

            // Assert
            assertThat(result).isNotNull
            verify(roleManagementService).searchRoles(null, PaginationConstants.DEFAULT_PAGE_SIZE, null, emptyList())
        }

        @Test
        fun `should validate orderBy parameter`() {
            // Arrange
            val invalidOrderBy = listOf(mapOf("field" to "INVALID_FIELD"))

            whenever(mapper.toRoleOrderByList(invalidOrderBy))
                .thenThrow(IllegalArgumentException("Invalid field: INVALID_FIELD"))

            // Act & Assert
            assertThatThrownBy {
                roleManagementResolver.roles(
                    first = 10,
                    after = null,
                    query = null,
                    orderBy = invalidOrderBy,
                )
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Invalid orderBy parameter")
        }
    }

    @Nested
    @DisplayName("CRUD Mutations")
    inner class CrudMutationTests {
        @Test
        fun `createRole should create and return role`() {
            // Arrange
            val input = CreateRoleInputDTO(name = "admin")
            val role =
                Role(
                    id = UUID.randomUUID(),
                    name = "admin",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            val roleDTO = RoleDTO(id = role.id.toString(), name = "admin")

            whenever(mapper.toCreateRoleCommand(input)).thenReturn(
                io.github.salomax.neotool.security.domain.RoleManagement.CreateRoleCommand("admin"),
            )
            whenever(roleManagementService.createRole(any())).thenReturn(role)
            whenever(mapper.toRoleDTO(role)).thenReturn(roleDTO)

            // Act
            val result = roleManagementResolver.createRole(input)

            // Assert
            assertThat(result).isNotNull
            assertThat(result.name).isEqualTo("admin")
            verify(roleManagementService).createRole(any())
        }

        @Test
        fun `updateRole should update and return role`() {
            // Arrange
            val roleId = UUID.randomUUID()
            val input = UpdateRoleInputDTO(name = "updated-admin")
            val role =
                Role(
                    id = roleId,
                    name = "updated-admin",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            val roleDTO = RoleDTO(id = roleId.toString(), name = "updated-admin")

            whenever(mapper.toUpdateRoleCommand(roleId.toString(), input)).thenReturn(
                io.github.salomax.neotool.security.domain.RoleManagement.UpdateRoleCommand(roleId, "updated-admin"),
            )
            whenever(roleManagementService.updateRole(any())).thenReturn(role)
            whenever(mapper.toRoleDTO(role)).thenReturn(roleDTO)

            // Act
            val result = roleManagementResolver.updateRole(roleId.toString(), input)

            // Assert
            assertThat(result).isNotNull
            assertThat(result.name).isEqualTo("updated-admin")
            verify(roleManagementService).updateRole(any())
        }

        @Test
        fun `deleteRole should delete role and return true`() {
            // Arrange
            val roleId = UUID.randomUUID()

            whenever(mapper.toRoleId(roleId.toString())).thenReturn(roleId)
            // deleteRole returns Unit, so no need to mock return value

            // Act
            val result = roleManagementResolver.deleteRole(roleId.toString())

            // Assert
            assertThat(result).isTrue()
            verify(roleManagementService).deleteRole(roleId)
        }

        @Test
        fun `assignPermissionToRole should assign permission and return role`() {
            // Arrange
            val roleId = UUID.randomUUID()
            val permissionId = UUID.randomUUID()
            val role =
                Role(
                    id = roleId,
                    name = "admin",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            val roleDTO = RoleDTO(id = roleId.toString(), name = "admin")

            whenever(mapper.toRoleId(roleId.toString())).thenReturn(roleId)
            whenever(mapper.toPermissionId(permissionId.toString())).thenReturn(permissionId)
            whenever(roleManagementService.assignPermissionToRole(any())).thenReturn(role)
            whenever(mapper.toRoleDTO(role)).thenReturn(roleDTO)

            // Act
            val result = roleManagementResolver.assignPermissionToRole(roleId.toString(), permissionId.toString())

            // Assert
            assertThat(result).isNotNull
            assertThat(result.name).isEqualTo("admin")
            verify(roleManagementService).assignPermissionToRole(any())
        }

        @Test
        fun `removePermissionFromRole should remove permission and return role`() {
            // Arrange
            val roleId = UUID.randomUUID()
            val permissionId = UUID.randomUUID()
            val role =
                Role(
                    id = roleId,
                    name = "admin",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            val roleDTO = RoleDTO(id = roleId.toString(), name = "admin")

            whenever(mapper.toRoleId(roleId.toString())).thenReturn(roleId)
            whenever(mapper.toPermissionId(permissionId.toString())).thenReturn(permissionId)
            whenever(roleManagementService.removePermissionFromRole(any())).thenReturn(role)
            whenever(mapper.toRoleDTO(role)).thenReturn(roleDTO)

            // Act
            val result = roleManagementResolver.removePermissionFromRole(roleId.toString(), permissionId.toString())

            // Assert
            assertThat(result).isNotNull
            assertThat(result.name).isEqualTo("admin")
            verify(roleManagementService).removePermissionFromRole(any())
        }
    }

    @Nested
    @DisplayName("resolveRolePermissions() - Single Role Permissions")
    inner class ResolveRolePermissionsTests {
        @Test
        fun `should return permissions for role`() {
            // Arrange
            val roleId = UUID.randomUUID()
            val permission1 =
                Permission(
                    id = UUID.randomUUID(),
                    name = "transaction:read",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            val permission2 =
                Permission(
                    id = UUID.randomUUID(),
                    name = "transaction:write",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            val permissions = listOf(permission1, permission2)
            val permissionDTOs =
                listOf(
                    PermissionDTO(id = permission1.id!!.toString(), name = "transaction:read"),
                    PermissionDTO(id = permission2.id!!.toString(), name = "transaction:write"),
                )

            whenever(mapper.toRoleId(roleId.toString())).thenReturn(roleId)
            whenever(roleManagementService.listRolePermissions(roleId)).thenReturn(permissions)
            whenever(mapper.toPermissionDTOList(permissions)).thenReturn(permissionDTOs)

            // Act
            val result = roleManagementResolver.resolveRolePermissions(roleId.toString())

            // Assert
            assertThat(result).hasSize(2)
            assertThat(result[0].name).isEqualTo("transaction:read")
            assertThat(result[1].name).isEqualTo("transaction:write")
            verify(roleManagementService).listRolePermissions(roleId)
        }

        @Test
        fun `should throw IllegalArgumentException for invalid role ID`() {
            // Arrange
            val invalidId = "not-a-uuid"

            whenever(mapper.toRoleId(invalidId)).thenThrow(IllegalArgumentException("Invalid UUID format"))

            // Act & Assert
            assertThatThrownBy { roleManagementResolver.resolveRolePermissions(invalidId) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Invalid role ID format")
        }
    }

    @Nested
    @DisplayName("resolveRoleGroups() - Single Role Groups")
    inner class ResolveRoleGroupsTests {
        @Test
        fun `should return groups for role`() {
            // Arrange
            val roleId = UUID.randomUUID()
            val group1 =
                Group(
                    id = UUID.randomUUID(),
                    name = "Engineering",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            val group2 =
                Group(
                    id = UUID.randomUUID(),
                    name = "Product",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            val groups = listOf(group1, group2)
            val groupDTOs =
                listOf(
                    GroupDTO(id = group1.id!!.toString(), name = "Engineering"),
                    GroupDTO(id = group2.id!!.toString(), name = "Product"),
                )

            whenever(mapper.toRoleId(roleId.toString())).thenReturn(roleId)
            whenever(roleManagementService.listRoleGroups(roleId)).thenReturn(groups)
            whenever(mapper.toGroupDTOList(groups)).thenReturn(groupDTOs)

            // Act
            val result = roleManagementResolver.resolveRoleGroups(roleId.toString())

            // Assert
            assertThat(result).hasSize(2)
            assertThat(result[0].name).isEqualTo("Engineering")
            assertThat(result[1].name).isEqualTo("Product")
            verify(roleManagementService).listRoleGroups(roleId)
        }

        @Test
        fun `should throw IllegalArgumentException for invalid role ID`() {
            // Arrange
            val invalidId = "not-a-uuid"

            whenever(mapper.toRoleId(invalidId)).thenThrow(IllegalArgumentException("Invalid UUID format"))

            // Act & Assert
            assertThatThrownBy { roleManagementResolver.resolveRoleGroups(invalidId) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Invalid role ID format")
        }
    }

    @Nested
    @DisplayName("resolveRoleGroupsBatch() - Batch Role Groups")
    inner class ResolveRoleGroupsBatchTests {
        @Test
        fun `should return groups for multiple roles`() {
            // Arrange
            val roleId1 = UUID.randomUUID()
            val roleId2 = UUID.randomUUID()
            val group1 =
                Group(
                    id = UUID.randomUUID(),
                    name = "Engineering",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            val group2 =
                Group(
                    id = UUID.randomUUID(),
                    name = "Product",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            whenever(mapper.toRoleId(roleId1.toString())).thenReturn(roleId1)
            whenever(mapper.toRoleId(roleId2.toString())).thenReturn(roleId2)
            whenever(roleManagementService.listRoleGroupsBatch(any<List<UUID>>()))
                .thenReturn(
                    mapOf(
                        roleId1 to listOf(group1),
                        roleId2 to listOf(group2),
                    ),
                )
            whenever(mapper.toGroupDTOList(any<List<Group>>())).thenAnswer { invocation ->
                val groups = invocation.getArgument<List<Group>>(0)
                groups.map { GroupDTO(id = it.id!!.toString(), name = it.name) }
            }

            // Act
            val result =
                roleManagementResolver.resolveRoleGroupsBatch(
                    listOf(
                        roleId1.toString(),
                        roleId2.toString(),
                    ),
                )

            // Assert
            assertThat(result).hasSize(2)
            assertThat(result[roleId1.toString()]).hasSize(1)
            assertThat(result[roleId1.toString()]!![0].name).isEqualTo("Engineering")
            assertThat(result[roleId2.toString()]).hasSize(1)
            assertThat(result[roleId2.toString()]!![0].name).isEqualTo("Product")
            verify(roleManagementService).listRoleGroupsBatch(any<List<UUID>>())
        }

        @Test
        fun `should return empty map for empty role list`() {
            // Act
            val result = roleManagementResolver.resolveRoleGroupsBatch(emptyList())

            // Assert
            assertThat(result).isEmpty()
        }

        @Test
        fun `should filter out invalid role IDs`() {
            // Arrange
            val roleId1 = UUID.randomUUID()
            val invalidId = "not-a-uuid"
            val group1 =
                Group(
                    id = UUID.randomUUID(),
                    name = "Engineering",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            whenever(mapper.toRoleId(roleId1.toString())).thenReturn(roleId1)
            whenever(mapper.toRoleId(invalidId)).thenThrow(IllegalArgumentException("Invalid role ID"))
            whenever(roleManagementService.listRoleGroupsBatch(any<List<UUID>>()))
                .thenReturn(mapOf(roleId1 to listOf(group1)))
            whenever(mapper.toGroupDTOList(any<List<Group>>())).thenAnswer { invocation ->
                val groups = invocation.getArgument<List<Group>>(0)
                groups.map { GroupDTO(id = it.id!!.toString(), name = it.name) }
            }

            // Act
            val result = roleManagementResolver.resolveRoleGroupsBatch(listOf(roleId1.toString(), invalidId))

            // Assert
            assertThat(result).hasSize(1)
            assertThat(result[roleId1.toString()]).hasSize(1)
            assertThat(result).doesNotContainKey(invalidId)
        }
    }
}
