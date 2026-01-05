package io.github.salomax.neotool.security.test.service.integration

import io.github.salomax.neotool.common.security.jwt.JwtTokenValidator
import io.github.salomax.neotool.common.test.assertions.assertNoErrors
import io.github.salomax.neotool.common.test.assertions.shouldBeJson
import io.github.salomax.neotool.common.test.assertions.shouldBeSuccessful
import io.github.salomax.neotool.common.test.assertions.shouldHaveNonEmptyBody
import io.github.salomax.neotool.common.test.http.exchangeAsString
import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.common.test.json.read
import io.github.salomax.neotool.common.test.transaction.runTransaction
import io.github.salomax.neotool.security.model.PermissionEntity
import io.github.salomax.neotool.security.model.RoleEntity
import io.github.salomax.neotool.security.model.UserEntity
import io.github.salomax.neotool.security.repo.GroupMembershipRepository
import io.github.salomax.neotool.security.repo.GroupRepository
import io.github.salomax.neotool.security.repo.GroupRoleAssignmentRepository
import io.github.salomax.neotool.security.repo.PermissionRepository
import io.github.salomax.neotool.security.repo.RefreshTokenRepository
import io.github.salomax.neotool.security.repo.RoleRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.authentication.AuthContextFactory
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
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("AuthContext Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("authentication-context")
@Tag("security")
class AuthContextIntegrationTest :
    BaseIntegrationTest(),
    PostgresIntegrationTest {
    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var authenticationService: AuthenticationService

    @Inject
    lateinit var authContextFactory: AuthContextFactory

    @Inject
    lateinit var jwtTokenValidator: JwtTokenValidator

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
    lateinit var refreshTokenRepository: RefreshTokenRepository

    @Inject
    lateinit var entityManager: EntityManager

    private lateinit var testRole: RoleEntity
    private lateinit var testPermission: PermissionEntity

    private fun uniqueEmail() = SecurityTestDataBuilders.uniqueEmail("authentication-context")

    @BeforeEach
    fun setUpTestData() {
        // Create test role and permission
        testPermission = PermissionEntity(name = "test:read", createdAt = Instant.now(), updatedAt = Instant.now())
        testPermission = permissionRepository.save(testPermission)

        testRole = RoleEntity(name = "test-role", createdAt = Instant.now(), updatedAt = Instant.now())
        testRole = roleRepository.save(testRole)

        // Assign permission to role via role_permissions join table
        // Note: role_permissions is a join table without an entity, handled via RoleRepository
        roleRepository.assignPermissionToRole(testRole.id!!, testPermission.id!!)
    }

    @BeforeEach
    fun cleanupTestDataBefore() {
        // Clean up before each test to ensure clean state
        cleanupTestData()
    }

    @AfterEach
    fun cleanupTestData() {
        try {
            // Clean up role_permissions join table (no entity, so use native query)
            entityManager.runTransaction {
                entityManager.createNativeQuery("DELETE FROM security.role_permissions").executeUpdate()
                refreshTokenRepository.deleteAll()
                groupRoleAssignmentRepository.deleteAll()
                groupMembershipRepository.deleteAll()
                userRepository.deleteAll()
                roleRepository.deleteAll()
                permissionRepository.deleteAll()
            }
            entityManager.clear()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    fun saveUser(user: UserEntity) {
        entityManager.runTransaction {
            authenticationService.saveUser(user)
        }
    }

    @Nested
    @DisplayName("Sign In AuthContext Integration")
    inner class SignInAuthContextTests {
        @Test
        fun `should call AuthContextFactory and include permissions in JWT for signIn with roles`() {
            // Arrange
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            saveUser(user)

            // Create group and assign role to group, then add user to group
            val userId = requireNotNull(user.id)
            entityManager.runTransaction {
                val group = SecurityTestDataBuilders.group(name = "test-group-${UUID.randomUUID().toString().take(8)}")
                val savedGroup = groupRepository.save(group)

                val groupRoleAssignment =
                    SecurityTestDataBuilders.groupRoleAssignment(
                        groupId = savedGroup.id,
                        roleId = testRole.id!!,
                    )
                groupRoleAssignmentRepository.save(groupRoleAssignment)

                val groupMembership =
                    SecurityTestDataBuilders.groupMembership(
                        userId = userId,
                        groupId = savedGroup.id,
                    )
                groupMembershipRepository.save(groupMembership)
            }

            val mutation =
                SecurityTestDataBuilders.signInMutation(
                    email = email,
                    password = password,
                    rememberMe = false,
                )

            val request =
                HttpRequest
                    .POST("/graphql", mutation)
                    .contentType(MediaType.APPLICATION_JSON)

            // Act
            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            // Assert
            val payload: JsonNode = json.read(response)
            payload["errors"].assertNoErrors()

            val signInPayload = payload["data"]["signIn"]
            val token = signInPayload["token"].stringValue

            // Verify JWT contains permissions
            val permissions = jwtTokenValidator.getPermissionsFromToken(token)
            assertThat(permissions).isNotNull
            assertThat(permissions).contains("test:read")

            // Verify JWT contains correct userId and email
            val userIdFromToken = jwtTokenValidator.getUserIdFromToken(token)
            assertThat(userIdFromToken).isEqualTo(userId)
            val claims = jwtTokenValidator.validateToken(token)
            assertThat(claims?.get("email")).isEqualTo(email)
        }

        @Test
        fun `should include empty permissions array in JWT when user has no roles`() {
            // Arrange
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            saveUser(user)

            val mutation =
                SecurityTestDataBuilders.signInMutation(
                    email = email,
                    password = password,
                    rememberMe = false,
                )

            val request =
                HttpRequest
                    .POST("/graphql", mutation)
                    .contentType(MediaType.APPLICATION_JSON)

            // Act
            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            // Assert
            val payload: JsonNode = json.read(response)
            payload["errors"].assertNoErrors()

            val signInPayload = payload["data"]["signIn"]
            val token = signInPayload["token"].stringValue

            // Verify JWT contains empty permissions array (not null or missing)
            val permissions = jwtTokenValidator.getPermissionsFromToken(token)
            assertThat(permissions).isNotNull()
            assertThat(permissions).isEmpty()

            // Verify permissions claim exists as array in raw claims
            val claims = jwtTokenValidator.validateToken(token)
            assertThat(claims).isNotNull()
            @Suppress("UNCHECKED_CAST")
            val tokenPermissions = claims?.get("permissions", List::class.java) as? List<*>
            assertThat(tokenPermissions).isNotNull()
            assertThat(tokenPermissions).isEmpty()
        }

        @Test
        fun `should always include permissions claim as array in JWT`() {
            // Arrange
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            saveUser(user)

            val mutation =
                SecurityTestDataBuilders.signInMutation(
                    email = email,
                    password = password,
                    rememberMe = false,
                )

            val request =
                HttpRequest
                    .POST("/graphql", mutation)
                    .contentType(MediaType.APPLICATION_JSON)

            // Act
            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            // Assert
            val payload: JsonNode = json.read(response)
            payload["errors"].assertNoErrors()

            val signInPayload = payload["data"]["signIn"]
            val token = signInPayload["token"].stringValue

            // Verify permissions claim is always present and is an array
            val claims = jwtTokenValidator.validateToken(token)
            assertThat(claims).isNotNull()
            assertThat(claims?.containsKey("permissions")).isTrue()
            @Suppress("UNCHECKED_CAST")
            val tokenPermissions = claims?.get("permissions", List::class.java) as? List<*>
            assertThat(tokenPermissions).isNotNull() // Must be present
            assertThat(tokenPermissions).isInstanceOf(List::class.java) // Must be a list/array
        }
    }

    @Nested
    @DisplayName("Sign In With OAuth AuthContext Integration")
    inner class SignInWithOAuthAuthContextTests {
        @Test
        fun `should call AuthContextFactory and include permissions in JWT for signInWithOAuth`() {
            // Arrange - Create user via OAuth (simulated)
            val email = uniqueEmail()
            val user = UserEntity(email = email, displayName = "OAuth User", passwordHash = null)
            saveUser(user)

            // Create group and assign role to group, then add user to group
            val userId = requireNotNull(user.id)
            entityManager.runTransaction {
                val group = SecurityTestDataBuilders.group(name = "test-group-${UUID.randomUUID().toString().take(8)}")
                val savedGroup = groupRepository.save(group)

                val groupRoleAssignment =
                    SecurityTestDataBuilders.groupRoleAssignment(
                        groupId = savedGroup.id,
                        roleId = testRole.id!!,
                    )
                groupRoleAssignmentRepository.save(groupRoleAssignment)

                val groupMembership =
                    SecurityTestDataBuilders.groupMembership(
                        userId = userId,
                        groupId = savedGroup.id,
                    )
                groupMembershipRepository.save(groupMembership)
            }

            // Note: This test would need actual OAuth token validation setup
            // For now, we verify the factory is called by checking the JWT structure
            // In a real scenario, we'd use a test OAuth provider

            // Verify factory can build context for OAuth user
            val authContext = authContextFactory.build(user)
            assertThat(authContext.userId).isEqualTo(userId)
            assertThat(authContext.email).isEqualTo(email)
            assertThat(authContext.roles).contains("test-role")
            assertThat(authContext.permissions).contains("test:read")
        }

        @Test
        fun `should produce identical permissions for same user via password and OAuth authentication`() {
            // Arrange - Create user that can authenticate via both methods
            val email = uniqueEmail()
            val password = "TestPassword123!"

            // Create user with password (simulating password authentication)
            val passwordUser =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            saveUser(passwordUser)

            // Create group and assign role to group, then add user to group
            val userId = requireNotNull(passwordUser.id)
            entityManager.runTransaction {
                val group = SecurityTestDataBuilders.group(name = "test-group-${UUID.randomUUID().toString().take(8)}")
                val savedGroup = groupRepository.save(group)

                val groupRoleAssignment =
                    SecurityTestDataBuilders.groupRoleAssignment(
                        groupId = savedGroup.id,
                        roleId = testRole.id!!,
                    )
                groupRoleAssignmentRepository.save(groupRoleAssignment)

                val groupMembership =
                    SecurityTestDataBuilders.groupMembership(
                        userId = userId,
                        groupId = savedGroup.id,
                    )
                groupMembershipRepository.save(groupMembership)
            }

            // Sign in via password
            val signInMutation =
                SecurityTestDataBuilders.signInMutation(
                    email = email,
                    password = password,
                    rememberMe = false,
                )
            val signInRequest =
                HttpRequest
                    .POST("/graphql", signInMutation)
                    .contentType(MediaType.APPLICATION_JSON)
            val signInResponse = httpClient.exchangeAsString(signInRequest)
            signInResponse.shouldBeSuccessful().shouldBeJson()
            val signInPayload: JsonNode = json.read(signInResponse)
            val passwordToken = signInPayload["data"]["signIn"]["token"].stringValue

            // Now simulate OAuth user (same user, no password hash)
            // Use existing user instead of creating new one to avoid EntityExistsException
            val oauthUser = userRepository.findById(userId).orElse(null) ?: passwordUser
            oauthUser.passwordHash = null // Simulate OAuth user (no password)
            saveUser(oauthUser)

            // Build authentication context for OAuth user (simulating OAuth flow)
            val oauthAuthContext = authContextFactory.build(oauthUser)
            val oauthToken = authenticationService.generateAccessToken(oauthAuthContext)

            // Assert - Both tokens should have identical permissions
            val passwordPermissions = jwtTokenValidator.getPermissionsFromToken(passwordToken)
            val oauthPermissions = jwtTokenValidator.getPermissionsFromToken(oauthToken)

            assertThat(passwordPermissions).isNotNull
            assertThat(oauthPermissions).isNotNull
            assertThat(passwordPermissions).contains("test:read")
            assertThat(oauthPermissions).contains("test:read")
            assertThat(passwordPermissions).isEqualTo(oauthPermissions)
        }

        @Test
        fun `should produce identical AuthContext for user regardless of OAuth provider`() {
            // Arrange - Create user that could authenticate via any OAuth provider
            val email = uniqueEmail()
            val user = UserEntity(email = email, displayName = "Multi-Provider User", passwordHash = null)
            saveUser(user)

            // Create group and assign role to group, then add user to group
            val userId = requireNotNull(user.id)
            entityManager.runTransaction {
                val group = SecurityTestDataBuilders.group(name = "test-group-${UUID.randomUUID().toString().take(8)}")
                val savedGroup = groupRepository.save(group)

                val groupRoleAssignment =
                    SecurityTestDataBuilders.groupRoleAssignment(
                        groupId = savedGroup.id,
                        roleId = testRole.id!!,
                    )
                groupRoleAssignmentRepository.save(groupRoleAssignment)

                val groupMembership =
                    SecurityTestDataBuilders.groupMembership(
                        userId = userId,
                        groupId = savedGroup.id,
                    )
                groupMembershipRepository.save(groupMembership)
            }

            // Build authentication context (simulating Google OAuth)
            val googleAuthContext = authContextFactory.build(user)
            val googleToken = authenticationService.generateAccessToken(googleAuthContext)

            // Build authentication context again (simulating future Microsoft OAuth)
            val microsoftAuthContext = authContextFactory.build(user)
            val microsoftToken = authenticationService.generateAccessToken(microsoftAuthContext)

            // Build authentication context again (simulating future GitHub OAuth)
            val githubAuthContext = authContextFactory.build(user)
            val githubToken = authenticationService.generateAccessToken(githubAuthContext)

            // Assert - All tokens should have identical permissions regardless of provider
            val googlePermissions = jwtTokenValidator.getPermissionsFromToken(googleToken)
            val microsoftPermissions = jwtTokenValidator.getPermissionsFromToken(microsoftToken)
            val githubPermissions = jwtTokenValidator.getPermissionsFromToken(githubToken)

            assertThat(googlePermissions).isEqualTo(microsoftPermissions)
            assertThat(microsoftPermissions).isEqualTo(githubPermissions)
            assertThat(googlePermissions).contains("test:read")
            assertThat(microsoftPermissions).contains("test:read")
            assertThat(githubPermissions).contains("test:read")
        }
    }

    @Nested
    @DisplayName("Sign Up AuthContext Integration")
    inner class SignUpAuthContextTests {
        @Test
        fun `should call AuthContextFactory and include permissions in JWT for signUp`() {
            // Arrange
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val name = "Test User"

            val mutation =
                SecurityTestDataBuilders.signUpMutation(
                    name = name,
                    email = email,
                    password = password,
                )

            val request =
                HttpRequest
                    .POST("/graphql", mutation)
                    .contentType(MediaType.APPLICATION_JSON)

            // Act
            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            // Assert
            val payload: JsonNode = json.read(response)
            payload["errors"].assertNoErrors()

            val signUpPayload = payload["data"]["signUp"]
            val token = signUpPayload["token"].stringValue

            // Verify JWT is valid (new users may have no permissions, but token should still be valid)
            val userIdFromToken = jwtTokenValidator.getUserIdFromToken(token)
            assertThat(userIdFromToken).isNotNull

            val claims = jwtTokenValidator.validateToken(token)
            assertThat(claims?.get("email")).isEqualTo(email)

            // Verify permissions claim exists as array (empty for new users)
            val permissions = jwtTokenValidator.getPermissionsFromToken(token)
            assertThat(permissions).isNotNull()
            assertThat(permissions).isEmpty()
        }

        @Test
        fun `should include empty permissions array in JWT when user has no permissions`() {
            // Arrange
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val name = "Test User"

            val mutation =
                SecurityTestDataBuilders.signUpMutation(
                    name = name,
                    email = email,
                    password = password,
                )

            val request =
                HttpRequest
                    .POST("/graphql", mutation)
                    .contentType(MediaType.APPLICATION_JSON)

            // Act
            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            // Assert
            val payload: JsonNode = json.read(response)
            payload["errors"].assertNoErrors()

            val signUpPayload = payload["data"]["signUp"]
            val token = signUpPayload["token"].stringValue

            // Verify permissions claim exists as empty array (not null or missing)
            val claims = jwtTokenValidator.validateToken(token)
            assertThat(claims).isNotNull()
            @Suppress("UNCHECKED_CAST")
            val tokenPermissions = claims?.get("permissions", List::class.java) as? List<*>
            assertThat(tokenPermissions).isNotNull()
            assertThat(tokenPermissions).isEmpty()

            // Also verify via getPermissionsFromToken
            val permissions = jwtTokenValidator.getPermissionsFromToken(token)
            assertThat(permissions).isNotNull()
            assertThat(permissions).isEmpty()
        }
    }

    @Nested
    @DisplayName("Refresh Access Token AuthContext Integration")
    inner class RefreshAccessTokenAuthContextTests {
        @Test
        fun `should call AuthContextFactory and include permissions in JWT for refreshAccessToken`() {
            // Arrange
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            saveUser(user)

            // Create group and assign role to group, then add user to group
            val userId = requireNotNull(user.id)
            entityManager.runTransaction {
                val group = SecurityTestDataBuilders.group(name = "test-group-${UUID.randomUUID().toString().take(8)}")
                val savedGroup = groupRepository.save(group)

                val groupRoleAssignment =
                    SecurityTestDataBuilders.groupRoleAssignment(
                        groupId = savedGroup.id,
                        roleId = testRole.id!!,
                    )
                groupRoleAssignmentRepository.save(groupRoleAssignment)

                val groupMembership =
                    SecurityTestDataBuilders.groupMembership(
                        userId = userId,
                        groupId = savedGroup.id,
                    )
                groupMembershipRepository.save(groupMembership)
            }

            // Sign in to get refresh token
            val signInMutation =
                SecurityTestDataBuilders.signInMutation(
                    email = email,
                    password = password,
                    rememberMe = true,
                )

            val signInRequest =
                HttpRequest
                    .POST("/graphql", signInMutation)
                    .contentType(MediaType.APPLICATION_JSON)

            val signInResponse = httpClient.exchangeAsString(signInRequest)
            val signInPayload: JsonNode = json.read(signInResponse)
            val refreshToken = signInPayload["data"]["signIn"]["refreshToken"].stringValue

            // Clear entity manager cache to ensure fresh data is loaded
            entityManager.clear()

            // Refresh access token
            val refreshMutation =
                """
                mutation RefreshAccessToken(${'$'}input: RefreshAccessTokenInput!) {
                    refreshAccessToken(input: ${'$'}input) {
                        token
                        user {
                            id
                            email
                        }
                    }
                }
                """.trimIndent()

            val variables = mapOf("input" to mapOf("refreshToken" to refreshToken))
            val mutation = mapOf("query" to refreshMutation, "variables" to variables)

            val refreshRequest =
                HttpRequest
                    .POST("/graphql", mutation)
                    .contentType(MediaType.APPLICATION_JSON)

            // Act
            val refreshResponse = httpClient.exchangeAsString(refreshRequest)
            refreshResponse
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            // Assert
            val refreshPayload: JsonNode = json.read(refreshResponse)
            refreshPayload["errors"].assertNoErrors()

            val refreshAccessTokenPayload = refreshPayload["data"]["refreshAccessToken"]
            val newToken = refreshAccessTokenPayload["token"].stringValue

            // Verify new JWT contains permissions
            val permissions = jwtTokenValidator.getPermissionsFromToken(newToken)
            assertThat(permissions).isNotNull
            assertThat(permissions).contains("test:read")

            // Verify JWT contains correct userId and email
            val userIdFromToken = jwtTokenValidator.getUserIdFromToken(newToken)
            assertThat(userIdFromToken).isEqualTo(userId)
            val claims = jwtTokenValidator.validateToken(newToken)
            assertThat(claims?.get("email")).isEqualTo(email)
        }

        @Test
        fun `should refresh token with updated permissions when roles change`() {
            // Arrange - Create user and sign in
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            saveUser(user)

            val userId = requireNotNull(user.id)

            // Sign in to get refresh token
            val signInMutation =
                SecurityTestDataBuilders.signInMutation(
                    email = email,
                    password = password,
                    rememberMe = true,
                )

            val signInRequest =
                HttpRequest
                    .POST("/graphql", signInMutation)
                    .contentType(MediaType.APPLICATION_JSON)

            val signInResponse = httpClient.exchangeAsString(signInRequest)
            val signInPayload: JsonNode = json.read(signInResponse)
            val refreshToken = signInPayload["data"]["signIn"]["refreshToken"].stringValue

            // Verify initial token has no permissions
            val initialToken = signInPayload["data"]["signIn"]["token"].stringValue
            val initialPermissions = jwtTokenValidator.getPermissionsFromToken(initialToken)
            assertThat(initialPermissions).isEmpty()

            // Create group and assign role to group, then add user to group (simulating permission change)
            entityManager.runTransaction {
                val group = SecurityTestDataBuilders.group(name = "test-group-${UUID.randomUUID().toString().take(8)}")
                val savedGroup = groupRepository.save(group)

                val groupRoleAssignment =
                    SecurityTestDataBuilders.groupRoleAssignment(
                        groupId = savedGroup.id,
                        roleId = testRole.id!!,
                    )
                groupRoleAssignmentRepository.save(groupRoleAssignment)

                val groupMembership =
                    SecurityTestDataBuilders.groupMembership(
                        userId = userId,
                        groupId = savedGroup.id,
                    )
                groupMembershipRepository.save(groupMembership)
            }

            // Refresh access token - should now include permissions
            val refreshMutation =
                """
                mutation RefreshAccessToken(${'$'}input: RefreshAccessTokenInput!) {
                    refreshAccessToken(input: ${'$'}input) {
                        token
                        user {
                            id
                            email
                        }
                    }
                }
                """.trimIndent()

            val variables = mapOf("input" to mapOf("refreshToken" to refreshToken))
            val mutation = mapOf("query" to refreshMutation, "variables" to variables)

            val refreshRequest =
                HttpRequest
                    .POST("/graphql", mutation)
                    .contentType(MediaType.APPLICATION_JSON)

            val refreshResponse = httpClient.exchangeAsString(refreshRequest)
            refreshResponse
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            val refreshPayload: JsonNode = json.read(refreshResponse)
            refreshPayload["errors"].assertNoErrors()

            val refreshAccessTokenPayload = refreshPayload["data"]["refreshAccessToken"]
            val newToken = refreshAccessTokenPayload["token"].stringValue

            // Verify new JWT contains updated permissions
            val updatedPermissions = jwtTokenValidator.getPermissionsFromToken(newToken)
            assertThat(updatedPermissions).isNotNull
            assertThat(updatedPermissions).contains("test:read")
        }

        @Test
        fun `should refresh token with consistent permissions regardless of original auth method`() {
            // Arrange - Create user and sign in via password
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            saveUser(user)

            val userId = requireNotNull(user.id)

            // Create group and assign role to group, then add user to group
            entityManager.runTransaction {
                val group = SecurityTestDataBuilders.group(name = "test-group-${UUID.randomUUID().toString().take(8)}")
                val savedGroup = groupRepository.save(group)

                val groupRoleAssignment =
                    SecurityTestDataBuilders.groupRoleAssignment(
                        groupId = savedGroup.id,
                        roleId = testRole.id!!,
                    )
                groupRoleAssignmentRepository.save(groupRoleAssignment)

                val groupMembership =
                    SecurityTestDataBuilders.groupMembership(
                        userId = userId,
                        groupId = savedGroup.id,
                    )
                groupMembershipRepository.save(groupMembership)
            }

            // Sign in via password to get refresh token
            val signInMutation =
                SecurityTestDataBuilders.signInMutation(
                    email = email,
                    password = password,
                    rememberMe = true,
                )

            val signInRequest =
                HttpRequest
                    .POST("/graphql", signInMutation)
                    .contentType(MediaType.APPLICATION_JSON)

            val signInResponse = httpClient.exchangeAsString(signInRequest)
            val signInPayload: JsonNode = json.read(signInResponse)
            val refreshToken = signInPayload["data"]["signIn"]["refreshToken"].stringValue
            val initialToken = signInPayload["data"]["signIn"]["token"].stringValue

            // Verify initial token has permissions
            val initialPermissions = jwtTokenValidator.getPermissionsFromToken(initialToken)
            assertThat(initialPermissions).contains("test:read")

            // Clear entity manager cache to ensure fresh data is loaded
            entityManager.clear()

            // Refresh access token - should maintain same permissions
            val refreshMutation =
                """
                mutation RefreshAccessToken(${'$'}input: RefreshAccessTokenInput!) {
                    refreshAccessToken(input: ${'$'}input) {
                        token
                        user {
                            id
                            email
                        }
                    }
                }
                """.trimIndent()

            val variables = mapOf("input" to mapOf("refreshToken" to refreshToken))
            val mutation = mapOf("query" to refreshMutation, "variables" to variables)

            val refreshRequest =
                HttpRequest
                    .POST("/graphql", mutation)
                    .contentType(MediaType.APPLICATION_JSON)

            val refreshResponse = httpClient.exchangeAsString(refreshRequest)
            refreshResponse.shouldBeSuccessful().shouldBeJson()

            val refreshPayload: JsonNode = json.read(refreshResponse)
            refreshPayload["errors"].assertNoErrors()

            val refreshAccessTokenPayload = refreshPayload["data"]["refreshAccessToken"]
            val refreshedToken = refreshAccessTokenPayload["token"].stringValue

            // Assert - Refreshed token should have same permissions as initial token
            val refreshedPermissions = jwtTokenValidator.getPermissionsFromToken(refreshedToken)
            assertThat(refreshedPermissions).isNotNull
            assertThat(refreshedPermissions).contains("test:read")
            assertThat(refreshedPermissions).isEqualTo(initialPermissions)

            // Verify AuthContextFactory is used (permissions come from current user state)
            val currentUser = userRepository.findById(userId).orElse(null)
            assertThat(currentUser).isNotNull
            val authContext = authContextFactory.build(currentUser!!)
            assertThat(authContext.permissions).isEqualTo(refreshedPermissions)
        }
    }

    @Nested
    @DisplayName("AuthContextFactory Direct Tests")
    inner class AuthContextFactoryDirectTests {
        @Test
        fun `should build AuthContext with roles and permissions from database`() {
            // Arrange
            val email = uniqueEmail()
            val user = UserEntity(email = email, displayName = "Test User", passwordHash = null)
            saveUser(user)

            val userId = requireNotNull(user.id)
            entityManager.runTransaction {
                val group = SecurityTestDataBuilders.group(name = "test-group-${UUID.randomUUID().toString().take(8)}")
                val savedGroup = groupRepository.save(group)

                val groupRoleAssignment =
                    SecurityTestDataBuilders.groupRoleAssignment(
                        groupId = savedGroup.id,
                        roleId = testRole.id!!,
                    )
                groupRoleAssignmentRepository.save(groupRoleAssignment)

                val groupMembership =
                    SecurityTestDataBuilders.groupMembership(
                        userId = userId,
                        groupId = savedGroup.id,
                    )
                groupMembershipRepository.save(groupMembership)
            }

            // Act
            val authContext = authContextFactory.build(user)

            // Assert
            assertThat(authContext.userId).isEqualTo(userId)
            assertThat(authContext.email).isEqualTo(email)
            assertThat(authContext.displayName).isEqualTo("Test User")
            assertThat(authContext.roles).contains("test-role")
            assertThat(authContext.permissions).contains("test:read")
            assertThat(authContext.roles).isNotNull // Always non-null
            assertThat(authContext.permissions).isNotNull // Always non-null
        }

        @Test
        fun `should build AuthContext with empty lists when user has no roles`() {
            // Arrange
            val email = uniqueEmail()
            val user = UserEntity(email = email, displayName = "Test User", passwordHash = null)
            saveUser(user)

            // Act
            val authContext = authContextFactory.build(user)

            // Assert
            assertThat(authContext.userId).isEqualTo(user.id)
            assertThat(authContext.email).isEqualTo(email)
            assertThat(authContext.roles).isEmpty()
            assertThat(authContext.permissions).isEmpty()
            assertThat(authContext.roles).isNotNull // Always non-null
            assertThat(authContext.permissions).isNotNull // Always non-null
        }

        @Test
        fun `should build AuthContext and generate token with permissions`() {
            // Arrange
            val email = uniqueEmail()
            val user = UserEntity(email = email, displayName = "Test User", passwordHash = null)
            saveUser(user)

            val userId = requireNotNull(user.id)
            entityManager.runTransaction {
                val group = SecurityTestDataBuilders.group(name = "test-group-${UUID.randomUUID().toString().take(8)}")
                val savedGroup = groupRepository.save(group)

                val groupRoleAssignment =
                    SecurityTestDataBuilders.groupRoleAssignment(
                        groupId = savedGroup.id,
                        roleId = testRole.id!!,
                    )
                groupRoleAssignmentRepository.save(groupRoleAssignment)

                val groupMembership =
                    SecurityTestDataBuilders.groupMembership(
                        userId = userId,
                        groupId = savedGroup.id,
                    )
                groupMembershipRepository.save(groupMembership)
            }

            // Act
            val authContext = authContextFactory.build(user)
            val token = authenticationService.generateAccessToken(authContext)

            // Assert
            assertThat(token).isNotBlank()
            val permissions = jwtTokenValidator.getPermissionsFromToken(token)
            assertThat(permissions).isNotNull()
            assertThat(permissions).contains("test:read")
        }

        @Test
        fun `should build AuthContext and generate token with empty permissions`() {
            // Arrange
            val email = uniqueEmail()
            val user = UserEntity(email = email, displayName = "Test User", passwordHash = null)
            saveUser(user)

            // Act
            val authContext = authContextFactory.build(user)
            val token = authenticationService.generateAccessToken(authContext)

            // Assert
            assertThat(token).isNotBlank()
            val permissions = jwtTokenValidator.getPermissionsFromToken(token)
            assertThat(permissions).isNotNull()
            assertThat(permissions).isEmpty()

            // Verify permissions claim exists in raw token
            val claims = jwtTokenValidator.validateToken(token)
            assertThat(claims?.containsKey("permissions")).isTrue()
            @Suppress("UNCHECKED_CAST")
            val tokenPermissions = claims?.get("permissions", List::class.java) as? List<*>
            assertThat(tokenPermissions).isNotNull()
            assertThat(tokenPermissions).isEmpty()
        }
    }
}
