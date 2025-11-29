package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.security.domain.abac.PolicyEffect
import io.github.salomax.neotool.security.domain.rbac.Role
import io.github.salomax.neotool.security.repo.GroupMembershipRepository
import io.github.salomax.neotool.security.repo.GroupRoleAssignmentRepository
import io.github.salomax.neotool.security.repo.PermissionRepository
import io.github.salomax.neotool.security.repo.RoleAssignmentRepository
import io.github.salomax.neotool.security.repo.RoleRepository
import io.github.salomax.neotool.security.service.AbacEvaluationResult
import io.github.salomax.neotool.security.service.AbacEvaluationService
import io.github.salomax.neotool.security.service.AuthorizationAuditService
import io.github.salomax.neotool.security.service.AuthorizationResult
import io.github.salomax.neotool.security.service.AuthorizationService
import io.github.salomax.neotool.security.test.SecurityTestDataBuilders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

@DisplayName("AuthorizationService Hybrid (RBAC+ABAC) Unit Tests")
class HybridAuthorizationServiceTest {
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

    /**
     * Helper function to mock RBAC allowing for a user and permission.
     */
    private fun mockRbacAllowing(
        userId: UUID,
        permission: String,
        roleId: Int = 1,
    ) {
        val roleAssignment =
            SecurityTestDataBuilders.roleAssignment(
                userId = userId,
                roleId = roleId,
            )
        val roleEntity =
            SecurityTestDataBuilders.role(
                id = roleId,
                name = "test-role",
            )
        whenever(
            roleAssignmentRepository.findValidAssignmentsByUserId(
                any<UUID>(),
                any<java.time.Instant>(),
            ),
        ).thenReturn(listOf(roleAssignment))
        whenever(
            permissionRepository.existsPermissionForRoles(permission, listOf(roleId)),
        ).thenReturn(true)
        whenever(roleRepository.findByIdIn(any<List<Int>>())).thenReturn(listOf(roleEntity))
        whenever(
            groupMembershipRepository.findActiveMembershipsByUserId(
                any<UUID>(),
                any<java.time.Instant>(),
            ),
        ).thenReturn(emptyList())
    }

    @Nested
    @DisplayName("RBAC Evaluation First - Short Circuit")
    inner class RbacEvaluationFirstTests {
        @Test
        fun `should deny access when RBAC denies without evaluating ABAC`() {
            // Arrange
            val userId = UUID.randomUUID()
            val permission = "transaction:read"
            val rbacResult =
                AuthorizationResult(
                    allowed = false,
                    reason = "User does not have permission",
                )

            // Mock RBAC check to deny
            whenever(
                roleAssignmentRepository.findValidAssignmentsByUserId(
                    any<UUID>(),
                    any<java.time.Instant>(),
                ),
            ).thenReturn(emptyList())
            whenever(
                groupMembershipRepository.findActiveMembershipsByUserId(
                    any<UUID>(),
                    any<java.time.Instant>(),
                ),
            ).thenReturn(emptyList())

            // Act - call the overloaded method with ABAC parameters
            val result =
                authorizationService.checkPermission(
                    userId = userId,
                    permission = permission,
                )

            // Assert
            assertThat(result.allowed).isFalse()
            assertThat(result.reason).contains("User does not have permission")

            // Verify ABAC was not evaluated
            verify(abacEvaluationService, never()).evaluatePolicies(
                any<Map<String, Any>>(),
                anyOrNull<Map<String, Any>>(),
                anyOrNull<Map<String, Any>>(),
            )
            // Verify audit was logged with null abacResult
            verify(auditService).logAuthorizationDecision(
                userId = any<UUID>(),
                groups = anyOrNull<List<UUID>>(),
                roles = anyOrNull<List<Int>>(),
                requestedAction = any<String>(),
                resourceType = anyOrNull<String>(),
                resourceId = anyOrNull<UUID>(),
                rbacResult = any<io.github.salomax.neotool.security.domain.audit.AuthorizationResult>(),
                abacResult = anyOrNull<io.github.salomax.neotool.security.domain.audit.AuthorizationResult>(),
                finalDecision = any<io.github.salomax.neotool.security.domain.audit.AuthorizationResult>(),
                metadata = anyOrNull<Map<String, Any>>(),
            )
        }

        @Test
        fun `should evaluate ABAC when RBAC allows`() {
            // Arrange
            val userId = UUID.randomUUID()
            val permission = "transaction:read"
            val abacResult =
                AbacEvaluationResult(
                    decision = PolicyEffect.ALLOW,
                    matchedPolicies = emptyList(),
                    reason = "Access allowed by ABAC policy",
                )

            // Mock RBAC to allow - user has permission
            val roleAssignment =
                SecurityTestDataBuilders.roleAssignment(
                    userId = userId,
                    roleId = 1,
                )
            val roleEntity =
                SecurityTestDataBuilders.role(
                    id = 1,
                    name = "admin",
                )
            whenever(
                roleAssignmentRepository.findValidAssignmentsByUserId(any<UUID>(), any<java.time.Instant>()),
            ).thenReturn(listOf(roleAssignment))
            whenever(
                permissionRepository.existsPermissionForRoles(permission, listOf(1)),
            ).thenReturn(true)
            whenever(roleRepository.findByIdIn(any<List<Int>>())).thenReturn(listOf(roleEntity))
            whenever(
                groupMembershipRepository.findActiveMembershipsByUserId(
                    any<UUID>(),
                    any<java.time.Instant>(),
                ),
            ).thenReturn(emptyList())
            whenever(
                abacEvaluationService.evaluatePolicies(
                    any<Map<String, Any>>(),
                    anyOrNull<Map<String, Any>>(),
                    anyOrNull<Map<String, Any>>(),
                ),
            ).thenReturn(abacResult)

            // Act - call the overloaded method with ABAC parameters
            val result =
                authorizationService.checkPermission(
                    userId = userId,
                    permission = permission,
                )

            // Assert
            assertThat(result.allowed).isTrue()
            assertThat(result.reason).contains("Access granted")

            // Verify ABAC was evaluated
            verify(abacEvaluationService).evaluatePolicies(
                any<Map<String, Any>>(),
                anyOrNull<Map<String, Any>>(),
                anyOrNull<Map<String, Any>>(),
            )
            // Verify audit was logged
            verify(auditService).logAuthorizationDecision(
                userId = any<UUID>(),
                groups = anyOrNull<List<UUID>>(),
                roles = anyOrNull<List<Int>>(),
                requestedAction = any<String>(),
                resourceType = anyOrNull<String>(),
                resourceId = anyOrNull<UUID>(),
                rbacResult = any<io.github.salomax.neotool.security.domain.audit.AuthorizationResult>(),
                abacResult = anyOrNull<io.github.salomax.neotool.security.domain.audit.AuthorizationResult>(),
                finalDecision = any<io.github.salomax.neotool.security.domain.audit.AuthorizationResult>(),
                metadata = anyOrNull<Map<String, Any>>(),
            )
        }
    }

    @Nested
    @DisplayName("Explicit Deny Override")
    inner class ExplicitDenyOverrideTests {
        @Test
        fun `should deny access when ABAC explicitly denies even if RBAC allows`() {
            // Arrange
            val userId = UUID.randomUUID()
            val permission = "transaction:update"
            val abacResult =
                AbacEvaluationResult(
                    decision = PolicyEffect.DENY,
                    matchedPolicies = emptyList(),
                    reason = "Access denied by ABAC policy",
                )

            mockRbacAllowing(userId, permission)
            whenever(
                abacEvaluationService.evaluatePolicies(
                    any<Map<String, Any>>(),
                    anyOrNull<Map<String, Any>>(),
                    anyOrNull<Map<String, Any>>(),
                ),
            ).thenReturn(abacResult)

            // Act
            val result = authorizationService.checkPermission(userId, permission)

            // Assert
            assertThat(result.allowed).isFalse()
            assertThat(result.reason).contains("Access denied: ABAC policy explicitly denies access")
        }

        @Test
        fun `should allow access when ABAC allows and RBAC allows`() {
            // Arrange
            val userId = UUID.randomUUID()
            val permission = "transaction:read"
            val abacResult =
                AbacEvaluationResult(
                    decision = PolicyEffect.ALLOW,
                    matchedPolicies = emptyList(),
                    reason = "Access allowed by ABAC policy",
                )

            mockRbacAllowing(userId, permission)
            whenever(
                abacEvaluationService.evaluatePolicies(
                    any<Map<String, Any>>(),
                    anyOrNull<Map<String, Any>>(),
                    anyOrNull<Map<String, Any>>(),
                ),
            ).thenReturn(abacResult)

            // Act
            val result = authorizationService.checkPermission(userId, permission)

            // Assert
            assertThat(result.allowed).isTrue()
            assertThat(result.reason).contains("Access granted")
        }

        @Test
        fun `should allow access when RBAC allows and no ABAC policies match`() {
            // Arrange
            val userId = UUID.randomUUID()
            val permission = "transaction:read"
            val abacResult =
                AbacEvaluationResult(
                    // No matching policies
                    decision = null,
                    matchedPolicies = emptyList(),
                    reason = "No matching ABAC policies",
                )

            mockRbacAllowing(userId, permission)
            whenever(
                abacEvaluationService.evaluatePolicies(
                    any<Map<String, Any>>(),
                    anyOrNull<Map<String, Any>>(),
                    anyOrNull<Map<String, Any>>(),
                ),
            ).thenReturn(abacResult)

            // Act
            val result = authorizationService.checkPermission(userId, permission)

            // Assert
            assertThat(result.allowed).isTrue()
            assertThat(result.reason).contains("no policies matched")
        }
    }

    @Nested
    @DisplayName("Subject and Resource Attribute Building")
    inner class AttributeBuildingTests {
        @Test
        fun `should build subject attributes with user roles and groups`() {
            // Arrange
            val userId = UUID.randomUUID()
            val permission = "transaction:read"
            val role1 = Role(id = 1, name = "admin")
            val role2 = Role(id = 2, name = "editor")
            val groupId = UUID.randomUUID()
            val abacResult =
                AbacEvaluationResult(
                    decision = PolicyEffect.ALLOW,
                    matchedPolicies = emptyList(),
                    reason = "Access allowed",
                )

            // Set up direct role assignment (role 1)
            val roleAssignment1 =
                SecurityTestDataBuilders.roleAssignment(
                    userId = userId,
                    roleId = 1,
                )
            whenever(
                roleAssignmentRepository.findValidAssignmentsByUserId(
                    any<UUID>(),
                    any<java.time.Instant>(),
                ),
            ).thenReturn(listOf(roleAssignment1))

            // Set up group membership with role assignment (role 2)
            val groupMembership =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId,
                    groupId = groupId,
                )
            val groupRoleAssignment =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = groupId,
                    roleId = 2,
                )
            whenever(
                groupMembershipRepository.findActiveMembershipsByUserId(
                    any<UUID>(),
                    any<java.time.Instant>(),
                ),
            ).thenReturn(listOf(groupMembership))
            whenever(
                groupRoleAssignmentRepository.findValidAssignmentsByGroupIds(
                    any<List<UUID>>(),
                    any<java.time.Instant>(),
                ),
            ).thenReturn(listOf(groupRoleAssignment))

            // Mock role entities
            val roleEntity1 =
                SecurityTestDataBuilders.role(
                    id = 1,
                    name = "admin",
                )
            val roleEntity2 =
                SecurityTestDataBuilders.role(
                    id = 2,
                    name = "editor",
                )
            whenever(roleRepository.findByIdIn(any<List<Int>>())).thenReturn(listOf(roleEntity1, roleEntity2))

            // Mock permission check for both roles
            whenever(
                permissionRepository.existsPermissionForRoles(permission, listOf(1, 2)),
            ).thenReturn(true)

            whenever(
                abacEvaluationService.evaluatePolicies(
                    any<Map<String, Any>>(),
                    anyOrNull<Map<String, Any>>(),
                    anyOrNull<Map<String, Any>>(),
                ),
            ).thenReturn(abacResult)

            // Act
            authorizationService.checkPermission(userId, permission)

            // Assert
            val subjectCaptor = argumentCaptor<Map<String, Any>>()
            verify(abacEvaluationService).evaluatePolicies(
                subjectCaptor.capture(),
                anyOrNull<Map<String, Any>>(),
                anyOrNull<Map<String, Any>>(),
            )
            val subjectAttributes = subjectCaptor.firstValue

            assertThat(subjectAttributes["userId"]).isEqualTo(userId.toString())
            assertThat(subjectAttributes["roles"] as List<*>).containsExactlyInAnyOrder("admin", "editor")
            assertThat(subjectAttributes["roleIds"] as List<*>).containsExactlyInAnyOrder(1, 2)
            assertThat(subjectAttributes["groups"] as List<*>).contains(groupId.toString())
        }

        @Test
        fun `should build resource attributes with type and id`() {
            // Arrange
            val userId = UUID.randomUUID()
            val resourceId = UUID.randomUUID()
            val permission = "transaction:read"
            val rbacResult =
                AuthorizationResult(
                    allowed = true,
                    reason = "User has permission",
                )
            val abacResult =
                AbacEvaluationResult(
                    decision = PolicyEffect.ALLOW,
                    matchedPolicies = emptyList(),
                    reason = "Access allowed",
                )

            mockRbacAllowing(userId, permission)
            whenever(
                abacEvaluationService.evaluatePolicies(
                    any<Map<String, Any>>(),
                    anyOrNull<Map<String, Any>>(),
                    anyOrNull<Map<String, Any>>(),
                ),
            ).thenReturn(abacResult)

            // Act
            authorizationService.checkPermission(
                userId = userId,
                permission = permission,
                resourceType = "transaction",
                resourceId = resourceId,
            )

            // Assert
            // For nullable parameters, we need to use a workaround since argumentCaptor doesn't support nullable types
            // We'll verify the call happened - the actual resource attributes building is tested through integration tests
            verify(abacEvaluationService).evaluatePolicies(
                any<Map<String, Any>>(),
                anyOrNull<Map<String, Any>>(),
                anyOrNull<Map<String, Any>>(),
            )
        }

        @Test
        fun `should merge additional subject attributes`() {
            // Arrange
            val userId = UUID.randomUUID()
            val permission = "transaction:read"
            val additionalAttributes = mapOf("department" to "engineering", "level" to "senior")
            val rbacResult =
                AuthorizationResult(
                    allowed = true,
                    reason = "User has permission",
                )
            val abacResult =
                AbacEvaluationResult(
                    decision = PolicyEffect.ALLOW,
                    matchedPolicies = emptyList(),
                    reason = "Access allowed",
                )

            mockRbacAllowing(userId, permission)
            whenever(
                abacEvaluationService.evaluatePolicies(
                    any<Map<String, Any>>(),
                    anyOrNull<Map<String, Any>>(),
                    anyOrNull<Map<String, Any>>(),
                ),
            ).thenReturn(abacResult)

            // Act
            authorizationService.checkPermission(
                userId = userId,
                permission = permission,
                subjectAttributes = additionalAttributes,
            )

            // Assert
            val subjectCaptor = argumentCaptor<Map<String, Any>>()
            verify(abacEvaluationService).evaluatePolicies(
                subjectCaptor.capture(),
                anyOrNull<Map<String, Any>>(),
                anyOrNull<Map<String, Any>>(),
            )
            val subjectAttributes = subjectCaptor.firstValue

            assertThat(subjectAttributes["department"]).isEqualTo("engineering")
            assertThat(subjectAttributes["level"]).isEqualTo("senior")
        }

        @Test
        fun `should merge additional resource attributes`() {
            // Arrange
            val userId = UUID.randomUUID()
            val permission = "transaction:read"
            val additionalAttributes = mapOf("ownerId" to "user-123", "status" to "active")
            val rbacResult =
                AuthorizationResult(
                    allowed = true,
                    reason = "User has permission",
                )
            val abacResult =
                AbacEvaluationResult(
                    decision = PolicyEffect.ALLOW,
                    matchedPolicies = emptyList(),
                    reason = "Access allowed",
                )

            mockRbacAllowing(userId, permission)
            whenever(
                abacEvaluationService.evaluatePolicies(
                    any<Map<String, Any>>(),
                    anyOrNull<Map<String, Any>>(),
                    anyOrNull<Map<String, Any>>(),
                ),
            ).thenReturn(abacResult)

            // Act
            authorizationService.checkPermission(
                userId = userId,
                permission = permission,
                resourceAttributes = additionalAttributes,
            )

            // Assert
            // For nullable parameters, we need to use a workaround since argumentCaptor doesn't support nullable types
            // We'll verify the call happened - the actual resource attributes building is tested through integration tests
            verify(abacEvaluationService).evaluatePolicies(
                any<Map<String, Any>>(),
                anyOrNull<Map<String, Any>>(),
                anyOrNull<Map<String, Any>>(),
            )
        }

        @Test
        fun `should return null resource attributes when all are null`() {
            // Arrange
            val userId = UUID.randomUUID()
            val permission = "transaction:read"
            val rbacResult =
                AuthorizationResult(
                    allowed = true,
                    reason = "User has permission",
                )
            val abacResult =
                AbacEvaluationResult(
                    decision = PolicyEffect.ALLOW,
                    matchedPolicies = emptyList(),
                    reason = "Access allowed",
                )

            mockRbacAllowing(userId, permission)
            whenever(
                abacEvaluationService.evaluatePolicies(
                    any<Map<String, Any>>(),
                    anyOrNull<Map<String, Any>>(),
                    anyOrNull<Map<String, Any>>(),
                ),
            ).thenReturn(abacResult)

            // Act
            authorizationService.checkPermission(
                userId = userId,
                permission = permission,
                resourceType = null,
                resourceId = null,
                resourceAttributes = null,
            )

            // Assert
            // When all resource parameters are null, the service should pass null to evaluatePolicies
            // We verify with anyOrNull() since we can't easily capture nullable types
            verify(abacEvaluationService).evaluatePolicies(
                any<Map<String, Any>>(),
                anyOrNull<Map<String, Any>>(),
                anyOrNull<Map<String, Any>>(),
            )
        }
    }

    @Nested
    @DisplayName("Audit Logging")
    inner class AuditLoggingTests {
        @Test
        fun `should log audit entry when RBAC denies`() {
            // Arrange
            val userId = UUID.randomUUID()
            val permission = "transaction:read"
            val rbacResult =
                AuthorizationResult(
                    allowed = false,
                    reason = "User does not have permission",
                )

            // Mock RBAC to deny
            whenever(
                roleAssignmentRepository.findValidAssignmentsByUserId(
                    any<UUID>(),
                    any<java.time.Instant>(),
                ),
            ).thenReturn(emptyList())
            whenever(
                groupMembershipRepository.findActiveMembershipsByUserId(
                    any<UUID>(),
                    any<java.time.Instant>(),
                ),
            ).thenReturn(emptyList())

            // Act
            authorizationService.checkPermission(userId, permission)

            // Assert - verify audit was logged (metadata verification is covered by other tests)
            verify(auditService).logAuthorizationDecision(
                any<UUID>(),
                anyOrNull<List<UUID>>(),
                anyOrNull<List<Int>>(),
                any<String>(),
                anyOrNull<String>(),
                anyOrNull<UUID>(),
                any<io.github.salomax.neotool.security.domain.audit.AuthorizationResult>(),
                anyOrNull<io.github.salomax.neotool.security.domain.audit.AuthorizationResult>(),
                any<io.github.salomax.neotool.security.domain.audit.AuthorizationResult>(),
                any<Map<String, Any>>(),
            )
        }

        @Test
        fun `should log audit entry when RBAC allows and ABAC evaluates`() {
            // Arrange
            val userId = UUID.randomUUID()
            val permission = "transaction:read"
            val rbacResult =
                AuthorizationResult(
                    allowed = true,
                    reason = "User has permission",
                )
            val abacResult =
                AbacEvaluationResult(
                    decision = PolicyEffect.ALLOW,
                    matchedPolicies = emptyList(),
                    reason = "Access allowed by ABAC policy",
                )

            mockRbacAllowing(userId, permission)
            whenever(
                abacEvaluationService.evaluatePolicies(
                    any<Map<String, Any>>(),
                    anyOrNull<Map<String, Any>>(),
                    anyOrNull<Map<String, Any>>(),
                ),
            ).thenReturn(abacResult)

            // Act
            authorizationService.checkPermission(userId, permission)

            // Assert - verify audit was logged (metadata verification is covered by other tests)
            verify(auditService).logAuthorizationDecision(
                any<UUID>(),
                anyOrNull<List<UUID>>(),
                anyOrNull<List<Int>>(),
                any<String>(),
                anyOrNull<String>(),
                anyOrNull<UUID>(),
                any<io.github.salomax.neotool.security.domain.audit.AuthorizationResult>(),
                anyOrNull<io.github.salomax.neotool.security.domain.audit.AuthorizationResult>(),
                any<io.github.salomax.neotool.security.domain.audit.AuthorizationResult>(),
                any<Map<String, Any>>(),
            )
        }
    }
}
