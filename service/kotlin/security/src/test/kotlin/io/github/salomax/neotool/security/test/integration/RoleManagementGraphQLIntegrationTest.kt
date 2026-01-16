package io.github.salomax.neotool.security.test.integration

import io.github.salomax.neotool.common.test.assertions.assertNoErrors
import io.github.salomax.neotool.common.test.assertions.shouldBeJson
import io.github.salomax.neotool.common.test.assertions.shouldBeSuccessful
import io.github.salomax.neotool.common.test.assertions.shouldHaveNonEmptyBody
import io.github.salomax.neotool.common.test.http.exchangeAsString
import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.common.test.json.read
import io.github.salomax.neotool.common.test.transaction.runTransaction
import io.github.salomax.neotool.security.domain.rbac.SecurityPermissions
import io.github.salomax.neotool.security.model.UserEntity
import io.github.salomax.neotool.security.repo.PermissionRepository
import io.github.salomax.neotool.security.repo.RoleRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.authentication.AuthenticationService
import io.github.salomax.neotool.security.test.SecurityTestDataBuilders
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.json.tree.JsonNode
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import java.util.UUID

/**
 * Integration tests for Role Management GraphQL operations.
 * Tests cover role CRUD operations, permission assignments, and queries via GraphQL API.
 */
@MicronautTest(startApplication = true)
@DisplayName("Role Management GraphQL Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("graphql")
@Tag("security")
@Tag("role-management")
@TestMethodOrder(MethodOrderer.Random::class)
open class RoleManagementGraphQLIntegrationTest :
    BaseIntegrationTest(),
    PostgresIntegrationTest {
    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var roleRepository: RoleRepository

    @Inject
    lateinit var permissionRepository: PermissionRepository

    @Inject
    lateinit var authenticationService: AuthenticationService

    @Inject
    lateinit var entityManager: EntityManager

    private var testUserEmail = ""
    private var testUserPassword = ""
    private lateinit var testUser: UserEntity

    private fun uniqueEmail() = SecurityTestDataBuilders.uniqueEmail("role-graphql-test")

    private fun saveUser(user: UserEntity) {
        entityManager.runTransaction {
            authenticationService.saveUser(user)
            entityManager.flush()
        }
    }

    @BeforeEach
    fun setupTestUser() {
        testUserEmail = uniqueEmail()
        testUserPassword = "TestPassword123!"
        testUser =
            SecurityTestDataBuilders.userWithPassword(
                authenticationService = authenticationService,
                email = testUserEmail,
                password = testUserPassword,
                displayName = "Test Admin User",
            )
        saveUser(testUser)

        // Grant all required permissions in a single transaction
        // This matches the pattern used in GroupManagementIntegrationTest and SecurityGraphQLIntegrationTest
        val requiredPermissions =
            listOf(
                SecurityPermissions.SECURITY_ROLE_VIEW,
                SecurityPermissions.SECURITY_ROLE_SAVE,
                SecurityPermissions.SECURITY_ROLE_DELETE,
            )

        entityManager.runTransaction {
            // Create a single role for all permissions
            val role = roleRepository.save(SecurityTestDataBuilders.role(name = "test-role-${UUID.randomUUID()}"))

            // Link all permissions to the role
            requiredPermissions.forEach { permissionName ->
                val permission =
                    permissionRepository.findByName(permissionName).orElseGet {
                        permissionRepository.save(SecurityTestDataBuilders.permission(name = permissionName))
                    }
                roleRepository.assignPermissionToRole(role.id!!, permission.id!!)
            }

            // Create a single group
            val group = SecurityTestDataBuilders.group(name = "test-group-${UUID.randomUUID()}")
            entityManager.persist(group)
            entityManager.flush()

            // Assign role to group
            entityManager
                .createNativeQuery(
                    "INSERT INTO security.group_role_assignments (group_id, role_id) VALUES (:groupId, :roleId)",
                ).setParameter("groupId", group.id)
                .setParameter("roleId", role.id)
                .executeUpdate()

            // Add user to group
            entityManager
                .createNativeQuery(
                    "INSERT INTO security.group_memberships (group_id, user_id) VALUES (:groupId, :userId)",
                ).setParameter("groupId", group.id)
                .setParameter("userId", testUser.id)
                .executeUpdate()

            entityManager.flush()
        }
    }

    @AfterEach
    fun cleanupTestData() {
        try {
            // Clean up in reverse order of dependencies
            roleRepository.deleteAll()
            userRepository.deleteAll()
            entityManager.flush()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    private fun getAuthToken(): String {
        val signInMutation =
            SecurityTestDataBuilders.signInMutation(
                email = testUserEmail,
                password = testUserPassword,
            )

        val request =
            HttpRequest
                .POST("/graphql", signInMutation)
                .contentType(MediaType.APPLICATION_JSON)

        val response = httpClient.exchangeAsString(request)
        val payload: JsonNode = json.read(response)
        payload["errors"].assertNoErrors()
        return payload["data"]["signIn"]["token"].stringValue
    }

    @Nested
    @DisplayName("Role Query Operations")
    inner class RoleQueryTests {
        @Test
        fun `should query roles with pagination`() {
            val token = getAuthToken()

            entityManager.runTransaction {
                roleRepository.save(SecurityTestDataBuilders.role(name = "test_role_1"))
                roleRepository.save(SecurityTestDataBuilders.role(name = "test_role_2"))
                roleRepository.save(SecurityTestDataBuilders.role(name = "test_role_3"))
            }

            val query =
                mapOf(
                    "query" to
                        """
                        query {
                            roles(first: 2) {
                                edges {
                                    node {
                                        id
                                        name
                                    }
                                    cursor
                                }
                                pageInfo {
                                    hasNextPage
                                    hasPreviousPage
                                }
                                totalCount
                            }
                        }
                        """.trimIndent(),
                )

            val request =
                HttpRequest
                    .POST("/graphql", query)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer $token")

            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            payload["errors"].assertNoErrors()

            val rolesConnection = payload["data"]["roles"]
            assertThat(rolesConnection["edges"].size()).isEqualTo(2)
            assertThat(rolesConnection["totalCount"].intValue).isGreaterThanOrEqualTo(3)
            assertThat(rolesConnection["pageInfo"]["hasNextPage"].booleanValue).isTrue()
        }

        @Test
        fun `should query single role by ID`() {
            val token = getAuthToken()

            val roleId =
                entityManager.runTransaction {
                    val role = roleRepository.save(SecurityTestDataBuilders.role(name = "test_single_role"))
                    role.id
                }

            val query =
                mapOf(
                    "query" to
                        """
                        query(${'$'}id: ID!) {
                            role(id: ${'$'}id) {
                                id
                                name
                            }
                        }
                        """.trimIndent(),
                    "variables" to
                        mapOf(
                            "id" to roleId.toString(),
                        ),
                )

            val request =
                HttpRequest
                    .POST("/graphql", query)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer $token")

            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            payload["errors"].assertNoErrors()

            val role = payload["data"]["role"]
            assertThat(role["id"].stringValue).isEqualTo(roleId.toString())
            assertThat(role["name"].stringValue).isEqualTo("test_single_role")
        }

        @Test
        fun `should search roles by query`() {
            val token = getAuthToken()

            entityManager.runTransaction {
                roleRepository.save(SecurityTestDataBuilders.role(name = "test_admin_role"))
                roleRepository.save(SecurityTestDataBuilders.role(name = "test_user_role"))
                roleRepository.save(SecurityTestDataBuilders.role(name = "test_guest_role"))
            }

            val query =
                mapOf(
                    "query" to
                        """
                        query(${'$'}searchQuery: String) {
                            roles(query: ${'$'}searchQuery, first: 10) {
                                edges {
                                    node {
                                        id
                                        name
                                    }
                                }
                                totalCount
                            }
                        }
                        """.trimIndent(),
                    "variables" to
                        mapOf(
                            "searchQuery" to "admin",
                        ),
                )

            val request =
                HttpRequest
                    .POST("/graphql", query)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer $token")

            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            payload["errors"].assertNoErrors()

            val rolesConnection = payload["data"]["roles"]
            assertThat(rolesConnection["edges"].size()).isGreaterThanOrEqualTo(1)

            val roleNames =
                (0 until rolesConnection["edges"].size()).map {
                    rolesConnection["edges"][it]["node"]["name"].stringValue
                }
            assertThat(roleNames).anyMatch { it.contains("admin", ignoreCase = true) }
        }
    }

    @Nested
    @DisplayName("Role Mutation Operations")
    inner class RoleMutationTests {
        @Test
        fun `should create role via GraphQL mutation`() {
            val token = getAuthToken()

            val mutation =
                mapOf(
                    "query" to
                        """
                        mutation(${'$'}input: CreateRoleInput!) {
                            createRole(input: ${'$'}input) {
                                id
                                name
                                createdAt
                            }
                        }
                        """.trimIndent(),
                    "variables" to
                        mapOf(
                            "input" to
                                mapOf(
                                    "name" to "test_new_role",
                                ),
                        ),
                )

            val request =
                HttpRequest
                    .POST("/graphql", mutation)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer $token")

            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            payload["errors"].assertNoErrors()

            val createdRole = payload["data"]["createRole"]
            assertThat(createdRole["name"].stringValue).isEqualTo("test_new_role")
            assertThat(createdRole["id"].stringValue).isNotNull()
            assertThat(createdRole["createdAt"].stringValue).isNotNull()

            // Verify role exists in database
            val roleId = createdRole["id"].stringValue
            val dbRole = entityManager.runTransaction { roleRepository.findById(UUID.fromString(roleId)) }
            assertThat(dbRole.isPresent).isTrue()
            assertThat(dbRole.get().name).isEqualTo("test_new_role")
        }

        @Test
        fun `should update role via GraphQL mutation`() {
            val token = getAuthToken()

            val roleId =
                entityManager.runTransaction {
                    val role = roleRepository.save(SecurityTestDataBuilders.role(name = "test_update_role"))
                    role.id
                }

            val mutation =
                mapOf(
                    "query" to
                        """
                        mutation(${'$'}roleId: ID!, ${'$'}input: UpdateRoleInput!) {
                            updateRole(roleId: ${'$'}roleId, input: ${'$'}input) {
                                id
                                name
                                updatedAt
                            }
                        }
                        """.trimIndent(),
                    "variables" to
                        mapOf(
                            "roleId" to roleId.toString(),
                            "input" to
                                mapOf(
                                    "name" to "test_updated_role",
                                ),
                        ),
                )

            val request =
                HttpRequest
                    .POST("/graphql", mutation)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer $token")

            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            payload["errors"].assertNoErrors()

            val updatedRole = payload["data"]["updateRole"]
            assertThat(updatedRole["id"].stringValue).isEqualTo(roleId.toString())
            assertThat(updatedRole["name"].stringValue).isEqualTo("test_updated_role")
            assertThat(updatedRole["updatedAt"].stringValue).isNotNull()

            // Verify changes in database - refresh the entity manager to see committed changes
            entityManager.clear()
            val dbRole = entityManager.runTransaction { roleRepository.findById(roleId!!) }
            assertThat(dbRole.isPresent).isTrue()
            assertThat(dbRole.get().name).isEqualTo("test_updated_role")
        }

        @Test
        fun `should delete role via GraphQL mutation`() {
            val token = getAuthToken()

            val roleId =
                entityManager.runTransaction {
                    val role = roleRepository.save(SecurityTestDataBuilders.role(name = "test_delete_role"))
                    role.id
                }

            val mutation =
                mapOf(
                    "query" to
                        """
                        mutation(${'$'}roleId: ID!) {
                            deleteRole(roleId: ${'$'}roleId)
                        }
                        """.trimIndent(),
                    "variables" to
                        mapOf(
                            "roleId" to roleId.toString(),
                        ),
                )

            val request =
                HttpRequest
                    .POST("/graphql", mutation)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer $token")

            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            payload["errors"].assertNoErrors()

            val deleted = payload["data"]["deleteRole"].booleanValue
            assertThat(deleted).isTrue()

            // Verify role no longer exists in database
            val dbRole = entityManager.runTransaction { roleRepository.findById(roleId!!) }
            assertThat(dbRole.isPresent).isFalse()
        }
    }

    @Nested
    @DisplayName("Role Permission Assignment Operations")
    inner class RolePermissionAssignmentTests {
        @Test
        fun `should assign permission to role via GraphQL mutation`() {
            val token = getAuthToken()

            val roleId =
                entityManager.runTransaction {
                    val role = roleRepository.save(SecurityTestDataBuilders.role(name = "test_role_for_permission"))
                    role.id
                }

            val permissionId =
                entityManager.runTransaction {
                    val permission =
                        permissionRepository.findByName(SecurityPermissions.SECURITY_USER_VIEW).orElseGet {
                            permissionRepository.save(
                                SecurityTestDataBuilders.permission(
                                    name = SecurityPermissions.SECURITY_USER_VIEW,
                                ),
                            )
                        }
                    permission.id!!
                }

            val mutation =
                mapOf(
                    "query" to
                        """
                        mutation(${'$'}roleId: ID!, ${'$'}permissionId: ID!) {
                            assignPermissionToRole(roleId: ${'$'}roleId, permissionId: ${'$'}permissionId) {
                                id
                                name
                            }
                        }
                        """.trimIndent(),
                    "variables" to
                        mapOf(
                            "roleId" to roleId.toString(),
                            "permissionId" to permissionId.toString(),
                        ),
                )

            val request =
                HttpRequest
                    .POST("/graphql", mutation)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer $token")

            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            payload["errors"].assertNoErrors()

            val role = payload["data"]["assignPermissionToRole"]
            assertThat(role["id"].stringValue).isEqualTo(roleId.toString())

            // Verify permission assignment in database
            val permissionIds = entityManager.runTransaction { roleRepository.findPermissionIdsByRoleId(roleId!!) }
            assertThat(permissionIds).contains(permissionId)
        }

        @Test
        fun `should remove permission from role via GraphQL mutation`() {
            val token = getAuthToken()

            val (roleId, permissionId) =
                entityManager.runTransaction {
                    val role = roleRepository.save(SecurityTestDataBuilders.role(name = "test_role_remove_permission"))
                    val permission =
                        permissionRepository.findByName(SecurityPermissions.SECURITY_USER_VIEW).orElseGet {
                            permissionRepository.save(
                                SecurityTestDataBuilders.permission(
                                    name = SecurityPermissions.SECURITY_USER_VIEW,
                                ),
                            )
                        }
                    roleRepository.assignPermissionToRole(role.id!!, permission.id!!)
                    Pair(role.id!!, permission.id!!)
                }

            val mutation =
                mapOf(
                    "query" to
                        """
                        mutation(${'$'}roleId: ID!, ${'$'}permissionId: ID!) {
                            removePermissionFromRole(roleId: ${'$'}roleId, permissionId: ${'$'}permissionId) {
                                id
                                name
                            }
                        }
                        """.trimIndent(),
                    "variables" to
                        mapOf(
                            "roleId" to roleId.toString(),
                            "permissionId" to permissionId.toString(),
                        ),
                )

            val request =
                HttpRequest
                    .POST("/graphql", mutation)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer $token")

            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            payload["errors"].assertNoErrors()

            val role = payload["data"]["removePermissionFromRole"]
            assertThat(role["id"].stringValue).isEqualTo(roleId.toString())

            // Verify permission removed from database
            val permissionIds = entityManager.runTransaction { roleRepository.findPermissionIdsByRoleId(roleId) }
            assertThat(permissionIds).doesNotContain(permissionId)
        }

        @Test
        fun `should query role with permissions relationship`() {
            val token = getAuthToken()

            val roleId =
                entityManager.runTransaction {
                    val role = roleRepository.save(SecurityTestDataBuilders.role(name = "test_role_with_permissions"))
                    val permission1 =
                        permissionRepository.findByName(SecurityPermissions.SECURITY_USER_VIEW).orElseGet {
                            permissionRepository.save(
                                SecurityTestDataBuilders.permission(
                                    name = SecurityPermissions.SECURITY_USER_VIEW,
                                ),
                            )
                        }
                    val permission2 =
                        permissionRepository.findByName(SecurityPermissions.SECURITY_GROUP_VIEW).orElseGet {
                            permissionRepository.save(
                                SecurityTestDataBuilders.permission(
                                    name = SecurityPermissions.SECURITY_GROUP_VIEW,
                                ),
                            )
                        }
                    roleRepository.assignPermissionToRole(role.id!!, permission1.id!!)
                    roleRepository.assignPermissionToRole(role.id!!, permission2.id!!)
                    role.id
                }

            val query =
                mapOf(
                    "query" to
                        """
                        query(${'$'}id: ID!) {
                            role(id: ${'$'}id) {
                                id
                                name
                                permissions {
                                    id
                                    name
                                }
                            }
                        }
                        """.trimIndent(),
                    "variables" to
                        mapOf(
                            "id" to roleId.toString(),
                        ),
                )

            val request =
                HttpRequest
                    .POST("/graphql", query)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer $token")

            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            payload["errors"].assertNoErrors()

            val role = payload["data"]["role"]
            assertThat(role["id"].stringValue).isEqualTo(roleId.toString())
            assertThat(role["permissions"].size()).isEqualTo(2)

            val permissionNames =
                (0 until role["permissions"].size()).map {
                    role["permissions"][it]["name"].stringValue
                }
            assertThat(permissionNames).contains(
                SecurityPermissions.SECURITY_USER_VIEW,
                SecurityPermissions.SECURITY_GROUP_VIEW,
            )
        }
    }
}
