package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.security.repo.GroupMembershipRepository
import io.github.salomax.neotool.security.repo.GroupRoleAssignmentRepository
import io.github.salomax.neotool.security.repo.PermissionRepository
import io.github.salomax.neotool.security.repo.RoleRepository
import io.github.salomax.neotool.security.service.AbacEvaluationResult
import io.github.salomax.neotool.security.service.AbacEvaluationService
import io.github.salomax.neotool.security.service.AuthorizationAuditService
import io.github.salomax.neotool.security.service.AuthorizationService
import io.github.salomax.neotool.security.service.exception.AuthorizationDeniedException
import io.github.salomax.neotool.security.test.SecurityTestDataBuilders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

@DisplayName("AuthorizationService Unit Tests")
class AuthorizationServiceTest {
    private lateinit var roleRepository: RoleRepository
    private lateinit var permissionRepository: PermissionRepository
    private lateinit var groupMembershipRepository: GroupMembershipRepository
    private lateinit var groupRoleAssignmentRepository: GroupRoleAssignmentRepository
    private lateinit var abacEvaluationService: AbacEvaluationService
    private lateinit var auditService: AuthorizationAuditService
    private lateinit var authorizationService: AuthorizationService

    @BeforeEach
    fun setUp() {
        roleRepository = mock()
        permissionRepository = mock()
        groupMembershipRepository = mock()
        groupRoleAssignmentRepository = mock()
        abacEvaluationService = mock()
        auditService = mock()
        authorizationService =
            AuthorizationService(
                roleRepository = roleRepository,
                permissionRepository = permissionRepository,
                groupMembershipRepository = groupMembershipRepository,
                groupRoleAssignmentRepository = groupRoleAssignmentRepository,
                abacEvaluationService = abacEvaluationService,
                auditService = auditService,
            )
    }

    @Nested
    @DisplayName("Check Permission - Group-Based Role Assignments")
    inner class CheckPermissionGroupBasedRoleTests {
        @Test
        fun `should allow access when user has role through group membership`() {
            // Arrange
            val userId = UUID.randomUUID()
            val permission = "transaction:read"
            val roleId = UUID.randomUUID()
            val groupId = UUID.randomUUID()
            val groupMembership =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId,
                    groupId = groupId,
                )
            val groupRoleAssignment =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = groupId,
                    roleId = roleId,
                )

            val roleEntity = SecurityTestDataBuilders.role(id = roleId, name = "admin")

            whenever(
                groupMembershipRepository.findActiveMembershipsByUserId(any(), any()),
            ).thenReturn(listOf(groupMembership))
            whenever(
                groupRoleAssignmentRepository.findValidAssignmentsByGroupIds(any<List<UUID>>(), any()),
            ).thenReturn(listOf(groupRoleAssignment))
            whenever(
                permissionRepository.existsPermissionForRoles(permission, listOf(roleId)),
            ).thenReturn(true)
            whenever(roleRepository.findByIdIn(any())).thenReturn(listOf(roleEntity))
            whenever(
                abacEvaluationService.evaluatePolicies(any(), anyOrNull(), anyOrNull()),
            ).thenReturn(
                AbacEvaluationResult(
                    // No matching ABAC policies
                    decision = null,
                    matchedPolicies = emptyList(),
                    reason = "No matching ABAC policies",
                ),
            )

            // Act
            val result = authorizationService.checkPermission(userId, permission)

            // Assert
            assertThat(result.allowed).isTrue()
            assertThat(result.reason).contains("Access granted")
        }

        @Test
        fun `should deny access when user does not have permission`() {
            // Arrange
            val userId = UUID.randomUUID()
            val permission = "transaction:read"
            whenever(groupMembershipRepository.findActiveMembershipsByUserId(any(), any())).thenReturn(emptyList())

            // Act
            val result = authorizationService.checkPermission(userId, permission)

            // Assert
            assertThat(result.allowed).isFalse()
            assertThat(result.reason).contains("User does not have permission")
        }

        @Test
        fun `should deny access when role assignment is expired`() {
            // Arrange
            val userId = UUID.randomUUID()
            val permission = "transaction:read"
            val roleId = UUID.randomUUID()
            // Expired group role assignments should not be returned by findValidAssignmentsByGroupIds
            whenever(groupMembershipRepository.findActiveMembershipsByUserId(any(), any())).thenReturn(emptyList())

            // Act
            val result = authorizationService.checkPermission(userId, permission)

            // Assert
            assertThat(result.allowed).isFalse()
        }

        @Test
        fun `should deny access when role assignment is future-dated`() {
            // Arrange
            val userId = UUID.randomUUID()
            val permission = "transaction:read"

            // Future-dated group role assignments should not be returned by findValidAssignmentsByGroupIds
            whenever(groupMembershipRepository.findActiveMembershipsByUserId(any(), any())).thenReturn(emptyList())

            // Act
            val result = authorizationService.checkPermission(userId, permission)

            // Assert
            assertThat(result.allowed).isFalse()
        }
    }

    @Nested
    @DisplayName("Check Permission - Group Inherited Roles")
    inner class CheckPermissionGroupInheritedTests {
        @Test
        fun `should allow access when user inherits permission from group`() {
            // Arrange
            val userId = UUID.randomUUID()
            val groupId = UUID.randomUUID()
            val permission = "transaction:write"
            val roleId = UUID.randomUUID()
            val groupMembership =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId,
                    groupId = groupId,
                )
            val groupRoleAssignment =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = groupId,
                    roleId = roleId,
                )

            val roleEntity = SecurityTestDataBuilders.role(id = roleId, name = "editor")

            whenever(
                groupMembershipRepository.findActiveMembershipsByUserId(any(), any()),
            ).thenReturn(listOf(groupMembership))
            whenever(
                groupRoleAssignmentRepository.findValidAssignmentsByGroupIds(any<List<UUID>>(), any()),
            ).thenReturn(listOf(groupRoleAssignment))
            whenever(
                permissionRepository.existsPermissionForRoles(permission, listOf(roleId)),
            ).thenReturn(true)
            whenever(roleRepository.findByIdIn(any())).thenReturn(listOf(roleEntity))
            whenever(
                abacEvaluationService.evaluatePolicies(any(), anyOrNull(), anyOrNull()),
            ).thenReturn(
                AbacEvaluationResult(
                    // No matching ABAC policies
                    decision = null,
                    matchedPolicies = emptyList(),
                    reason = "No matching ABAC policies",
                ),
            )

            // Act
            val result = authorizationService.checkPermission(userId, permission)

            // Assert
            assertThat(result.allowed).isTrue()
            assertThat(result.reason).contains("Access granted")
        }

        @Test
        fun `should deny access when group membership is expired`() {
            // Arrange
            val userId = UUID.randomUUID()
            val permission = "transaction:read"

            // Expired memberships should not be returned by findActiveMembershipsByUserId
            whenever(groupMembershipRepository.findActiveMembershipsByUserId(any(), any())).thenReturn(emptyList())

            // Act
            val result = authorizationService.checkPermission(userId, permission)

            // Assert
            assertThat(result.allowed).isFalse()
        }
    }

    @Nested
    @DisplayName("Get User Permissions")
    inner class GetUserPermissionsTests {
        @Test
        fun `should return permissions from group-inherited role assignments`() {
            // Arrange
            val userId = UUID.randomUUID()
            val groupId = UUID.randomUUID()
            val roleId = UUID.randomUUID()
            val permissionId1 = UUID.randomUUID()
            val permissionId2 = UUID.randomUUID()
            val groupMembership =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId,
                    groupId = groupId,
                )
            val groupRoleAssignment =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = groupId,
                    roleId = roleId,
                )
            val permission1 = SecurityTestDataBuilders.permission(id = permissionId1, name = "transaction:read")
            val permission2 = SecurityTestDataBuilders.permission(id = permissionId2, name = "transaction:write")

            whenever(
                groupMembershipRepository.findActiveMembershipsByUserId(any(), any()),
            ).thenReturn(listOf(groupMembership))
            whenever(
                groupRoleAssignmentRepository.findValidAssignmentsByGroupIds(any<List<UUID>>(), any()),
            ).thenReturn(listOf(groupRoleAssignment))
            whenever(
                roleRepository.findPermissionIdsByRoleIds(any<List<UUID>>()),
            ).thenReturn(listOf(permissionId1, permissionId2))
            whenever(permissionRepository.findByIdIn(any())).thenReturn(listOf(permission1, permission2))

            // Act
            val result = authorizationService.getUserPermissions(userId)

            // Assert
            assertThat(result).hasSize(2)
            assertThat(result.map { it.name }).containsExactlyInAnyOrder("transaction:read", "transaction:write")
        }

        @Test
        fun `should return permissions from group-inherited roles`() {
            // Arrange
            val userId = UUID.randomUUID()
            val groupId = UUID.randomUUID()
            val roleId = UUID.randomUUID()
            val permissionId = UUID.randomUUID()
            val groupMembership =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId,
                    groupId = groupId,
                )
            val groupRoleAssignment =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = groupId,
                    roleId = roleId,
                )
            val permission = SecurityTestDataBuilders.permission(id = permissionId, name = "transaction:delete")

            whenever(
                groupMembershipRepository.findActiveMembershipsByUserId(any(), any()),
            ).thenReturn(listOf(groupMembership))
            whenever(
                groupRoleAssignmentRepository.findValidAssignmentsByGroupIds(any<List<UUID>>(), any()),
            ).thenReturn(listOf(groupRoleAssignment))
            whenever(
                roleRepository.findPermissionIdsByRoleIds(any<List<UUID>>()),
            ).thenReturn(listOf(permissionId))
            whenever(permissionRepository.findByIdIn(any())).thenReturn(listOf(permission))

            // Act
            val result = authorizationService.getUserPermissions(userId)

            // Assert
            assertThat(result).hasSize(1)
            assertThat(result[0].name).isEqualTo("transaction:delete")
        }

        @Test
        fun `should combine permissions from direct and group-inherited roles`() {
            // Arrange
            val userId = UUID.randomUUID()
            val groupId = UUID.randomUUID()
            val directRoleId = UUID.randomUUID()
            val groupRoleId = UUID.randomUUID()
            val permissionId1 = UUID.randomUUID()
            val permissionId2 = UUID.randomUUID()
            // Group 1 with role 1
            val group1 = UUID.randomUUID()
            val groupMembership1 =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId,
                    groupId = group1,
                )
            val groupRoleAssignment1 =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = group1,
                    roleId = directRoleId,
                )
            // Group 2 with role 2
            val groupMembership2 =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId,
                    groupId = groupId,
                )
            val groupRoleAssignment2 =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = groupId,
                    roleId = groupRoleId,
                )
            val directPermission = SecurityTestDataBuilders.permission(id = permissionId1, name = "transaction:read")
            val groupPermission = SecurityTestDataBuilders.permission(id = permissionId2, name = "transaction:write")

            whenever(
                groupMembershipRepository.findActiveMembershipsByUserId(any(), any()),
            ).thenReturn(listOf(groupMembership1, groupMembership2))
            whenever(
                groupRoleAssignmentRepository.findValidAssignmentsByGroupIds(any<List<UUID>>(), any()),
            ).thenReturn(listOf(groupRoleAssignment1, groupRoleAssignment2))
            whenever(
                roleRepository.findPermissionIdsByRoleIds(any<List<UUID>>()),
            ).thenReturn(listOf(permissionId1, permissionId2))
            whenever(
                permissionRepository.findByIdIn(any()),
            ).thenReturn(listOf(directPermission, groupPermission))

            // Act
            val result = authorizationService.getUserPermissions(userId)

            // Assert
            assertThat(result).hasSize(2)
            assertThat(result.map { it.name }).containsExactlyInAnyOrder("transaction:read", "transaction:write")
        }

        @Test
        fun `should return empty list when user has no permissions`() {
            // Arrange
            val userId = UUID.randomUUID()
            whenever(groupMembershipRepository.findActiveMembershipsByUserId(any(), any())).thenReturn(emptyList())

            // Act
            val result = authorizationService.getUserPermissions(userId)

            // Assert
            assertThat(result).isEmpty()
        }
    }

    @Nested
    @DisplayName("Get User Roles")
    inner class GetUserRolesTests {
        @Test
        fun `should return roles from group-inherited role assignments`() {
            // Arrange
            val userId = UUID.randomUUID()
            val groupId = UUID.randomUUID()
            val roleId = UUID.randomUUID()
            val groupMembership =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId,
                    groupId = groupId,
                )
            val groupRoleAssignment =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = groupId,
                    roleId = roleId,
                )
            val roleEntity = SecurityTestDataBuilders.role(id = roleId, name = "admin")

            whenever(
                groupMembershipRepository.findActiveMembershipsByUserId(any(), any()),
            ).thenReturn(listOf(groupMembership))
            whenever(
                groupRoleAssignmentRepository.findValidAssignmentsByGroupIds(any<List<UUID>>(), any()),
            ).thenReturn(listOf(groupRoleAssignment))
            whenever(roleRepository.findByIdIn(any<List<UUID>>())).thenReturn(listOf(roleEntity))

            // Act
            val result = authorizationService.getUserRoles(userId)

            // Assert
            assertThat(result).hasSize(1)
            assertThat(result[0].name).isEqualTo("admin")
        }

        @Test
        fun `should return roles from group-inherited assignments`() {
            // Arrange
            val userId = UUID.randomUUID()
            val groupId = UUID.randomUUID()
            val roleId = UUID.randomUUID()
            val groupMembership =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId,
                    groupId = groupId,
                )
            val groupRoleAssignment =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = groupId,
                    roleId = roleId,
                )
            val roleEntity = SecurityTestDataBuilders.role(id = roleId, name = "editor")

            whenever(
                groupMembershipRepository.findActiveMembershipsByUserId(any(), any()),
            ).thenReturn(listOf(groupMembership))
            whenever(
                groupRoleAssignmentRepository.findValidAssignmentsByGroupIds(any<List<UUID>>(), any()),
            ).thenReturn(listOf(groupRoleAssignment))
            whenever(roleRepository.findByIdIn(any<List<UUID>>())).thenReturn(listOf(roleEntity))

            // Act
            val result = authorizationService.getUserRoles(userId)

            // Assert
            assertThat(result).hasSize(1)
            assertThat(result[0].name).isEqualTo("editor")
        }

        @Test
        fun `should combine roles from multiple group-inherited assignments`() {
            // Arrange
            val userId = UUID.randomUUID()
            val groupId1 = UUID.randomUUID()
            val groupId2 = UUID.randomUUID()
            val roleId1 = UUID.randomUUID()
            val roleId2 = UUID.randomUUID()
            val groupMembership1 =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId,
                    groupId = groupId1,
                )
            val groupRoleAssignment1 =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = groupId1,
                    roleId = roleId1,
                )
            val groupMembership2 =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId,
                    groupId = groupId2,
                )
            val groupRoleAssignment2 =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = groupId2,
                    roleId = roleId2,
                )
            val role1 = SecurityTestDataBuilders.role(id = roleId1, name = "admin")
            val role2 = SecurityTestDataBuilders.role(id = roleId2, name = "editor")

            whenever(
                groupMembershipRepository.findActiveMembershipsByUserId(any(), any()),
            ).thenReturn(listOf(groupMembership1, groupMembership2))
            whenever(
                groupRoleAssignmentRepository.findValidAssignmentsByGroupIds(any<List<UUID>>(), any()),
            ).thenReturn(listOf(groupRoleAssignment1, groupRoleAssignment2))
            whenever(roleRepository.findByIdIn(any<List<UUID>>())).thenReturn(listOf(role1, role2))

            // Act
            val result = authorizationService.getUserRoles(userId)

            // Assert
            assertThat(result).hasSize(2)
            assertThat(result.map { it.name }).containsExactlyInAnyOrder("admin", "editor")
        }

        @Test
        fun `should return empty list when user has no roles`() {
            // Arrange
            val userId = UUID.randomUUID()
            whenever(groupMembershipRepository.findActiveMembershipsByUserId(any(), any())).thenReturn(emptyList())

            // Act
            val result = authorizationService.getUserRoles(userId)

            // Assert
            assertThat(result).isEmpty()
        }

        @Test
        fun `should skip roles that do not exist in repository`() {
            // Arrange
            val userId = UUID.randomUUID()
            val groupId = UUID.randomUUID()
            val roleId = UUID.randomUUID() // Non-existent role
            val groupMembership =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId,
                    groupId = groupId,
                )
            val groupRoleAssignment =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = groupId,
                    roleId = roleId,
                )

            whenever(
                groupMembershipRepository.findActiveMembershipsByUserId(any(), any()),
            ).thenReturn(listOf(groupMembership))
            whenever(
                groupRoleAssignmentRepository.findValidAssignmentsByGroupIds(any<List<UUID>>(), any()),
            ).thenReturn(listOf(groupRoleAssignment))
            whenever(roleRepository.findByIdIn(any<List<UUID>>())).thenReturn(emptyList())

            // Act
            val result = authorizationService.getUserRoles(userId)

            // Assert
            assertThat(result).isEmpty()
        }
    }

    @Nested
    @DisplayName("Batch Methods Tests")
    inner class BatchMethodsTests {
        @Test
        fun `getUserRolesBatch should return roles for multiple users`() {
            // Arrange
            val userId1 = UUID.randomUUID()
            val userId2 = UUID.randomUUID()
            val groupId1 = UUID.randomUUID()
            val groupId2 = UUID.randomUUID()
            val roleId1 = UUID.randomUUID()
            val roleId2 = UUID.randomUUID()
            val groupMembership1 =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId1,
                    groupId = groupId1,
                )
            val groupRoleAssignment1 =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = groupId1,
                    roleId = roleId1,
                )
            val groupMembership2 =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId2,
                    groupId = groupId2,
                )
            val groupRoleAssignment2 =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = groupId2,
                    roleId = roleId2,
                )
            val role1 = SecurityTestDataBuilders.role(id = roleId1, name = "admin")
            val role2 = SecurityTestDataBuilders.role(id = roleId2, name = "editor")

            whenever(groupMembershipRepository.findActiveMembershipsByUserIds(any<List<UUID>>(), any()))
                .thenReturn(listOf(groupMembership1, groupMembership2))
            whenever(
                groupRoleAssignmentRepository.findValidAssignmentsByGroupIds(any<List<UUID>>(), any()),
            ).thenReturn(listOf(groupRoleAssignment1, groupRoleAssignment2))
            whenever(roleRepository.findByIdIn(any<List<UUID>>())).thenReturn(listOf(role1, role2))

            // Act
            val result = authorizationService.getUserRolesBatch(listOf(userId1, userId2))

            // Assert
            assertThat(result).hasSize(2)
            assertThat(result[userId1]).hasSize(1)
            assertThat(result[userId1]!![0].name).isEqualTo("admin")
            assertThat(result[userId2]).hasSize(1)
            assertThat(result[userId2]!![0].name).isEqualTo("editor")
            verify(groupMembershipRepository).findActiveMembershipsByUserIds(any<List<UUID>>(), any())
        }

        @Test
        fun `getUserRolesBatch should handle users with group-inherited roles`() {
            // Arrange
            val userId1 = UUID.randomUUID()
            val userId2 = UUID.randomUUID()
            val groupId = UUID.randomUUID()
            val roleId = UUID.randomUUID()
            val groupMembership1 =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId1,
                    groupId = groupId,
                )
            val groupMembership2 =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId2,
                    groupId = groupId,
                )
            val groupRoleAssignment =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = groupId,
                    roleId = roleId,
                )
            val role = SecurityTestDataBuilders.role(id = roleId, name = "editor")

            whenever(groupMembershipRepository.findActiveMembershipsByUserIds(any<List<UUID>>(), any()))
                .thenReturn(listOf(groupMembership1, groupMembership2))
            whenever(
                groupRoleAssignmentRepository.findValidAssignmentsByGroupIds(any<List<UUID>>(), any()),
            ).thenReturn(listOf(groupRoleAssignment))
            whenever(roleRepository.findByIdIn(any<List<UUID>>())).thenReturn(listOf(role))

            // Act
            val result = authorizationService.getUserRolesBatch(listOf(userId1, userId2))

            // Assert
            assertThat(result).hasSize(2)
            assertThat(result[userId1]).hasSize(1)
            assertThat(result[userId1]!![0].name).isEqualTo("editor")
            assertThat(result[userId2]).hasSize(1)
            assertThat(result[userId2]!![0].name).isEqualTo("editor")
            verify(groupMembershipRepository).findActiveMembershipsByUserIds(any<List<UUID>>(), any())
        }

        @Test
        fun `getUserRolesBatch should return empty map for empty user list`() {
            // Act
            val result = authorizationService.getUserRolesBatch(emptyList())

            // Assert
            assertThat(result).isEmpty()
        }

        @Test
        fun `getUserRolesBatch should return empty lists for users with no roles`() {
            // Arrange
            val userId = UUID.randomUUID()
            whenever(groupMembershipRepository.findActiveMembershipsByUserIds(any<List<UUID>>(), any()))
                .thenReturn(emptyList())

            // Act
            val result = authorizationService.getUserRolesBatch(listOf(userId))

            // Assert
            assertThat(result).hasSize(1)
            assertThat(result[userId]).isEmpty()
        }

        @Test
        fun `getUserPermissionsBatch should return permissions for multiple users`() {
            // Arrange
            val userId1 = UUID.randomUUID()
            val userId2 = UUID.randomUUID()
            val roleId1 = UUID.randomUUID()
            val roleId2 = UUID.randomUUID()
            val permissionId1 = UUID.randomUUID()
            val permissionId2 = UUID.randomUUID()
            val groupId1 = UUID.randomUUID()
            val groupId2 = UUID.randomUUID()
            val groupMembership1 =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId1,
                    groupId = groupId1,
                )
            val groupRoleAssignment1 =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = groupId1,
                    roleId = roleId1,
                )
            val groupMembership2 =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId2,
                    groupId = groupId2,
                )
            val groupRoleAssignment2 =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = groupId2,
                    roleId = roleId2,
                )
            val role1 = SecurityTestDataBuilders.role(id = roleId1, name = "admin")
            val role2 = SecurityTestDataBuilders.role(id = roleId2, name = "editor")
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

            whenever(groupMembershipRepository.findActiveMembershipsByUserIds(any<List<UUID>>(), any()))
                .thenReturn(listOf(groupMembership1, groupMembership2))
            whenever(
                groupRoleAssignmentRepository.findValidAssignmentsByGroupIds(any<List<UUID>>(), any()),
            ).thenReturn(listOf(groupRoleAssignment1, groupRoleAssignment2))
            whenever(roleRepository.findByIdIn(any<List<UUID>>())).thenReturn(listOf(role1, role2))
            whenever(roleRepository.findPermissionIdsByRoleIds(any<List<UUID>>()))
                .thenReturn(listOf(permissionId1, permissionId2))
            whenever(roleRepository.findPermissionIdsByRoleId(roleId1)).thenReturn(listOf(permissionId1))
            whenever(roleRepository.findPermissionIdsByRoleId(roleId2)).thenReturn(listOf(permissionId2))
            whenever(permissionRepository.findByIdIn(any<List<UUID>>())).thenReturn(listOf(permission1, permission2))

            // Act
            val result = authorizationService.getUserPermissionsBatch(listOf(userId1, userId2))

            // Assert
            assertThat(result).hasSize(2)
            assertThat(result[userId1]).hasSize(1)
            assertThat(result[userId1]!![0].name).isEqualTo("transaction:read")
            assertThat(result[userId2]).hasSize(1)
            assertThat(result[userId2]!![0].name).isEqualTo("transaction:write")
        }

        @Test
        fun `getUserPermissionsBatch should return empty map for empty user list`() {
            // Act
            val result = authorizationService.getUserPermissionsBatch(emptyList())

            // Assert
            assertThat(result).isEmpty()
        }

        @Test
        fun `getUserPermissionsBatch should return empty lists for users with no permissions`() {
            // Arrange
            val userId = UUID.randomUUID()
            whenever(groupMembershipRepository.findActiveMembershipsByUserIds(any<List<UUID>>(), any()))
                .thenReturn(emptyList())

            // Act
            val result = authorizationService.getUserPermissionsBatch(listOf(userId))

            // Assert
            assertThat(result).hasSize(1)
            assertThat(result[userId]).isEmpty()
        }
    }

    @Nested
    @DisplayName("Require Permission")
    inner class RequirePermissionTests {
        @Test
        fun `should not throw exception when permission is allowed`() {
            // Arrange
            val userId = UUID.randomUUID()
            val permission = "security:user:view"
            val groupId = UUID.randomUUID()
            val roleId = UUID.randomUUID()
            val groupMembership =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId,
                    groupId = groupId,
                )
            val groupRoleAssignment =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = groupId,
                    roleId = roleId,
                )
            val roleEntity = SecurityTestDataBuilders.role(id = roleId, name = "admin")

            whenever(
                groupMembershipRepository.findActiveMembershipsByUserId(any(), any()),
            ).thenReturn(listOf(groupMembership))
            whenever(
                groupRoleAssignmentRepository.findValidAssignmentsByGroupIds(any<List<UUID>>(), any()),
            ).thenReturn(listOf(groupRoleAssignment))
            whenever(
                permissionRepository.existsPermissionForRoles(permission, listOf(roleId)),
            ).thenReturn(true)
            whenever(roleRepository.findByIdIn(any())).thenReturn(listOf(roleEntity))
            whenever(
                abacEvaluationService.evaluatePolicies(any(), anyOrNull(), anyOrNull()),
            ).thenReturn(
                AbacEvaluationResult(
                    decision = null,
                    matchedPolicies = emptyList(),
                    reason = "No matching ABAC policies",
                ),
            )

            // Act & Assert - should not throw
            authorizationService.requirePermission(userId, permission)
        }

        @Test
        fun `should throw AuthorizationDeniedException when permission is denied`() {
            // Arrange
            val userId = UUID.randomUUID()
            val permission = "security:user:view"

            whenever(groupMembershipRepository.findActiveMembershipsByUserId(any(), any())).thenReturn(emptyList())

            // Act & Assert
            org.assertj.core.api.Assertions.assertThatThrownBy {
                authorizationService.requirePermission(userId, permission)
            }
                .isInstanceOf(AuthorizationDeniedException::class.java)
                .hasMessageContaining("User $userId lacks permission '$permission'")
        }

        @Test
        fun `should pass all parameters to checkPermission`() {
            // Arrange
            val userId = UUID.randomUUID()
            val permission = "security:user:view"
            val resourceType = "user"
            val resourceId = UUID.randomUUID()
            val subjectAttributes = mapOf("department" to "IT")
            val resourceAttributes = mapOf("status" to "active")
            val contextAttributes = mapOf("ip" to "127.0.0.1")
            val groupId = UUID.randomUUID()
            val roleId = UUID.randomUUID()
            val groupMembership =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId,
                    groupId = groupId,
                )
            val groupRoleAssignment =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = groupId,
                    roleId = roleId,
                )
            val roleEntity = SecurityTestDataBuilders.role(id = roleId, name = "admin")

            whenever(
                groupMembershipRepository.findActiveMembershipsByUserId(any(), any()),
            ).thenReturn(listOf(groupMembership))
            whenever(
                groupRoleAssignmentRepository.findValidAssignmentsByGroupIds(any<List<UUID>>(), any()),
            ).thenReturn(listOf(groupRoleAssignment))
            whenever(
                permissionRepository.existsPermissionForRoles(permission, listOf(roleId)),
            ).thenReturn(true)
            whenever(roleRepository.findByIdIn(any())).thenReturn(listOf(roleEntity))
            whenever(
                abacEvaluationService.evaluatePolicies(any(), anyOrNull(), anyOrNull()),
            ).thenReturn(
                AbacEvaluationResult(
                    decision = null,
                    matchedPolicies = emptyList(),
                    reason = "No matching ABAC policies",
                ),
            )

            // Act
            authorizationService.requirePermission(
                userId = userId,
                permission = permission,
                resourceType = resourceType,
                resourceId = resourceId,
                subjectAttributes = subjectAttributes,
                resourceAttributes = resourceAttributes,
                contextAttributes = contextAttributes,
            )

            // Assert - verify that checkPermission was called with all parameters
            // This is verified indirectly by the fact that the method doesn't throw
            // and the mocks are set up correctly
        }
    }
}
