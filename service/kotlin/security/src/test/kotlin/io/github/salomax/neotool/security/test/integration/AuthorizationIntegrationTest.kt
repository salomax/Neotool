package io.github.salomax.neotool.security.test.integration

import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.common.test.transaction.runTransaction
import io.github.salomax.neotool.security.model.UserEntity
import io.github.salomax.neotool.security.repo.AbacPolicyRepository
import io.github.salomax.neotool.security.repo.GroupMembershipRepository
import io.github.salomax.neotool.security.repo.GroupRepository
import io.github.salomax.neotool.security.repo.GroupRoleAssignmentRepository
import io.github.salomax.neotool.security.repo.PermissionRepository
import io.github.salomax.neotool.security.repo.RoleRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.AuthenticationService
import io.github.salomax.neotool.security.service.AuthorizationService
import io.github.salomax.neotool.security.test.SecurityTestDataBuilders
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import java.time.Instant

/**
 * Integration tests for authorization checks.
 * Tests full flow from service layer to database.
 */
@MicronautTest(startApplication = true)
@DisplayName("Authorization Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("authorization")
@Tag("database")
@TestMethodOrder(MethodOrderer.Random::class)
open class AuthorizationIntegrationTest : BaseIntegrationTest(), PostgresIntegrationTest {
    @Inject
    lateinit var roleRepository: RoleRepository

    @Inject
    lateinit var permissionRepository: PermissionRepository

    @Inject
    lateinit var groupRepository: GroupRepository

    @Inject
    lateinit var groupMembershipRepository: GroupMembershipRepository

    @Inject
    lateinit var groupRoleAssignmentRepository: GroupRoleAssignmentRepository

    @Inject
    lateinit var abacPolicyRepository: AbacPolicyRepository

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var authenticationService: AuthenticationService

    @Inject
    lateinit var authorizationService: AuthorizationService

    @Inject
    lateinit var entityManager: EntityManager

    /**
     * Create a test user with database-generated UUID v7.
     * Must be called before creating role assignments or group memberships.
     * Returns the saved user with the generated ID.
     */
    private fun createTestUser(): UserEntity {
        // Let database generate UUID v7
        val user =
            SecurityTestDataBuilders.user(
                id = null,
                email = SecurityTestDataBuilders.uniqueEmail("auth-test"),
            )
        return entityManager.runTransaction {
            val savedUser = authenticationService.saveUser(user)
            entityManager.flush()
            savedUser
        }
    }

    @AfterEach
    fun cleanupTestData() {
        try {
            // Clean up in reverse order of dependencies
            groupRoleAssignmentRepository.deleteAll()
            groupMembershipRepository.deleteAll()
            // Note: role_permissions is a join table, handled by JPA
            permissionRepository.deleteAll()
            roleRepository.deleteAll()
            groupRepository.deleteAll()
            abacPolicyRepository.deleteAll()
            userRepository.deleteAll()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    @Nested
    @DisplayName("Group-Based Role Assignment - Happy Path")
    inner class GroupBasedRoleAssignmentTests {
        @Test
        fun `should allow access when user has role through group membership`() {
            // Arrange
            val user = createTestUser() // Create user before group membership

            val role = SecurityTestDataBuilders.role(name = "admin")
            val savedRole = roleRepository.save(role)

            val permission = SecurityTestDataBuilders.permission(name = "transaction:read")
            val savedPermission = permissionRepository.save(permission)

            // Link role and permission via role_permissions join table
            entityManager.createNativeQuery(
                """
                INSERT INTO security.role_permissions (role_id, permission_id)
                VALUES (:roleId, :permissionId)
                """.trimIndent(),
            )
                .setParameter("roleId", savedRole.id)
                .setParameter("permissionId", savedPermission.id)
                .executeUpdate()

            // Create group and assign role to group
            val group = SecurityTestDataBuilders.group(name = "admins")
            val savedGroup = groupRepository.save(group)

            val groupRoleAssignment =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = savedGroup.id,
                    roleId = savedRole.id!!,
                )
            groupRoleAssignmentRepository.save(groupRoleAssignment)

            // Add user to group
            val groupMembership =
                SecurityTestDataBuilders.groupMembership(
                    userId = user.id!!,
                    groupId = savedGroup.id,
                )
            groupMembershipRepository.save(groupMembership)
            entityManager.flush()

            // Act
            val result = authorizationService.checkPermission(user.id!!, "transaction:read")

            // Assert
            assertThat(result.allowed).isTrue()
            assertThat(result.reason).contains("Access granted")
        }

        @Test
        fun `should deny access when user does not have permission`() {
            // Arrange
            val user = createTestUser()
            val userId = user.id!!

            // Act
            val result = authorizationService.checkPermission(userId, "transaction:read")

            // Assert
            assertThat(result.allowed).isFalse()
            assertThat(result.reason).contains("User does not have permission")
        }
    }

    @Nested
    @DisplayName("Group Inherited Roles - Happy Path")
    inner class GroupInheritedRolesTests {
        @Test
        fun `should allow access when user inherits permission from group`() {
            // Arrange
            val user = createTestUser() // Create user before group membership
            val userId = user.id!!

            val role = SecurityTestDataBuilders.role(name = "editor")
            val savedRole = roleRepository.save(role)

            val permission = SecurityTestDataBuilders.permission(name = "transaction:write")
            val savedPermission = permissionRepository.save(permission)

            // Link role and permission
            entityManager.createNativeQuery(
                """
                INSERT INTO security.role_permissions (role_id, permission_id)
                VALUES (:roleId, :permissionId)
                """.trimIndent(),
            )
                .setParameter("roleId", savedRole.id)
                .setParameter("permissionId", savedPermission.id)
                .executeUpdate()

            val group = SecurityTestDataBuilders.group(name = "editors")
            val savedGroup = groupRepository.save(group)

            val groupMembership =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId,
                    groupId = savedGroup.id,
                )
            groupMembershipRepository.save(groupMembership)

            val groupRoleAssignment =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = savedGroup.id,
                    roleId = savedRole.id!!,
                )
            groupRoleAssignmentRepository.save(groupRoleAssignment)
            entityManager.flush()

            // Act
            val result = authorizationService.checkPermission(userId, "transaction:write")

            // Assert
            assertThat(result.allowed).isTrue()
            assertThat(result.reason).contains("Access granted")
        }
    }

    @Nested
    @DisplayName("Temporary Access Validity")
    inner class TemporaryAccessValidityTests {
        @Test
        fun `should allow access when group role assignment is within valid date range`() {
            // Arrange
            val user = createTestUser() // Create user before group membership
            val userId = user.id!!

            val role = SecurityTestDataBuilders.role(name = "temp-access")
            val savedRole = roleRepository.save(role)

            val permission = SecurityTestDataBuilders.permission(name = "transaction:read")
            val savedPermission = permissionRepository.save(permission)

            // Link role and permission
            entityManager.createNativeQuery(
                """
                INSERT INTO security.role_permissions (role_id, permission_id)
                VALUES (:roleId, :permissionId)
                """.trimIndent(),
            )
                .setParameter("roleId", savedRole.id)
                .setParameter("permissionId", savedPermission.id)
                .executeUpdate()

            // Create group and assign role to group with temporal validity
            val group = SecurityTestDataBuilders.group(name = "temp-group")
            val savedGroup = groupRepository.save(group)

            val now = Instant.now()
            val groupRoleAssignment =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = savedGroup.id,
                    roleId = savedRole.id!!,
                    // Started 1 hour ago
                    validFrom = now.minusSeconds(3600),
                    // Expires in 1 hour
                    validUntil = now.plusSeconds(3600),
                )
            groupRoleAssignmentRepository.save(groupRoleAssignment)

            // Add user to group
            val groupMembership =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId,
                    groupId = savedGroup.id,
                )
            groupMembershipRepository.save(groupMembership)
            entityManager.flush()

            // Act
            val result = authorizationService.checkPermission(userId, "transaction:read")

            // Assert
            assertThat(result.allowed).isTrue()
        }

        @Test
        fun `should deny access when group role assignment has expired`() {
            // Arrange
            val user = createTestUser() // Create user before group membership
            val userId = user.id!!

            val role = SecurityTestDataBuilders.role(name = "expired-role")
            val savedRole = roleRepository.save(role)

            val permission = SecurityTestDataBuilders.permission(name = "transaction:read")
            val savedPermission = permissionRepository.save(permission)

            // Link role and permission
            entityManager.createNativeQuery(
                """
                INSERT INTO security.role_permissions (role_id, permission_id)
                VALUES (:roleId, :permissionId)
                """.trimIndent(),
            )
                .setParameter("roleId", savedRole.id)
                .setParameter("permissionId", savedPermission.id)
                .executeUpdate()

            // Create group and assign role to group with expired validity
            val group = SecurityTestDataBuilders.group(name = "expired-group")
            val savedGroup = groupRepository.save(group)

            val now = Instant.now()
            val groupRoleAssignment =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = savedGroup.id,
                    roleId = savedRole.id!!,
                    // Started 2 hours ago
                    validFrom = now.minusSeconds(7200),
                    // Expired 1 hour ago
                    validUntil = now.minusSeconds(3600),
                )
            groupRoleAssignmentRepository.save(groupRoleAssignment)

            // Add user to group
            val groupMembership =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId,
                    groupId = savedGroup.id,
                )
            groupMembershipRepository.save(groupMembership)
            entityManager.flush()

            // Act
            val result = authorizationService.checkPermission(userId, "transaction:read")

            // Assert
            // Expired assignments should not be returned by findValidAssignmentsByGroupIds
            assertThat(result.allowed).isFalse()
        }
    }

    @Nested
    @DisplayName("Get User Permissions")
    inner class GetUserPermissionsTests {
        @Test
        fun `should return permissions from multiple group-inherited roles`() {
            // Arrange
            val user = createTestUser() // Create user before group memberships
            val userId = user.id!!

            val role1 = SecurityTestDataBuilders.role(name = "admin")
            val savedRole1 = roleRepository.save(role1)

            val role2 = SecurityTestDataBuilders.role(name = "editor")
            val savedRole2 = roleRepository.save(role2)

            val permission1 = SecurityTestDataBuilders.permission(name = "transaction:read")
            val savedPermission1 = permissionRepository.save(permission1)

            val permission2 = SecurityTestDataBuilders.permission(name = "transaction:write")
            val savedPermission2 = permissionRepository.save(permission2)

            // Link roles and permissions
            entityManager.createNativeQuery(
                """
                INSERT INTO security.role_permissions (role_id, permission_id)
                VALUES (:roleId, :permissionId)
                """.trimIndent(),
            )
                .setParameter("roleId", savedRole1.id)
                .setParameter("permissionId", savedPermission1.id)
                .executeUpdate()

            entityManager.createNativeQuery(
                """
                INSERT INTO security.role_permissions (role_id, permission_id)
                VALUES (:roleId, :permissionId)
                """.trimIndent(),
            )
                .setParameter("roleId", savedRole2.id)
                .setParameter("permissionId", savedPermission2.id)
                .executeUpdate()

            // Group 1 with role 1
            val group1 = SecurityTestDataBuilders.group(name = "admins")
            val savedGroup1 = groupRepository.save(group1)

            val groupMembership1 =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId,
                    groupId = savedGroup1.id,
                )
            groupMembershipRepository.save(groupMembership1)

            val groupRoleAssignment1 =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = savedGroup1.id,
                    roleId = savedRole1.id!!,
                )
            groupRoleAssignmentRepository.save(groupRoleAssignment1)

            // Group 2 with role 2
            val group2 = SecurityTestDataBuilders.group(name = "editors")
            val savedGroup2 = groupRepository.save(group2)

            val groupMembership2 =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId,
                    groupId = savedGroup2.id,
                )
            groupMembershipRepository.save(groupMembership2)

            val groupRoleAssignment2 =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = savedGroup2.id,
                    roleId = savedRole2.id!!,
                )
            groupRoleAssignmentRepository.save(groupRoleAssignment2)
            entityManager.flush()

            // Act
            val result = authorizationService.getUserPermissions(userId)

            // Assert
            assertThat(result).hasSize(2)
            assertThat(result.map { it.name }).containsExactlyInAnyOrder("transaction:read", "transaction:write")
        }
    }

    @Nested
    @DisplayName("Get User Roles")
    inner class GetUserRolesTests {
        @Test
        fun `should return roles from multiple group-inherited assignments`() {
            // Arrange
            val user = createTestUser() // Create user before group memberships
            val userId = user.id!!

            val role1 = SecurityTestDataBuilders.role(name = "admin")
            val savedRole1 = roleRepository.save(role1)

            val role2 = SecurityTestDataBuilders.role(name = "editor")
            val savedRole2 = roleRepository.save(role2)

            // Group 1 with role 1
            val group1 = SecurityTestDataBuilders.group(name = "admins")
            val savedGroup1 = groupRepository.save(group1)

            val groupMembership1 =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId,
                    groupId = savedGroup1.id,
                )
            groupMembershipRepository.save(groupMembership1)

            val groupRoleAssignment1 =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = savedGroup1.id,
                    roleId = savedRole1.id!!,
                )
            groupRoleAssignmentRepository.save(groupRoleAssignment1)

            // Group 2 with role 2
            val group2 = SecurityTestDataBuilders.group(name = "editors")
            val savedGroup2 = groupRepository.save(group2)

            val groupMembership2 =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId,
                    groupId = savedGroup2.id,
                )
            groupMembershipRepository.save(groupMembership2)

            val groupRoleAssignment2 =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = savedGroup2.id,
                    roleId = savedRole2.id!!,
                )
            groupRoleAssignmentRepository.save(groupRoleAssignment2)
            entityManager.flush()

            // Act
            val result = authorizationService.getUserRoles(userId)

            // Assert
            assertThat(result).hasSize(2)
            assertThat(result.map { it.name }).containsExactlyInAnyOrder("admin", "editor")
        }
    }

    @Nested
    @DisplayName("Hybrid Authorization - RBAC + ABAC")
    inner class HybridAuthorizationTests {
        @Test
        fun `should deny access when RBAC denies without evaluating ABAC`() {
            // Arrange
            val user = createTestUser()
            val userId = user.id!!
            val userIdString = userId.toString()

            // Setup ABAC policy in a committed transaction to verify it's not evaluated when RBAC denies
            entityManager.runTransaction {
                val abacPolicy =
                    SecurityTestDataBuilders.abacPolicy(
                        name = "should-not-match",
                        effect = io.github.salomax.neotool.security.domain.abac.PolicyEffect.ALLOW,
                        condition = """{"eq": {"subject.userId": "$userIdString"}}""",
                        isActive = true,
                    )
                abacPolicyRepository.save(abacPolicy)
            }

            // Act
            val result =
                authorizationService.checkPermission(
                    userId = userId,
                    permission = "transaction:read",
                )

            // Assert
            assertThat(result.allowed)
                .withFailMessage("Expected access to be denied when RBAC denies. Reason: ${result.reason}")
                .isFalse()
            // Verify ABAC was not evaluated (reason should not mention ABAC)
            assertThat(result.reason)
                .withFailMessage("Expected reason to not mention ABAC when RBAC denies, but got: ${result.reason}")
                .doesNotContain("ABAC")
        }

        @Test
        fun `should allow access when RBAC allows and ABAC allows`() {
            // Arrange
            // Setup test data in a committed transaction so it's visible to the service
            val userId =
                entityManager.runTransaction {
                    // Create user before group membership
                    val user = createTestUser()
                    val userId = user.id!!

                    val role = SecurityTestDataBuilders.role(name = "admin")
                    val savedRole = roleRepository.save(role)

                    val permission = SecurityTestDataBuilders.permission(name = "transaction:read")
                    val savedPermission = permissionRepository.save(permission)

                    // Link role and permission
                    entityManager.createNativeQuery(
                        """
                        INSERT INTO security.role_permissions (role_id, permission_id)
                        VALUES (:roleId, :permissionId)
                        """.trimIndent(),
                    )
                        .setParameter("roleId", savedRole.id)
                        .setParameter("permissionId", savedPermission.id)
                        .executeUpdate()

                    // Create group and assign role to group
                    val group = SecurityTestDataBuilders.group(name = "admins")
                    val savedGroup = groupRepository.save(group)

                    val groupRoleAssignment =
                        SecurityTestDataBuilders.groupRoleAssignment(
                            groupId = savedGroup.id,
                            roleId = savedRole.id!!,
                        )
                    groupRoleAssignmentRepository.save(groupRoleAssignment)

                    // Add user to group
                    val groupMembership =
                        SecurityTestDataBuilders.groupMembership(
                            userId = userId,
                            groupId = savedGroup.id,
                        )
                    groupMembershipRepository.save(groupMembership)

                    // ABAC policy that allows - use explicit UUID string format
                    val userIdString = userId.toString()
                    val abacPolicy =
                        SecurityTestDataBuilders.abacPolicy(
                            name = "allow-policy",
                            effect = io.github.salomax.neotool.security.domain.abac.PolicyEffect.ALLOW,
                            condition = """{"eq": {"subject.userId": "$userIdString"}}""",
                            isActive = true,
                        )
                    abacPolicyRepository.save(abacPolicy)
                    entityManager.flush()
                    userId
                }

            // Act
            val result =
                authorizationService.checkPermission(
                    userId = userId,
                    permission = "transaction:read",
                )

            // Assert
            assertThat(result.allowed)
                .withFailMessage(
                    "Expected access to be allowed when RBAC and ABAC both allow. Reason: ${result.reason}",
                )
                .isTrue()
            assertThat(result.reason)
                .withFailMessage("Expected reason to contain 'Access granted', but got: ${result.reason}")
                .contains("Access granted")
        }

        @Test
        fun `should deny access when RBAC allows but ABAC explicitly denies`() {
            // Arrange
            // Setup test data in a committed transaction so it's visible to the service
            val userId =
                entityManager.runTransaction {
                    // Create user before group membership
                    val user = createTestUser()
                    val userId = user.id!!

                    val role = SecurityTestDataBuilders.role(name = "admin")
                    val savedRole = roleRepository.save(role)

                    val permission = SecurityTestDataBuilders.permission(name = "transaction:read")
                    val savedPermission = permissionRepository.save(permission)

                    // Link role and permission
                    entityManager.createNativeQuery(
                        """
                        INSERT INTO security.role_permissions (role_id, permission_id)
                        VALUES (:roleId, :permissionId)
                        """.trimIndent(),
                    )
                        .setParameter("roleId", savedRole.id)
                        .setParameter("permissionId", savedPermission.id)
                        .executeUpdate()

                    // Create group and assign role to group
                    val group = SecurityTestDataBuilders.group(name = "admins")
                    val savedGroup = groupRepository.save(group)

                    val groupRoleAssignment =
                        SecurityTestDataBuilders.groupRoleAssignment(
                            groupId = savedGroup.id,
                            roleId = savedRole.id!!,
                        )
                    groupRoleAssignmentRepository.save(groupRoleAssignment)

                    // Add user to group
                    val groupMembership =
                        SecurityTestDataBuilders.groupMembership(
                            userId = userId,
                            groupId = savedGroup.id,
                        )
                    groupMembershipRepository.save(groupMembership)

                    // ABAC policy that explicitly denies - use explicit UUID string format
                    val userIdString = userId.toString()
                    val abacPolicy =
                        SecurityTestDataBuilders.abacPolicy(
                            name = "deny-policy",
                            effect = io.github.salomax.neotool.security.domain.abac.PolicyEffect.DENY,
                            condition = """{"eq": {"subject.userId": "$userIdString"}}""",
                            isActive = true,
                        )
                    abacPolicyRepository.save(abacPolicy)
                    entityManager.flush()
                    userId
                }

            // Act
            val result =
                authorizationService.checkPermission(
                    userId = userId,
                    permission = "transaction:read",
                )

            // Assert
            assertThat(result.allowed)
                .withFailMessage("Expected access to be denied when ABAC explicitly denies. Reason: ${result.reason}")
                .isFalse()
            assertThat(result.reason)
                .withFailMessage(
                    "Expected reason to contain 'ABAC policy explicitly denies', but got: ${result.reason}",
                )
                .contains("ABAC policy explicitly denies")
        }
    }
}

}






