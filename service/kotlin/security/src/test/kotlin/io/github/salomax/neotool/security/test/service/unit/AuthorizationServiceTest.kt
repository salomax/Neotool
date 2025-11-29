package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.security.repo.GroupMembershipRepository
import io.github.salomax.neotool.security.repo.GroupRoleAssignmentRepository
import io.github.salomax.neotool.security.repo.PermissionRepository
import io.github.salomax.neotool.security.repo.RoleAssignmentRepository
import io.github.salomax.neotool.security.repo.RoleRepository
import io.github.salomax.neotool.security.service.AbacEvaluationResult
import io.github.salomax.neotool.security.service.AbacEvaluationService
import io.github.salomax.neotool.security.service.AuthorizationAuditService
import io.github.salomax.neotool.security.service.AuthorizationService
import io.github.salomax.neotool.security.test.SecurityTestDataBuilders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

@DisplayName("AuthorizationService Unit Tests")
class AuthorizationServiceTest {
    private lateinit var roleRepository: RoleRepository
    private lateinit var permissionRepository: PermissionRepository
    private lateinit var roleAssignmentRepository: RoleAssignmentRepository
    private lateinit var groupMembershipRepository: GroupMembershipRepository
    private lateinit var groupRoleAssignmentRepository: GroupRoleAssignmentRepository
    private lateinit var abacEvaluationService: AbacEvaluationService
    private lateinit var auditService: AuthorizationAuditService
    private lateinit var authorizationService: AuthorizationService

    @BeforeEach
    fun setUp() {
        roleRepository = mock()
        permissionRepository = mock()
        roleAssignmentRepository = mock()
        groupMembershipRepository = mock()
        groupRoleAssignmentRepository = mock()
        abacEvaluationService = mock()
        auditService = mock()
        authorizationService =
            AuthorizationService(
                roleRepository = roleRepository,
                permissionRepository = permissionRepository,
                roleAssignmentRepository = roleAssignmentRepository,
                groupMembershipRepository = groupMembershipRepository,
                groupRoleAssignmentRepository = groupRoleAssignmentRepository,
                abacEvaluationService = abacEvaluationService,
                auditService = auditService,
            )
    }

    @Nested
    @DisplayName("Check Permission - Direct Role Assignments")
    inner class CheckPermissionDirectRoleTests {
        @Test
        fun `should allow access when user has direct role with permission`() {
            // Arrange
            val userId = UUID.randomUUID()
            val permission = "transaction:read"
            val roleId = 1
            val roleAssignment =
                SecurityTestDataBuilders.roleAssignment(
                    userId = userId,
                    roleId = roleId,
                )

            val roleEntity = SecurityTestDataBuilders.role(id = roleId, name = "admin")

            whenever(
                roleAssignmentRepository.findValidAssignmentsByUserId(any(), any()),
            ).thenReturn(listOf(roleAssignment))
            whenever(
                permissionRepository.existsPermissionForRoles(permission, listOf(roleId)),
            ).thenReturn(true)
            whenever(roleRepository.findByIdIn(any())).thenReturn(listOf(roleEntity))
            whenever(groupMembershipRepository.findActiveMembershipsByUserId(any(), any())).thenReturn(emptyList())
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
            whenever(roleAssignmentRepository.findValidAssignmentsByUserId(any(), any())).thenReturn(emptyList())
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
            val roleId = 1
            val expiredAssignment =
                SecurityTestDataBuilders.roleAssignment(
                    userId = userId,
                    roleId = roleId,
                    // Expired 1 hour ago
                    validUntil = Instant.now().minusSeconds(3600),
                )

            // Expired assignments should not be returned by findValidAssignmentsByUserId
            whenever(roleAssignmentRepository.findValidAssignmentsByUserId(any(), any())).thenReturn(emptyList())
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

            // Future-dated assignments should not be returned by findValidAssignmentsByUserId
            whenever(roleAssignmentRepository.findValidAssignmentsByUserId(any(), any())).thenReturn(emptyList())
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
            val roleId = 2
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

            whenever(roleAssignmentRepository.findValidAssignmentsByUserId(any(), any())).thenReturn(emptyList())
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
            whenever(roleAssignmentRepository.findValidAssignmentsByUserId(any(), any())).thenReturn(emptyList())
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
        fun `should return permissions from direct role assignments`() {
            // Arrange
            val userId = UUID.randomUUID()
            val roleId = 1
            val roleAssignment =
                SecurityTestDataBuilders.roleAssignment(
                    userId = userId,
                    roleId = roleId,
                )
            val permission1 = SecurityTestDataBuilders.permission(id = 1, name = "transaction:read")
            val permission2 = SecurityTestDataBuilders.permission(id = 2, name = "transaction:write")

            whenever(
                roleAssignmentRepository.findValidAssignmentsByUserId(any(), any()),
            ).thenReturn(listOf(roleAssignment))
            whenever(roleRepository.findPermissionIdsByRoleIds(any<List<Int>>())).thenReturn(listOf(1, 2))
            whenever(permissionRepository.findByIdIn(any())).thenReturn(listOf(permission1, permission2))
            whenever(groupMembershipRepository.findActiveMembershipsByUserId(any(), any())).thenReturn(emptyList())

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
            val roleId = 2
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
            val permission = SecurityTestDataBuilders.permission(id = 3, name = "transaction:delete")

            whenever(roleAssignmentRepository.findValidAssignmentsByUserId(any(), any())).thenReturn(emptyList())
            whenever(
                groupMembershipRepository.findActiveMembershipsByUserId(any(), any()),
            ).thenReturn(listOf(groupMembership))
            whenever(
                groupRoleAssignmentRepository.findValidAssignmentsByGroupIds(any<List<UUID>>(), any()),
            ).thenReturn(listOf(groupRoleAssignment))
            whenever(roleRepository.findPermissionIdsByRoleIds(any<List<Int>>())).thenReturn(listOf(3))
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
            val directRoleId = 1
            val groupRoleId = 2
            val directAssignment =
                SecurityTestDataBuilders.roleAssignment(
                    userId = userId,
                    roleId = directRoleId,
                )
            val groupMembership =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId,
                    groupId = groupId,
                )
            val groupRoleAssignment =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = groupId,
                    roleId = groupRoleId,
                )
            val directPermission = SecurityTestDataBuilders.permission(id = 1, name = "transaction:read")
            val groupPermission = SecurityTestDataBuilders.permission(id = 2, name = "transaction:write")

            whenever(
                roleAssignmentRepository.findValidAssignmentsByUserId(any(), any()),
            ).thenReturn(listOf(directAssignment))
            whenever(roleRepository.findPermissionIdsByRoleIds(any<List<Int>>())).thenReturn(listOf(1, 2))
            whenever(
                permissionRepository.findByIdIn(any()),
            ).thenReturn(listOf(directPermission, groupPermission))
            whenever(
                groupMembershipRepository.findActiveMembershipsByUserId(any(), any()),
            ).thenReturn(listOf(groupMembership))
            whenever(
                groupRoleAssignmentRepository.findValidAssignmentsByGroupIds(any<List<UUID>>(), any()),
            ).thenReturn(listOf(groupRoleAssignment))

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
            whenever(roleAssignmentRepository.findValidAssignmentsByUserId(any(), any())).thenReturn(emptyList())
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
        fun `should return roles from direct role assignments`() {
            // Arrange
            val userId = UUID.randomUUID()
            val roleId = 1
            val roleAssignment =
                SecurityTestDataBuilders.roleAssignment(
                    userId = userId,
                    roleId = roleId,
                )
            val roleEntity = SecurityTestDataBuilders.role(id = roleId, name = "admin")

            whenever(
                roleAssignmentRepository.findValidAssignmentsByUserId(any(), any()),
            ).thenReturn(listOf(roleAssignment))
            whenever(roleRepository.findByIdIn(any<List<Int>>())).thenReturn(listOf(roleEntity))
            whenever(groupMembershipRepository.findActiveMembershipsByUserId(any(), any())).thenReturn(emptyList())

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
            val roleId = 2
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

            whenever(roleAssignmentRepository.findValidAssignmentsByUserId(any(), any())).thenReturn(emptyList())
            whenever(
                groupMembershipRepository.findActiveMembershipsByUserId(any(), any()),
            ).thenReturn(listOf(groupMembership))
            whenever(
                groupRoleAssignmentRepository.findValidAssignmentsByGroupIds(any<List<UUID>>(), any()),
            ).thenReturn(listOf(groupRoleAssignment))
            whenever(roleRepository.findByIdIn(any<List<Int>>())).thenReturn(listOf(roleEntity))

            // Act
            val result = authorizationService.getUserRoles(userId)

            // Assert
            assertThat(result).hasSize(1)
            assertThat(result[0].name).isEqualTo("editor")
        }

        @Test
        fun `should combine roles from direct and group-inherited assignments`() {
            // Arrange
            val userId = UUID.randomUUID()
            val groupId = UUID.randomUUID()
            val directRoleId = 1
            val groupRoleId = 2
            val directAssignment =
                SecurityTestDataBuilders.roleAssignment(
                    userId = userId,
                    roleId = directRoleId,
                )
            val groupMembership =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId,
                    groupId = groupId,
                )
            val groupRoleAssignment =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = groupId,
                    roleId = groupRoleId,
                )
            val directRole = SecurityTestDataBuilders.role(id = directRoleId, name = "admin")
            val groupRole = SecurityTestDataBuilders.role(id = groupRoleId, name = "editor")

            whenever(
                roleAssignmentRepository.findValidAssignmentsByUserId(any(), any()),
            ).thenReturn(listOf(directAssignment))
            whenever(roleRepository.findByIdIn(any<List<Int>>())).thenReturn(listOf(directRole, groupRole))
            whenever(
                groupMembershipRepository.findActiveMembershipsByUserId(any(), any()),
            ).thenReturn(listOf(groupMembership))
            whenever(
                groupRoleAssignmentRepository.findValidAssignmentsByGroupIds(any<List<UUID>>(), any()),
            ).thenReturn(listOf(groupRoleAssignment))

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
            whenever(roleAssignmentRepository.findValidAssignmentsByUserId(any(), any())).thenReturn(emptyList())
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
            val roleId = 999 // Non-existent role
            val roleAssignment =
                SecurityTestDataBuilders.roleAssignment(
                    userId = userId,
                    roleId = roleId,
                )

            whenever(
                roleAssignmentRepository.findValidAssignmentsByUserId(any(), any()),
            ).thenReturn(listOf(roleAssignment))
            whenever(roleRepository.findByIdIn(any<List<Int>>())).thenReturn(emptyList())
            whenever(groupMembershipRepository.findActiveMembershipsByUserId(any(), any())).thenReturn(emptyList())

            // Act
            val result = authorizationService.getUserRoles(userId)

            // Assert
            assertThat(result).isEmpty()
        }
    }
}
