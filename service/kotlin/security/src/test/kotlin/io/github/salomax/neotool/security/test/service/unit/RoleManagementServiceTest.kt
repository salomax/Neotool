package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.common.graphql.pagination.CompositeCursor
import io.github.salomax.neotool.common.graphql.pagination.CursorEncoder
import io.github.salomax.neotool.common.graphql.pagination.OrderDirection
import io.github.salomax.neotool.common.graphql.pagination.PaginationConstants
import io.github.salomax.neotool.security.domain.RoleManagement
import io.github.salomax.neotool.security.repo.GroupRepository
import io.github.salomax.neotool.security.repo.PermissionRepository
import io.github.salomax.neotool.security.repo.RoleRepository
import io.github.salomax.neotool.security.repo.RoleRepositoryCustom
import io.github.salomax.neotool.security.service.management.RoleManagementService
import io.github.salomax.neotool.security.model.RoleOrderBy
import io.github.salomax.neotool.security.model.RoleOrderField
import io.github.salomax.neotool.security.test.SecurityTestDataBuilders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

@DisplayName("RoleManagementService Unit Tests")
class RoleManagementServiceTest {
    private lateinit var roleRepository: RoleRepository
    private lateinit var roleSearchRepository: RoleRepositoryCustom
    private lateinit var permissionRepository: PermissionRepository
    private lateinit var groupRepository: GroupRepository
    private lateinit var roleManagementService: RoleManagementService

    @BeforeEach
    fun setUp() {
        roleRepository = mock()
        roleSearchRepository = mock()
        permissionRepository = mock()
        groupRepository = mock()
        roleManagementService =
            RoleManagementService(roleRepository, roleSearchRepository, permissionRepository, groupRepository)
    }

    @Nested
    @DisplayName("List Roles")
    inner class ListRolesTests {
        @Test
        fun `should list roles with default page size`() {
            // Arrange
            val role1 = SecurityTestDataBuilders.role(id = UUID.randomUUID(), name = "Role One")
            val role2 = SecurityTestDataBuilders.role(id = UUID.randomUUID(), name = "Role Two")
            val defaultOrderBy =
                listOf(
                    RoleOrderBy(RoleOrderField.NAME, OrderDirection.ASC),
                    RoleOrderBy(RoleOrderField.ID, OrderDirection.ASC),
                )
            whenever(
                roleSearchRepository.searchByName(
                    null,
                    PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                    null,
                    defaultOrderBy,
                ),
            )
                .thenReturn(listOf(role1, role2))
            whenever(roleSearchRepository.countByName(null))
                .thenReturn(2L)

            // Act
            val result = roleManagementService.searchRoles(query = null, after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(2)
            assertThat(result.pageInfo.hasNextPage).isFalse()
            verify(
                roleSearchRepository,
            ).searchByName(null, PaginationConstants.DEFAULT_PAGE_SIZE + 1, null, defaultOrderBy)
        }

        @Test
        fun `should list roles with custom page size`() {
            // Arrange
            val first = 10
            val role1 = SecurityTestDataBuilders.role(id = UUID.randomUUID(), name = "Role One")
            val defaultOrderBy =
                listOf(
                    RoleOrderBy(RoleOrderField.NAME, OrderDirection.ASC),
                    RoleOrderBy(RoleOrderField.ID, OrderDirection.ASC),
                )
            whenever(roleSearchRepository.searchByName(null, first + 1, null, defaultOrderBy))
                .thenReturn(listOf(role1))
            whenever(roleSearchRepository.countByName(null))
                .thenReturn(1L)

            // Act
            val result = roleManagementService.searchRoles(query = null, first = first, after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(1)
            verify(roleSearchRepository).searchByName(null, first + 1, null, defaultOrderBy)
        }

        @Test
        fun `should enforce max page size`() {
            // Arrange
            val first = 200 // Exceeds MAX_PAGE_SIZE
            val defaultOrderBy =
                listOf(
                    RoleOrderBy(RoleOrderField.NAME, OrderDirection.ASC),
                    RoleOrderBy(RoleOrderField.ID, OrderDirection.ASC),
                )
            whenever(
                roleSearchRepository.searchByName(
                    null,
                    PaginationConstants.MAX_PAGE_SIZE + 1,
                    null,
                    defaultOrderBy,
                ),
            )
                .thenReturn(emptyList())
            whenever(roleSearchRepository.countByName(null))
                .thenReturn(0L)

            // Act
            val result = roleManagementService.searchRoles(query = null, first = first, after = null)

            // Assert
            assertThat(result).isNotNull()
            verify(roleSearchRepository).searchByName(
                null,
                PaginationConstants.MAX_PAGE_SIZE + 1,
                null,
                defaultOrderBy,
            )
        }

        @Test
        fun `should list roles with cursor pagination using legacy Int cursor`() {
            // Arrange
            val afterId = UUID.randomUUID()
            val afterCursor = CursorEncoder.encodeCursor(afterId)
            val role1 = SecurityTestDataBuilders.role(id = UUID.randomUUID(), name = "Role One")
            val defaultOrderBy =
                listOf(
                    RoleOrderBy(RoleOrderField.NAME, OrderDirection.ASC),
                    RoleOrderBy(RoleOrderField.ID, OrderDirection.ASC),
                )
            val compositeCursor = CompositeCursor(emptyMap(), afterId.toString())
            whenever(
                roleSearchRepository.searchByName(
                    null,
                    PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                    compositeCursor,
                    defaultOrderBy,
                ),
            )
                .thenReturn(listOf(role1))
            whenever(roleSearchRepository.countByName(null))
                .thenReturn(1L)

            // Act
            val result = roleManagementService.searchRoles(query = null, after = afterCursor)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(1)
            verify(
                roleSearchRepository,
            ).searchByName(null, PaginationConstants.DEFAULT_PAGE_SIZE + 1, compositeCursor, defaultOrderBy)
        }

        @Test
        fun `should indicate has more when results exceed page size`() {
            // Arrange
            val roles =
                (1..21).map {
                    SecurityTestDataBuilders.role(
                        id = UUID.randomUUID(),
                        name = "Role $it",
                    )
                }
            val defaultOrderBy =
                listOf(
                    RoleOrderBy(RoleOrderField.NAME, OrderDirection.ASC),
                    RoleOrderBy(RoleOrderField.ID, OrderDirection.ASC),
                )
            whenever(
                roleSearchRepository.searchByName(
                    null,
                    PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                    null,
                    defaultOrderBy,
                ),
            )
                .thenReturn(roles)
            whenever(roleSearchRepository.countByName(null))
                .thenReturn(21L)

            // Act
            val result = roleManagementService.searchRoles(query = null, after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(PaginationConstants.DEFAULT_PAGE_SIZE)
            assertThat(result.pageInfo.hasNextPage).isTrue()
        }

        @Test
        fun `should return empty connection when no roles exist`() {
            // Arrange
            val defaultOrderBy =
                listOf(
                    RoleOrderBy(RoleOrderField.NAME, OrderDirection.ASC),
                    RoleOrderBy(RoleOrderField.ID, OrderDirection.ASC),
                )
            whenever(
                roleSearchRepository.searchByName(
                    null,
                    PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                    null,
                    defaultOrderBy,
                ),
            )
                .thenReturn(emptyList())
            whenever(roleSearchRepository.countByName(null))
                .thenReturn(0L)

            // Act
            val result = roleManagementService.searchRoles(query = null, after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).isEmpty()
            assertThat(result.edges).isEmpty()
            assertThat(result.pageInfo.hasNextPage).isFalse()
        }

        @Test
        fun `should throw exception for invalid cursor`() {
            // Arrange
            val invalidCursor = "invalid-cursor"

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                roleManagementService.searchRoles(query = null, after = invalidCursor)
            }
            verify(roleSearchRepository, never()).searchByName(any(), any(), any(), any())
        }

        @Test
        fun `should use default sort when orderBy is null`() {
            // Arrange
            val role1 = SecurityTestDataBuilders.role(id = UUID.randomUUID(), name = "Alpha")
            val role2 = SecurityTestDataBuilders.role(id = UUID.randomUUID(), name = "Beta")
            val defaultOrderBy =
                listOf(
                    RoleOrderBy(RoleOrderField.NAME, OrderDirection.ASC),
                    RoleOrderBy(RoleOrderField.ID, OrderDirection.ASC),
                )
            whenever(
                roleSearchRepository.searchByName(
                    null,
                    PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                    null,
                    defaultOrderBy,
                ),
            )
                .thenReturn(listOf(role1, role2))
            whenever(roleSearchRepository.countByName(null))
                .thenReturn(2L)

            // Act
            val result = roleManagementService.searchRoles(query = null, after = null, orderBy = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(2)
            verify(
                roleSearchRepository,
            ).searchByName(null, PaginationConstants.DEFAULT_PAGE_SIZE + 1, null, defaultOrderBy)
        }

        @Test
        fun `should sort by NAME DESC when specified`() {
            // Arrange
            val role1 = SecurityTestDataBuilders.role(id = UUID.randomUUID(), name = "Zeta")
            val role2 = SecurityTestDataBuilders.role(id = UUID.randomUUID(), name = "Alpha")
            val orderBy =
                listOf(
                    RoleOrderBy(RoleOrderField.NAME, OrderDirection.DESC),
                    RoleOrderBy(RoleOrderField.ID, OrderDirection.ASC),
                )
            whenever(roleSearchRepository.searchByName(null, PaginationConstants.DEFAULT_PAGE_SIZE + 1, null, orderBy))
                .thenReturn(listOf(role1, role2))
            whenever(roleSearchRepository.countByName(null))
                .thenReturn(2L)

            // Act
            val result =
                roleManagementService.searchRoles(
                    query = null,
                    after = null,
                    orderBy = listOf(RoleOrderBy(RoleOrderField.NAME, OrderDirection.DESC)),
                )

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(2)
            verify(roleSearchRepository).searchByName(null, PaginationConstants.DEFAULT_PAGE_SIZE + 1, null, orderBy)
        }
    }

    @Nested
    @DisplayName("Search Roles")
    inner class SearchRolesTests {
        @Test
        fun `should search roles by name`() {
            // Arrange
            val query = "admin"
            val role1 = SecurityTestDataBuilders.role(id = UUID.randomUUID(), name = "Admin Role")
            val defaultOrderBy =
                listOf(
                    RoleOrderBy(RoleOrderField.NAME, OrderDirection.ASC),
                    RoleOrderBy(RoleOrderField.ID, OrderDirection.ASC),
                )
            whenever(
                roleSearchRepository.searchByName(
                    query,
                    PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                    null,
                    defaultOrderBy,
                ),
            )
                .thenReturn(listOf(role1))
            whenever(roleSearchRepository.countByName(query))
                .thenReturn(1L)

            // Act
            val result = roleManagementService.searchRoles(query, after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(1)
            assertThat(result.edges.first().node.name).isEqualTo("Admin Role")
            assertThat(result.totalCount).isEqualTo(1L)
            verify(
                roleSearchRepository,
            ).searchByName(query, PaginationConstants.DEFAULT_PAGE_SIZE + 1, null, defaultOrderBy)
            verify(roleSearchRepository).countByName(query)
        }

        @Test
        fun `should search roles with pagination`() {
            // Arrange
            val query = "test"
            val afterId = UUID.randomUUID()
            val afterCursor = CursorEncoder.encodeCursor(afterId)
            val role1 = SecurityTestDataBuilders.role(id = UUID.randomUUID(), name = "Test Role")
            val defaultOrderBy =
                listOf(
                    RoleOrderBy(RoleOrderField.NAME, OrderDirection.ASC),
                    RoleOrderBy(RoleOrderField.ID, OrderDirection.ASC),
                )
            val compositeCursor = CompositeCursor(emptyMap(), afterId.toString())
            whenever(
                roleSearchRepository.searchByName(
                    query,
                    PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                    compositeCursor,
                    defaultOrderBy,
                ),
            )
                .thenReturn(listOf(role1))
            whenever(roleSearchRepository.countByName(query))
                .thenReturn(2L)

            // Act
            val result = roleManagementService.searchRoles(query, after = afterCursor)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(1)
            assertThat(result.totalCount).isEqualTo(2L)
            verify(
                roleSearchRepository,
            ).searchByName(query, PaginationConstants.DEFAULT_PAGE_SIZE + 1, compositeCursor, defaultOrderBy)
            verify(roleSearchRepository).countByName(query)
        }

        @Test
        fun `should return empty connection when no roles match search`() {
            // Arrange
            val query = "nonexistent"
            val defaultOrderBy =
                listOf(
                    RoleOrderBy(RoleOrderField.NAME, OrderDirection.ASC),
                    RoleOrderBy(RoleOrderField.ID, OrderDirection.ASC),
                )
            whenever(
                roleSearchRepository.searchByName(
                    query,
                    PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                    null,
                    defaultOrderBy,
                ),
            )
                .thenReturn(emptyList())
            whenever(roleSearchRepository.countByName(query))
                .thenReturn(0L)

            // Act
            val result = roleManagementService.searchRoles(query, after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).isEmpty()
            assertThat(result.pageInfo.hasNextPage).isFalse()
            assertThat(result.totalCount).isEqualTo(0L)
            verify(roleSearchRepository).countByName(query)
        }

        @Test
        fun `should throw exception for invalid cursor in search`() {
            // Arrange
            val query = "test"
            val invalidCursor = "invalid-cursor"

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                roleManagementService.searchRoles(query, after = invalidCursor)
            }
            verify(roleSearchRepository, never()).searchByName(any(), any(), any(), any())
        }
    }

    @Nested
    @DisplayName("Create Role")
    inner class CreateRoleTests {
        @Test
        fun `should create role successfully`() {
            // Arrange
            val command = RoleManagement.CreateRoleCommand(name = "New Role")
            val savedEntity = SecurityTestDataBuilders.role(id = UUID.randomUUID(), name = "New Role")
            whenever(roleRepository.save(any())).thenReturn(savedEntity)

            // Act
            val result = roleManagementService.createRole(command)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.name).isEqualTo("New Role")
            verify(roleRepository).save(any())
        }
    }

    @Nested
    @DisplayName("Update Role")
    inner class UpdateRoleTests {
        @Test
        fun `should update role successfully`() {
            // Arrange
            val roleId = UUID.randomUUID()
            val existingEntity = SecurityTestDataBuilders.role(id = roleId, name = "Old Name")
            val command = RoleManagement.UpdateRoleCommand(roleId = roleId, name = "New Name")
            val updatedEntity = SecurityTestDataBuilders.role(id = roleId, name = "New Name")
            whenever(roleRepository.findById(roleId)).thenReturn(Optional.of(existingEntity))
            whenever(roleRepository.update(any())).thenReturn(updatedEntity)

            // Act
            val result = roleManagementService.updateRole(command)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.id).isEqualTo(roleId)
            assertThat(result.name).isEqualTo("New Name")
            verify(roleRepository).findById(roleId)
            verify(roleRepository).update(any())
        }

        @Test
        fun `should throw exception when role not found`() {
            // Arrange
            val roleId = UUID.randomUUID()
            val command = RoleManagement.UpdateRoleCommand(roleId = roleId, name = "New Name")
            whenever(roleRepository.findById(roleId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                roleManagementService.updateRole(command)
            }.also { exception ->
                assertThat(exception.message).contains("Role not found")
            }
            verify(roleRepository).findById(roleId)
            verify(roleRepository, never()).update(any())
        }
    }

    @Nested
    @DisplayName("Delete Role")
    inner class DeleteRoleTests {
        @Test
        fun `should delete role successfully`() {
            // Arrange
            val roleId = UUID.randomUUID()

            // Act
            roleManagementService.deleteRole(roleId)

            // Assert
            verify(roleRepository).deleteById(roleId)
        }
    }

    @Nested
    @DisplayName("List Role Permissions")
    inner class ListRolePermissionsTests {
        @Test
        fun `should list permissions for role`() {
            // Arrange
            val roleId = UUID.randomUUID()
            val permission1 = SecurityTestDataBuilders.permission(id = UUID.randomUUID(), name = "permission:read")
            val permission2 = SecurityTestDataBuilders.permission(id = UUID.randomUUID(), name = "permission:write")
            whenever(permissionRepository.findByRoleId(roleId))
                .thenReturn(listOf(permission1, permission2))

            // Act
            val result = roleManagementService.listRolePermissions(roleId)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result).hasSize(2)
            assertThat(result.map { it.name }).containsExactlyInAnyOrder("permission:read", "permission:write")
            verify(permissionRepository).findByRoleId(roleId)
        }

        @Test
        fun `should return empty list when role has no permissions`() {
            // Arrange
            val roleId = UUID.randomUUID()
            whenever(permissionRepository.findByRoleId(roleId))
                .thenReturn(emptyList())

            // Act
            val result = roleManagementService.listRolePermissions(roleId)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result).isEmpty()
            verify(permissionRepository).findByRoleId(roleId)
        }
    }

    @Nested
    @DisplayName("Assign Permission to Role")
    inner class AssignPermissionToRoleTests {
        @Test
        fun `should assign permission to role successfully`() {
            // Arrange
            val roleId = UUID.randomUUID()
            val permissionId = UUID.randomUUID()
            val command = RoleManagement.AssignPermissionCommand(roleId = roleId, permissionId = permissionId)
            val roleEntity = SecurityTestDataBuilders.role(id = roleId, name = "Admin Role")
            val permissionEntity = SecurityTestDataBuilders.permission(id = permissionId, name = "permission:read")
            whenever(roleRepository.findById(roleId)).thenReturn(Optional.of(roleEntity))
            whenever(permissionRepository.findById(permissionId)).thenReturn(Optional.of(permissionEntity))

            // Act
            val result = roleManagementService.assignPermissionToRole(command)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.id).isEqualTo(roleId)
            verify(roleRepository).assignPermissionToRole(roleId, permissionId)
            verify(roleRepository).findById(roleId)
            verify(permissionRepository).findById(permissionId)
        }

        @Test
        fun `should throw exception when role not found`() {
            // Arrange
            val roleId = UUID.randomUUID()
            val permissionId = UUID.randomUUID()
            val command = RoleManagement.AssignPermissionCommand(roleId = roleId, permissionId = permissionId)
            whenever(roleRepository.findById(roleId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                roleManagementService.assignPermissionToRole(command)
            }.also { exception ->
                assertThat(exception.message).contains("Role not found")
            }
            verify(roleRepository).findById(roleId)
            verify(roleRepository, never()).assignPermissionToRole(any(), any())
        }

        @Test
        fun `should throw exception when permission not found`() {
            // Arrange
            val roleId = UUID.randomUUID()
            val permissionId = UUID.randomUUID()
            val command = RoleManagement.AssignPermissionCommand(roleId = roleId, permissionId = permissionId)
            val roleEntity = SecurityTestDataBuilders.role(id = roleId, name = "Admin Role")
            whenever(roleRepository.findById(roleId)).thenReturn(Optional.of(roleEntity))
            whenever(permissionRepository.findById(permissionId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                roleManagementService.assignPermissionToRole(command)
            }.also { exception ->
                assertThat(exception.message).contains("Permission not found")
            }
            verify(roleRepository).findById(roleId)
            verify(permissionRepository).findById(permissionId)
            verify(roleRepository, never()).assignPermissionToRole(any(), any())
        }
    }

    @Nested
    @DisplayName("Remove Permission from Role")
    inner class RemovePermissionFromRoleTests {
        @Test
        fun `should remove permission from role successfully`() {
            // Arrange
            val roleId = UUID.randomUUID()
            val permissionId = UUID.randomUUID()
            val command = RoleManagement.RemovePermissionCommand(roleId = roleId, permissionId = permissionId)
            val roleEntity = SecurityTestDataBuilders.role(id = roleId, name = "Admin Role")
            val permissionEntity = SecurityTestDataBuilders.permission(id = permissionId, name = "permission:read")
            whenever(roleRepository.findById(roleId)).thenReturn(Optional.of(roleEntity))
            whenever(permissionRepository.findById(permissionId)).thenReturn(Optional.of(permissionEntity))

            // Act
            val result = roleManagementService.removePermissionFromRole(command)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.id).isEqualTo(roleId)
            verify(roleRepository).removePermissionFromRole(roleId, permissionId)
            verify(roleRepository).findById(roleId)
            verify(permissionRepository).findById(permissionId)
        }

        @Test
        fun `should throw exception when role not found`() {
            // Arrange
            val roleId = UUID.randomUUID()
            val permissionId = UUID.randomUUID()
            val command = RoleManagement.RemovePermissionCommand(roleId = roleId, permissionId = permissionId)
            whenever(roleRepository.findById(roleId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                roleManagementService.removePermissionFromRole(command)
            }.also { exception ->
                assertThat(exception.message).contains("Role not found")
            }
            verify(roleRepository).findById(roleId)
            verify(roleRepository, never()).removePermissionFromRole(any(), any())
        }

        @Test
        fun `should throw exception when permission not found`() {
            // Arrange
            val roleId = UUID.randomUUID()
            val permissionId = UUID.randomUUID()
            val command = RoleManagement.RemovePermissionCommand(roleId = roleId, permissionId = permissionId)
            val roleEntity = SecurityTestDataBuilders.role(id = roleId, name = "Admin Role")
            whenever(roleRepository.findById(roleId)).thenReturn(Optional.of(roleEntity))
            whenever(permissionRepository.findById(permissionId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                roleManagementService.removePermissionFromRole(command)
            }.also { exception ->
                assertThat(exception.message).contains("Permission not found")
            }
            verify(roleRepository).findById(roleId)
            verify(permissionRepository).findById(permissionId)
            verify(roleRepository, never()).removePermissionFromRole(any(), any())
        }
    }

    @Nested
    @DisplayName("Batch Methods Tests")
    inner class BatchMethodsTests {
        @Test
        fun `listRolePermissionsBatch should return permissions for multiple roles`() {
            // Arrange
            val roleId1 = UUID.randomUUID()
            val roleId2 = UUID.randomUUID()
            val permissionId1 = UUID.randomUUID()
            val permissionId2 = UUID.randomUUID()
            val permission1 =
                SecurityTestDataBuilders.permission(
                    id = permissionId1,
                    name = "transaction:read",
                )
            val permission2 =
                SecurityTestDataBuilders.permission(
                    id = permissionId2,
                    name = "transaction:write",
                )

            whenever(roleRepository.findPermissionIdsByRoleIds(any<List<UUID>>()))
                .thenReturn(listOf(permissionId1, permissionId2))
            whenever(roleRepository.findPermissionIdsByRoleId(roleId1)).thenReturn(listOf(permissionId1))
            whenever(roleRepository.findPermissionIdsByRoleId(roleId2)).thenReturn(listOf(permissionId2))
            whenever(permissionRepository.findByIdIn(any<List<UUID>>())).thenReturn(listOf(permission1, permission2))

            // Act
            val result = roleManagementService.listRolePermissionsBatch(listOf(roleId1, roleId2))

            // Assert
            assertThat(result).hasSize(2)
            assertThat(result[roleId1]).hasSize(1)
            assertThat(result[roleId1]!![0].name).isEqualTo("transaction:read")
            assertThat(result[roleId2]).hasSize(1)
            assertThat(result[roleId2]!![0].name).isEqualTo("transaction:write")
            verify(roleRepository).findPermissionIdsByRoleIds(any<List<UUID>>())
        }

        @Test
        fun `listRolePermissionsBatch should return empty map for empty role list`() {
            // Act
            val result = roleManagementService.listRolePermissionsBatch(emptyList())

            // Assert
            assertThat(result).isEmpty()
        }

        @Test
        fun `listRolePermissionsBatch should return empty lists for roles with no permissions`() {
            // Arrange
            val roleId = UUID.randomUUID()
            whenever(roleRepository.findPermissionIdsByRoleIds(any<List<UUID>>())).thenReturn(emptyList())
            whenever(roleRepository.findPermissionIdsByRoleId(roleId)).thenReturn(emptyList())

            // Act
            val result = roleManagementService.listRolePermissionsBatch(listOf(roleId))

            // Assert
            assertThat(result).hasSize(1)
            assertThat(result[roleId]).isEmpty()
        }

        @Test
        fun `listRolePermissionsBatch should handle roles with multiple permissions`() {
            // Arrange
            val roleId = UUID.randomUUID()
            val permissionId1 = UUID.randomUUID()
            val permissionId2 = UUID.randomUUID()
            val permission1 =
                SecurityTestDataBuilders.permission(
                    id = permissionId1,
                    name = "transaction:read",
                )
            val permission2 =
                SecurityTestDataBuilders.permission(
                    id = permissionId2,
                    name = "transaction:write",
                )

            whenever(roleRepository.findPermissionIdsByRoleIds(any<List<UUID>>()))
                .thenReturn(listOf(permissionId1, permissionId2))
            whenever(roleRepository.findPermissionIdsByRoleId(roleId)).thenReturn(listOf(permissionId1, permissionId2))
            whenever(permissionRepository.findByIdIn(any<List<UUID>>())).thenReturn(listOf(permission1, permission2))

            // Act
            val result = roleManagementService.listRolePermissionsBatch(listOf(roleId))

            // Assert
            assertThat(result).hasSize(1)
            assertThat(result[roleId]).hasSize(2)
            assertThat(
                result[roleId]!!.map { it.name },
            ).containsExactlyInAnyOrder("transaction:read", "transaction:write")
        }
    }

    @Nested
    @DisplayName("Role Groups Tests")
    inner class RoleGroupsTests {
        @Test
        fun `listRoleGroups should return groups assigned to role`() {
            val roleId = UUID.randomUUID()
            val groupId = UUID.randomUUID()
            val groupEntity =
                SecurityTestDataBuilders.group(
                    id = groupId,
                    name = "Admins",
                    description = "Admin group",
                )

            whenever(roleRepository.findGroupIdsByRoleId(roleId)).thenReturn(listOf(groupId))
            whenever(groupRepository.findByIdIn(listOf(groupId))).thenReturn(listOf(groupEntity))

            val result = roleManagementService.listRoleGroups(roleId)

            assertThat(result).hasSize(1)
            assertThat(result.first().id).isEqualTo(groupId)
            assertThat(result.first().name).isEqualTo("Admins")
        }

        @Test
        fun `listRoleGroupsBatch should return groups for multiple roles`() {
            val roleId1 = UUID.randomUUID()
            val roleId2 = UUID.randomUUID()
            val groupId1 = UUID.randomUUID()
            val groupId2 = UUID.randomUUID()

            val group1 = SecurityTestDataBuilders.group(id = groupId1, name = "Admins")
            val group2 = SecurityTestDataBuilders.group(id = groupId2, name = "Editors")

            whenever(roleRepository.findGroupIdsByRoleIds(listOf(roleId1, roleId2)))
                .thenReturn(listOf(groupId1, groupId2))
            whenever(roleRepository.findGroupIdsByRoleId(roleId1)).thenReturn(listOf(groupId1))
            whenever(roleRepository.findGroupIdsByRoleId(roleId2)).thenReturn(listOf(groupId2))
            whenever(groupRepository.findByIdIn(any<List<UUID>>())).thenReturn(listOf(group1, group2))

            val result = roleManagementService.listRoleGroupsBatch(listOf(roleId1, roleId2))

            assertThat(result).hasSize(2)
            assertThat(result[roleId1]).extracting("id").containsExactly(groupId1)
            assertThat(result[roleId2]).extracting("id").containsExactly(groupId2)
        }
    }
}
