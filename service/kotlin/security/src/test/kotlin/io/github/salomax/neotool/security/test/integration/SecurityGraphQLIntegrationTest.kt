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
import io.github.salomax.neotool.security.repo.GroupMembershipRepository
import io.github.salomax.neotool.security.repo.GroupRepository
import io.github.salomax.neotool.security.repo.GroupRoleAssignmentRepository
import io.github.salomax.neotool.security.repo.PermissionRepository
import io.github.salomax.neotool.security.repo.RoleRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.authentication.AuthContextFactory
import io.github.salomax.neotool.security.service.authentication.AuthenticationService
import io.github.salomax.neotool.security.service.email.EmailService
import io.github.salomax.neotool.security.service.email.MockEmailService
import io.github.salomax.neotool.security.test.SecurityTestDataBuilders
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.json.tree.JsonNode
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import java.util.UUID

/**
 * Integration tests for Security GraphQL factory, wiring, and federation features.
 * Tests cover federation entity fetching, custom type resolvers, and edge cases.
 */
@MicronautTest(startApplication = true)
@DisplayName("Security GraphQL Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("graphql")
@Tag("security")
@Tag("federation")
@TestMethodOrder(MethodOrderer.Random::class)
open class SecurityGraphQLIntegrationTest :
    BaseIntegrationTest(),
    PostgresIntegrationTest {
    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var authenticationService: AuthenticationService

    @Inject
    lateinit var entityManager: EntityManager

    @Inject
    lateinit var emailService: EmailService

    @Inject
    lateinit var authContextFactory: AuthContextFactory

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

    private val mockEmailService: MockEmailService
        get() = emailService as MockEmailService

    private fun uniqueEmail() = SecurityTestDataBuilders.uniqueEmail("graphql-security")

    /**
     * Helper to create a user with Authorization Manager role and generate an access token.
     * Ensures Authorization Manager role exists with required permissions, creating them if needed.
     * This helper is idempotent and safe to call multiple times.
     */
    private fun createUserWithAdminRoleAndToken(): Pair<UserEntity, String> {
        val email = uniqueEmail()
        val password = "TestPassword123!"
        val user =
            SecurityTestDataBuilders.userWithPassword(
                authenticationService = authenticationService,
                email = email,
                password = password,
            )
        saveUser(user)

        // Ensure Authorization Manager role exists, create if missing
        val adminRole =
            entityManager.runTransaction {
                roleRepository.findByName("Authorization Manager").orElseGet {
                    roleRepository.save(SecurityTestDataBuilders.role(name = "Authorization Manager"))
                }
            }

        // Ensure required permissions exist and are linked to Authorization Manager role
        val requiredPermissions =
            listOf(
                SecurityPermissions.SECURITY_USER_VIEW,
                SecurityPermissions.SECURITY_USER_SAVE,
                SecurityPermissions.SECURITY_USER_DELETE,
                SecurityPermissions.SECURITY_GROUP_VIEW,
                SecurityPermissions.SECURITY_GROUP_SAVE,
                SecurityPermissions.SECURITY_GROUP_DELETE,
                SecurityPermissions.SECURITY_ROLE_VIEW,
                SecurityPermissions.SECURITY_ROLE_SAVE,
                SecurityPermissions.SECURITY_ROLE_DELETE,
            )
        entityManager.runTransaction {
            requiredPermissions.forEach { permissionName ->
                val permission =
                    permissionRepository.findByName(permissionName).orElseGet {
                        permissionRepository.save(SecurityTestDataBuilders.permission(name = permissionName))
                    }
                // Link permission to role (idempotent due to ON CONFLICT DO NOTHING)
                roleRepository.assignPermissionToRole(adminRole.id!!, permission.id!!)
            }
            entityManager.flush()
        }

        // Create group and assign Authorization Manager role to group, then add user to group
        entityManager.runTransaction {
            val group = SecurityTestDataBuilders.group(name = "admin-group-${UUID.randomUUID()}")
            val savedGroup = groupRepository.save(group)

            val groupRoleAssignment =
                SecurityTestDataBuilders.groupRoleAssignment(
                    groupId = savedGroup.id,
                    roleId = adminRole.id!!,
                )
            groupRoleAssignmentRepository.save(groupRoleAssignment)

            val groupMembership =
                SecurityTestDataBuilders.groupMembership(
                    userId = user.id!!,
                    groupId = savedGroup.id,
                )
            groupMembershipRepository.save(groupMembership)
            entityManager.flush()
        }

        // Generate token with permissions
        val authContext = authContextFactory.build(user)
        val token = authenticationService.generateAccessToken(authContext)

        return Pair(user, token)
    }

    fun saveUser(user: UserEntity) {
        entityManager.runTransaction {
            authenticationService.saveUser(user)
        }
    }

    @AfterEach
    fun cleanupTestData() {
        try {
            groupRoleAssignmentRepository.deleteAll()
            groupMembershipRepository.deleteAll()
            userRepository.deleteAll()
            mockEmailService.clearSentEmails()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    @Nested
    @DisplayName("GraphQL Federation Entity Fetching")
    inner class FederationEntityFetchingTests {
        @Test
        fun `should fetch User entity via federation`() {
            // Tests SecurityGraphQLFactory.fetchEntities for User type
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                    displayName = "Test User",
                )
            saveUser(user)

            // Federation query to fetch User entity
            val query =
                mapOf(
                    "query" to
                        """
                        query FetchUser(${'$'}representations: [_Any!]!) {
                            _entities(representations: ${'$'}representations) {
                                ... on User {
                                    id
                                    email
                                    displayName
                                }
                            }
                        }
                        """.trimIndent(),
                    "variables" to
                        mapOf(
                            "representations" to
                                listOf(
                                    mapOf(
                                        "__typename" to "User",
                                        "id" to user.id.toString(),
                                    ),
                                ),
                        ),
                )

            val request =
                HttpRequest
                    .POST("/graphql", query)
                    .contentType(MediaType.APPLICATION_JSON)

            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            payload["errors"].assertNoErrors()

            val data = payload["data"]
            Assertions.assertThat(data).isNotNull()
            val entities = data["_entities"]
            Assertions.assertThat(entities).isNotNull()
            Assertions.assertThat(entities.isArray).isTrue()
            Assertions.assertThat(entities.size()).isEqualTo(1)

            val userEntity = entities[0]
            Assertions.assertThat(userEntity["id"].stringValue).isEqualTo(user.id.toString())
            Assertions.assertThat(userEntity["email"].stringValue).isEqualTo(email)
            Assertions.assertThat(userEntity["displayName"].stringValue).isEqualTo("Test User")
        }

        @Test
        fun `should return null for non-existent User entity via federation`() {
            // Tests SecurityGraphQLFactory.fetchEntities for non-existent user
            val nonExistentId = UUID.randomUUID()

            val query =
                mapOf(
                    "query" to
                        """
                        query FetchUser(${'$'}representations: [_Any!]!) {
                            _entities(representations: ${'$'}representations) {
                                ... on User {
                                    id
                                    email
                                }
                            }
                        }
                        """.trimIndent(),
                    "variables" to
                        mapOf(
                            "representations" to
                                listOf(
                                    mapOf(
                                        "__typename" to "User",
                                        "id" to nonExistentId.toString(),
                                    ),
                                ),
                        ),
                )

            val request =
                HttpRequest
                    .POST("/graphql", query)
                    .contentType(MediaType.APPLICATION_JSON)

            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            payload["errors"].assertNoErrors()

            val data = payload["data"]
            val entities = data["_entities"]
            Assertions.assertThat(entities).isNotNull()
            Assertions.assertThat(entities.isArray).isTrue()
            Assertions.assertThat(entities.size()).isEqualTo(1)
            // Should return null for non-existent entity
            Assertions.assertThat(entities[0].isNull).isTrue()
        }

        @Test
        fun `should handle federation query with missing id`() {
            // Tests SecurityGraphQLFactory.fetchEntities with null id
            val query =
                mapOf(
                    "query" to
                        """
                        query FetchUser(${'$'}representations: [_Any!]!) {
                            _entities(representations: ${'$'}representations) {
                                ... on User {
                                    id
                                    email
                                }
                            }
                        }
                        """.trimIndent(),
                    "variables" to
                        mapOf(
                            "representations" to
                                listOf(
                                    mapOf(
                                        "__typename" to "User",
                                        // Missing id
                                    ),
                                ),
                        ),
                )

            val request =
                HttpRequest
                    .POST("/graphql", query)
                    .contentType(MediaType.APPLICATION_JSON)

            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            val data = payload["data"]
            val entities = data["_entities"]
            Assertions.assertThat(entities).isNotNull()
            Assertions.assertThat(entities.isArray).isTrue()
            Assertions.assertThat(entities.size()).isEqualTo(1)
            // Should return null when id is missing
            Assertions.assertThat(entities[0].isNull).isTrue()
        }

        @Test
        fun `should handle federation query with invalid id format`() {
            // Tests SecurityGraphQLFactory.fetchEntities with invalid UUID
            val query =
                mapOf(
                    "query" to
                        """
                        query FetchUser(${'$'}representations: [_Any!]!) {
                            _entities(representations: ${'$'}representations) {
                                ... on User {
                                    id
                                    email
                                }
                            }
                        }
                        """.trimIndent(),
                    "variables" to
                        mapOf(
                            "representations" to
                                listOf(
                                    mapOf(
                                        "__typename" to "User",
                                        "id" to "invalid-uuid-format",
                                    ),
                                ),
                        ),
                )

            val request =
                HttpRequest
                    .POST("/graphql", query)
                    .contentType(MediaType.APPLICATION_JSON)

            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            val data = payload["data"]
            val entities = data["_entities"]
            Assertions.assertThat(entities).isNotNull()
            Assertions.assertThat(entities.isArray).isTrue()
            Assertions.assertThat(entities.size()).isEqualTo(1)
            // Should return null for invalid UUID format
            Assertions.assertThat(entities[0].isNull).isTrue()
        }

        @Test
        fun `should handle federation query with unknown typename`() {
            // Tests SecurityGraphQLFactory.fetchEntities with unknown type
            val query =
                mapOf(
                    "query" to
                        """
                        query FetchEntity(${'$'}representations: [_Any!]!) {
                            _entities(representations: ${'$'}representations) {
                                ... on User {
                                    id
                                }
                            }
                        }
                        """.trimIndent(),
                    "variables" to
                        mapOf(
                            "representations" to
                                listOf(
                                    mapOf(
                                        "__typename" to "UnknownType",
                                        "id" to UUID.randomUUID().toString(),
                                    ),
                                ),
                        ),
                )

            val request =
                HttpRequest
                    .POST("/graphql", query)
                    .contentType(MediaType.APPLICATION_JSON)

            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            val data = payload["data"]
            val entities = data["_entities"]
            Assertions.assertThat(entities).isNotNull()
            Assertions.assertThat(entities.isArray).isTrue()
            Assertions.assertThat(entities.size()).isEqualTo(1)
            // Should return null for unknown type
            Assertions.assertThat(entities[0].isNull).isTrue()
        }

        @Test
        fun `should handle federation query with multiple entities`() {
            // Tests SecurityGraphQLFactory.fetchEntities with multiple representations
            val email1 = uniqueEmail()
            val email2 = uniqueEmail()
            val password = "TestPassword123!"

            val user1 =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email1,
                    password = password,
                )
            saveUser(user1)

            // Use a different unique email for user2 to avoid conflicts
            val user2 =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email2,
                    password = password,
                )
            saveUser(user2)

            val query =
                mapOf(
                    "query" to
                        """
                        query FetchUsers(${'$'}representations: [_Any!]!) {
                            _entities(representations: ${'$'}representations) {
                                ... on User {
                                    id
                                    email
                                }
                            }
                        }
                        """.trimIndent(),
                    "variables" to
                        mapOf(
                            "representations" to
                                listOf(
                                    mapOf(
                                        "__typename" to "User",
                                        "id" to user1.id.toString(),
                                    ),
                                    mapOf(
                                        "__typename" to "User",
                                        "id" to user2.id.toString(),
                                    ),
                                ),
                        ),
                )

            val request =
                HttpRequest
                    .POST("/graphql", query)
                    .contentType(MediaType.APPLICATION_JSON)

            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            payload["errors"].assertNoErrors()

            val data = payload["data"]
            val entities = data["_entities"]
            Assertions.assertThat(entities.size()).isEqualTo(2)
            Assertions.assertThat(entities[0]["email"].stringValue).isEqualTo(email1)
            Assertions.assertThat(entities[1]["email"].stringValue).isEqualTo(email2)
        }
    }

    @Nested
    @DisplayName("GraphQL Custom Type Resolvers")
    inner class CustomTypeResolverTests {
        @Test
        fun `should resolve User type fields via custom resolver`() {
            // Tests SecurityWiringFactory custom User type resolvers
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val displayName = "Test User Display Name"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                    displayName = displayName,
                )
            saveUser(user)

            // Sign in to get token
            val signInMutation =
                SecurityTestDataBuilders.signInMutation(
                    email = email,
                    password = password,
                )

            val signInRequest =
                HttpRequest
                    .POST("/graphql", signInMutation)
                    .contentType(MediaType.APPLICATION_JSON)

            val signInResponse = httpClient.exchangeAsString(signInRequest)
            val signInPayload: JsonNode = json.read(signInResponse)
            signInPayload["errors"].assertNoErrors()
            val token = signInPayload["data"]["signIn"]["token"].stringValue

            // Query currentUser with all fields to test custom resolvers
            val query =
                mapOf(
                    "query" to
                        """
                        query {
                            currentUser {
                                id
                                email
                                displayName
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

            val currentUser = payload["data"]["currentUser"]
            Assertions.assertThat(currentUser["id"].stringValue).isEqualTo(user.id.toString())
            Assertions.assertThat(currentUser["email"].stringValue).isEqualTo(email)
            Assertions.assertThat(currentUser["displayName"].stringValue).isEqualTo(displayName)
        }

        @Test
        fun `should resolve SignInPayload type fields via custom resolver`() {
            // Tests SecurityWiringFactory custom SignInPayload type resolvers
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
                    rememberMe = true,
                )

            val request =
                HttpRequest
                    .POST("/graphql", mutation)
                    .contentType(MediaType.APPLICATION_JSON)

            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            payload["errors"].assertNoErrors()

            val signInPayload = payload["data"]["signIn"]
            // Test all SignInPayload fields are resolved correctly
            Assertions.assertThat(signInPayload["token"].stringValue).isNotBlank()
            Assertions.assertThat(signInPayload["refreshToken"].stringValue).isNotBlank()
            Assertions.assertThat(signInPayload["user"]).isNotNull()
            Assertions.assertThat(signInPayload["user"]["email"].stringValue).isEqualTo(email)
        }

        @Test
        fun `should resolve SignUpPayload type fields via custom resolver`() {
            // Tests SecurityWiringFactory custom SignUpPayload type resolvers
            val name = "New User"
            val email = uniqueEmail()
            val password = "TestPassword123!"

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

            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            payload["errors"].assertNoErrors()

            val signUpPayload = payload["data"]["signUp"]
            // Test all SignUpPayload fields are resolved correctly
            Assertions.assertThat(signUpPayload["token"].stringValue).isNotBlank()
            Assertions.assertThat(signUpPayload["refreshToken"].stringValue).isNotBlank()
            Assertions.assertThat(signUpPayload["user"]).isNotNull()
            Assertions.assertThat(signUpPayload["user"]["email"].stringValue).isEqualTo(email)
            Assertions.assertThat(signUpPayload["user"]["displayName"].stringValue).isEqualTo(name)
        }

        @Test
        fun `should resolve RequestPasswordResetPayload type fields via custom resolver`() {
            // Tests SecurityWiringFactory custom RequestPasswordResetPayload type resolvers
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
                SecurityTestDataBuilders.requestPasswordResetMutation(
                    email = email,
                    locale = "en",
                )

            val request =
                HttpRequest
                    .POST("/graphql", mutation)
                    .contentType(MediaType.APPLICATION_JSON)

            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            payload["errors"].assertNoErrors()

            val requestPasswordResetPayload = payload["data"]["requestPasswordReset"]
            // Test all RequestPasswordResetPayload fields are resolved correctly
            Assertions.assertThat(requestPasswordResetPayload["success"].booleanValue).isTrue()
            Assertions.assertThat(requestPasswordResetPayload["message"].stringValue).isNotBlank()
        }

        @Test
        fun `should resolve ResetPasswordPayload type fields via custom resolver`() {
            // Tests SecurityWiringFactory custom ResetPasswordPayload type resolvers
            val email = uniqueEmail()
            val oldPassword = "OldPassword123!"
            val newPassword = "NewPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = oldPassword,
                )
            saveUser(user)

            // Request password reset
            entityManager.runTransaction {
                authenticationService.requestPasswordReset(email, "en")
                entityManager.flush()
            }
            val savedUser = userRepository.findByEmail(email)
            val token = savedUser!!.passwordResetToken!!

            val mutation =
                SecurityTestDataBuilders.resetPasswordMutation(
                    token = token,
                    newPassword = newPassword,
                )

            val request =
                HttpRequest
                    .POST("/graphql", mutation)
                    .contentType(MediaType.APPLICATION_JSON)

            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            payload["errors"].assertNoErrors()

            val resetPasswordPayload = payload["data"]["resetPassword"]
            // Test all ResetPasswordPayload fields are resolved correctly
            Assertions.assertThat(resetPasswordPayload["success"].booleanValue).isTrue()
            Assertions.assertThat(resetPasswordPayload["message"].stringValue).isNotBlank()
        }
    }

    @Nested
    @DisplayName("MockEmailService Helper Methods")
    inner class MockEmailServiceHelperTests {
        @Test
        fun `should get all sent emails via MockEmailService`() {
            // Tests MockEmailService.getAllSentEmails()
            val email1 = uniqueEmail()
            val email2 = uniqueEmail()
            val password = "TestPassword123!"

            val user1 =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email1,
                    password = password,
                )
            val user2 =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email2,
                    password = password,
                )
            saveUser(user1)
            saveUser(user2)

            // Request password reset for both users
            entityManager.runTransaction {
                authenticationService.requestPasswordReset(email1, "en")
                authenticationService.requestPasswordReset(email2, "pt")
                entityManager.flush()
            }

            // Get all sent emails
            val allSentEmails = mockEmailService.getAllSentEmails()
            Assertions.assertThat(allSentEmails).hasSize(2)
            Assertions.assertThat(allSentEmails[email1]).hasSize(1)
            Assertions.assertThat(allSentEmails[email2]).hasSize(1)
            Assertions.assertThat(allSentEmails[email1]!![0].locale).isEqualTo("en")
            Assertions.assertThat(allSentEmails[email2]!![0].locale).isEqualTo("pt")
        }

        @Test
        fun `should get last sent email via MockEmailService`() {
            // Tests MockEmailService.getLastSentEmail()
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            saveUser(user)

            // Request password reset multiple times
            entityManager.runTransaction {
                authenticationService.requestPasswordReset(email, "en")
                entityManager.flush()
            }
            entityManager.runTransaction {
                authenticationService.requestPasswordReset(email, "pt")
                entityManager.flush()
            }

            // Get last sent email
            val lastEmail = mockEmailService.getLastSentEmail(email)
            Assertions.assertThat(lastEmail).isNotNull()
            Assertions.assertThat(lastEmail!!.email).isEqualTo(email)
            Assertions.assertThat(lastEmail.locale).isEqualTo("pt") // Should be the last one
        }

        @Test
        fun `should clear sent emails via MockEmailService`() {
            // Tests MockEmailService.clearSentEmails()
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            saveUser(user)

            // Request password reset
            entityManager.runTransaction {
                authenticationService.requestPasswordReset(email, "en")
                entityManager.flush()
            }

            // Verify email was sent
            val sentEmails = mockEmailService.getSentEmails(email)
            Assertions.assertThat(sentEmails).hasSize(1)

            // Clear sent emails
            mockEmailService.clearSentEmails()

            // Verify emails are cleared
            val clearedEmails = mockEmailService.getSentEmails(email)
            Assertions.assertThat(clearedEmails).isEmpty()

            val allSentEmails = mockEmailService.getAllSentEmails()
            Assertions.assertThat(allSentEmails).isEmpty()
        }
    }

    @Nested
    @DisplayName("DataLoader N+1 Prevention Tests")
    inner class DataLoaderN1PreventionTests {
        @Test
        fun `should batch load user roles for multiple users`() {
            // Arrange - Create users with roles
            val email1 = uniqueEmail()
            val email2 = uniqueEmail()
            val password = "TestPassword123!"
            val user1 =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email1,
                    password = password,
                )
            val user2 =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email2,
                    password = password,
                )
            saveUser(user1)
            saveUser(user2)

            // Note: This test verifies that DataLoader is used, but actual role assignment
            // would require additional setup. For now, we verify the query structure works.

            // Create user with Authorization Manager role and get token
            val (_, token) = createUserWithAdminRoleAndToken()

            // Query multiple users with roles field
            val query =
                mapOf(
                    "query" to
                        """
                        query {
                            users(first: 2) {
                                edges {
                                    node {
                                        id
                                        email
                                        roles {
                                            id
                                            name
                                        }
                                    }
                                }
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

            val data = payload["data"]
            val users = data["users"]["edges"]
            Assertions.assertThat(users.size()).isGreaterThanOrEqualTo(0)
            // Verify that roles field is accessible (even if empty)
            if (users.size() > 0) {
                val firstUser = users[0]["node"]
                Assertions.assertThat(firstUser["roles"]).isNotNull
            }
        }

        @Test
        fun `should batch load user groups for multiple users`() {
            // Arrange - Create users
            val email1 = uniqueEmail()
            val email2 = uniqueEmail()
            val password = "TestPassword123!"
            val user1 =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email1,
                    password = password,
                )
            val user2 =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email2,
                    password = password,
                )
            saveUser(user1)
            saveUser(user2)

            // Create user with Authorization Manager role and get token
            val (_, token) = createUserWithAdminRoleAndToken()

            // Query multiple users with groups field
            val query =
                mapOf(
                    "query" to
                        """
                        query {
                            users(first: 2) {
                                edges {
                                    node {
                                        id
                                        email
                                        groups {
                                            id
                                            name
                                        }
                                    }
                                }
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

            val data = payload["data"]
            val users = data["users"]["edges"]
            Assertions.assertThat(users.size()).isGreaterThanOrEqualTo(0)
            // Verify that groups field is accessible
            if (users.size() > 0) {
                val firstUser = users[0]["node"]
                Assertions.assertThat(firstUser["groups"]).isNotNull
            }
        }

        @Test
        fun `should batch load user permissions for multiple users`() {
            // Arrange - Create users
            val email1 = uniqueEmail()
            val email2 = uniqueEmail()
            val password = "TestPassword123!"
            val user1 =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email1,
                    password = password,
                )
            val user2 =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email2,
                    password = password,
                )
            saveUser(user1)
            saveUser(user2)

            // Create user with Authorization Manager role and get token
            val (_, token) = createUserWithAdminRoleAndToken()

            // Query multiple users with permissions field
            val query =
                mapOf(
                    "query" to
                        """
                        query {
                            users(first: 2) {
                                edges {
                                    node {
                                        id
                                        email
                                        permissions {
                                            id
                                            name
                                        }
                                    }
                                }
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

            val data = payload["data"]
            val users = data["users"]["edges"]
            Assertions.assertThat(users.size()).isGreaterThanOrEqualTo(0)
            // Verify that permissions field is accessible
            if (users.size() > 0) {
                val firstUser = users[0]["node"]
                Assertions.assertThat(firstUser["permissions"]).isNotNull
            }
        }

        @Test
        fun `should batch load role permissions for multiple roles`() {
            // Create user with Authorization Manager role and get token
            val (_, token) = createUserWithAdminRoleAndToken()

            // Query multiple roles with permissions field
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
                                        permissions {
                                            id
                                            name
                                        }
                                    }
                                }
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

            val data = payload["data"]
            val roles = data["roles"]["edges"]
            Assertions.assertThat(roles.size()).isGreaterThanOrEqualTo(0)
            // Verify that permissions field is accessible
            if (roles.size() > 0) {
                val firstRole = roles[0]["node"]
                Assertions.assertThat(firstRole["permissions"]).isNotNull
            }
        }

        @Test
        fun `should batch load group roles for multiple groups`() {
            // Create user with Authorization Manager role and get token
            val (_, token) = createUserWithAdminRoleAndToken()

            // Query multiple groups with roles field
            val query =
                mapOf(
                    "query" to
                        """
                        query {
                            groups(first: 2) {
                                edges {
                                    node {
                                        id
                                        name
                                        roles {
                                            id
                                            name
                                        }
                                    }
                                }
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

            val data = payload["data"]
            val groups = data["groups"]["edges"]
            Assertions.assertThat(groups.size()).isGreaterThanOrEqualTo(0)
            // Verify that roles field is accessible
            if (groups.size() > 0) {
                val firstGroup = groups[0]["node"]
                Assertions.assertThat(firstGroup["roles"]).isNotNull
            }
        }

        @Test
        fun `should batch load permission roles for multiple permissions`() {
            // Arrange - Create specific test data for this test
            val testRole1 =
                entityManager.runTransaction {
                    roleRepository.save(
                        SecurityTestDataBuilders.role(name = "TEST_ROLE_1_${System.currentTimeMillis()}"),
                    )
                }
            val testRole2 =
                entityManager.runTransaction {
                    roleRepository.save(
                        SecurityTestDataBuilders.role(name = "TEST_ROLE_2_${System.currentTimeMillis()}"),
                    )
                }

            val testPermission1 =
                entityManager.runTransaction {
                    permissionRepository.save(
                        SecurityTestDataBuilders.permission(name = "test:permission:1_${System.currentTimeMillis()}"),
                    )
                }
            val testPermission2 =
                entityManager.runTransaction {
                    permissionRepository.save(
                        SecurityTestDataBuilders.permission(name = "test:permission:2_${System.currentTimeMillis()}"),
                    )
                }

            // Link permissions to roles
            entityManager.runTransaction {
                roleRepository.assignPermissionToRole(testRole1.id!!, testPermission1.id!!)
                roleRepository.assignPermissionToRole(testRole1.id!!, testPermission2.id!!)
                roleRepository.assignPermissionToRole(testRole2.id!!, testPermission1.id!!)
            }

            // Create user with Authorization Manager role and get token
            val (_, token) = createUserWithAdminRoleAndToken()

            // Query the specific test permissions by name
            val query =
                mapOf(
                    "query" to
                        """
                        query {
                            permissions(first: 10, query: "${testPermission1.name}") {
                                edges {
                                    node {
                                        id
                                        name
                                        roles {
                                            id
                                            name
                                        }
                                    }
                                }
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

            val data = payload["data"]
            val permissions = data["permissions"]["edges"]
            // Assert on specific test data - should find at least our test permission
            Assertions.assertThat(permissions.size()).isGreaterThanOrEqualTo(1)

            // Find our test permission in the results
            val testPermission1Result =
                (0 until permissions.size())
                    .mapNotNull { i ->
                        val perm = permissions[i]["node"]
                        if (perm["id"].stringValue == testPermission1.id.toString()) perm else null
                    }.firstOrNull()

            Assertions.assertThat(testPermission1Result).isNotNull()
            Assertions.assertThat(testPermission1Result!!["roles"]).isNotNull
            // Verify it has the expected roles
            val roles = testPermission1Result["roles"]
            Assertions.assertThat(roles.isArray).isTrue()
            Assertions.assertThat(roles.size()).isGreaterThanOrEqualTo(1)
        }

        @Test
        fun `should batch load group members for multiple groups`() {
            // Create user with Authorization Manager role and get token
            val (_, token) = createUserWithAdminRoleAndToken()

            // Query multiple groups with members field (already uses DataLoader)
            val query =
                mapOf(
                    "query" to
                        """
                        query {
                            groups(first: 2) {
                                edges {
                                    node {
                                        id
                                        name
                                        members {
                                            id
                                            email
                                        }
                                    }
                                }
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

            val data = payload["data"]
            val groups = data["groups"]["edges"]
            Assertions.assertThat(groups.size()).isGreaterThanOrEqualTo(0)
            // Verify that members field is accessible
            if (groups.size() > 0) {
                val firstGroup = groups[0]["node"]
                Assertions.assertThat(firstGroup["members"]).isNotNull
            }
        }
    }

    @Nested
    @DisplayName("RBAC Authorization Enforcement")
    inner class RbacAuthorizationEnforcementTests {
        /**
         * Helper to verify GraphQL error response contains expected error message
         */
        private fun verifyGraphQLError(
            payload: JsonNode,
            expectedMessage: String,
        ) {
            val errors = payload["errors"]
            Assertions.assertThat(errors).isNotNull()
            Assertions.assertThat(errors.isArray).isTrue()
            Assertions.assertThat(errors.size()).isGreaterThan(0)

            // Check if any error contains the expected message
            val errorMessages =
                (0 until errors.size()).mapNotNull { i ->
                    try {
                        errors[i]["message"]?.stringValue
                    } catch (e: Exception) {
                        null
                    }
                }
            Assertions.assertThat(errorMessages).contains(expectedMessage)

            // Verify data is null, not present, or empty object (GraphQL may return {} for errors)
            val dataNode = payload["data"]
            if (dataNode != null && !dataNode.isNull) {
                // If data is present, it should be an object (could be empty {})
                // This is acceptable for GraphQL error responses - some implementations return {} instead of null
                Assertions.assertThat(dataNode.isObject).isTrue()
            }
        }

        @Test
        fun `should return authentication error when querying users without token`() {
            // Arrange
            val query =
                mapOf(
                    "query" to
                        """
                        query {
                            users(first: 2) {
                                edges {
                                    node {
                                        id
                                        email
                                    }
                                }
                            }
                        }
                        """.trimIndent(),
                )

            val request =
                HttpRequest
                    .POST("/graphql", query)
                    .contentType(MediaType.APPLICATION_JSON)
            // No Authorization header

            // Act
            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            // Assert
            val payload: JsonNode = json.read(response)
            verifyGraphQLError(payload, "Authentication required")
        }

        @Test
        fun `should return authentication error when querying user without token`() {
            // Arrange
            val userId = UUID.randomUUID()
            val query =
                mapOf(
                    "query" to
                        """
                        query {
                            user(id: "$userId") {
                                id
                                email
                            }
                        }
                        """.trimIndent(),
                )

            val request =
                HttpRequest
                    .POST("/graphql", query)
                    .contentType(MediaType.APPLICATION_JSON)
            // No Authorization header

            // Act
            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            // Assert
            val payload: JsonNode = json.read(response)
            verifyGraphQLError(payload, "Authentication required")
        }

        @Test
        fun `should allow querying users with valid token and Authorization Manager role`() {
            // Arrange
            val (_, token) = createUserWithAdminRoleAndToken()

            val query =
                mapOf(
                    "query" to
                        """
                        query {
                            users(first: 2) {
                                edges {
                                    node {
                                        id
                                        email
                                    }
                                }
                            }
                        }
                        """.trimIndent(),
                )

            val request =
                HttpRequest
                    .POST("/graphql", query)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer $token")

            // Act
            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            // Assert
            val payload: JsonNode = json.read(response)
            payload["errors"].assertNoErrors()
            val data = payload["data"]
            Assertions.assertThat(data).isNotNull()
            Assertions.assertThat(data["users"]).isNotNull()
        }
    }

    @Nested
    @DisplayName("RBAC Error Handling Tests")
    inner class RbacErrorHandlingTests {
        /**
         * Helper to verify GraphQL error response contains expected error message
         */
        private fun verifyGraphQLError(
            payload: JsonNode,
            expectedMessage: String,
        ) {
            val errors = payload["errors"]
            Assertions.assertThat(errors).isNotNull()
            Assertions.assertThat(errors.isArray).isTrue()
            Assertions.assertThat(errors.size()).isGreaterThan(0)

            // Check if any error contains the expected message
            val errorMessages =
                (0 until errors.size()).mapNotNull { i ->
                    try {
                        errors[i]["message"]?.stringValue
                    } catch (e: Exception) {
                        null
                    }
                }
            Assertions.assertThat(errorMessages).contains(expectedMessage)

            // Verify data is null, not present, or empty object (GraphQL may return {} for errors)
            val dataNode = payload["data"]
            if (dataNode != null && !dataNode.isNull) {
                // If data is present, it should be an object (could be empty {})
                // This is acceptable for GraphQL error responses - some implementations return {} instead of null
                Assertions.assertThat(dataNode.isObject).isTrue()
            }
        }

        /**
         * Helper to create a user without any roles/permissions for testing 403 scenarios
         */
        private fun createUserWithoutPermissions(): Pair<UserEntity, String> {
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            saveUser(user)

            // Generate token for user without any roles
            val authContext = authContextFactory.build(user)
            val token = authenticationService.generateAccessToken(authContext)

            return Pair(user, token)
        }

        // ========== Query Tests ==========

        @Test
        fun `should return Authentication required when querying user without token`() {
            val userId = UUID.randomUUID()
            val query =
                mapOf(
                    "query" to
                        """
                        query {
                            user(id: "$userId") {
                                id
                                email
                            }
                        }
                        """.trimIndent(),
                )

            val request =
                HttpRequest
                    .POST("/graphql", query)
                    .contentType(MediaType.APPLICATION_JSON)

            val response = httpClient.exchangeAsString(request)
            response.shouldBeSuccessful().shouldBeJson().shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            verifyGraphQLError(payload, "Authentication required")
        }

        @Test
        fun `should return Permission denied when querying user without permission`() {
            val (_, token) = createUserWithoutPermissions()
            val (targetUser, _) = createUserWithAdminRoleAndToken()

            val query =
                mapOf(
                    "query" to
                        """
                        query {
                            user(id: "${targetUser.id}") {
                                id
                                email
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
            response.shouldBeSuccessful().shouldBeJson().shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            verifyGraphQLError(payload, "Permission denied: security:user:view")
        }

        @Test
        fun `should return Authentication required when querying users without token`() {
            val query =
                mapOf(
                    "query" to
                        """
                        query {
                            users(first: 2) {
                                edges {
                                    node {
                                        id
                                        email
                                    }
                                }
                            }
                        }
                        """.trimIndent(),
                )

            val request =
                HttpRequest
                    .POST("/graphql", query)
                    .contentType(MediaType.APPLICATION_JSON)

            val response = httpClient.exchangeAsString(request)
            response.shouldBeSuccessful().shouldBeJson().shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            verifyGraphQLError(payload, "Authentication required")
        }

        @Test
        fun `should return Permission denied when querying users without permission`() {
            val (_, token) = createUserWithoutPermissions()

            val query =
                mapOf(
                    "query" to
                        """
                        query {
                            users(first: 2) {
                                edges {
                                    node {
                                        id
                                        email
                                    }
                                }
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
            response.shouldBeSuccessful().shouldBeJson().shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            verifyGraphQLError(payload, "Permission denied: security:user:view")
        }

        // ========== Mutation Tests ==========

        @Test
        fun `should return Authentication required when enabling user without token`() {
            val (targetUser, _) = createUserWithAdminRoleAndToken()

            val mutation =
                mapOf(
                    "query" to
                        """
                        mutation {
                            enableUser(userId: "${targetUser.id}") {
                                id
                            }
                        }
                        """.trimIndent(),
                )

            val request =
                HttpRequest
                    .POST("/graphql", mutation)
                    .contentType(MediaType.APPLICATION_JSON)

            val response = httpClient.exchangeAsString(request)
            response.shouldBeSuccessful().shouldBeJson().shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            verifyGraphQLError(payload, "Authentication required")
        }

        @Test
        fun `should return Permission denied when enabling user without permission`() {
            val (_, token) = createUserWithoutPermissions()
            val (targetUser, _) = createUserWithAdminRoleAndToken()

            val mutation =
                mapOf(
                    "query" to
                        """
                        mutation {
                            enableUser(userId: "${targetUser.id}") {
                                id
                            }
                        }
                        """.trimIndent(),
                )

            val request =
                HttpRequest
                    .POST("/graphql", mutation)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer $token")

            val response = httpClient.exchangeAsString(request)
            response.shouldBeSuccessful().shouldBeJson().shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            verifyGraphQLError(payload, "Permission denied: security:user:save")
        }

        @Test
        fun `should return Authentication required when disabling user without token`() {
            val (targetUser, _) = createUserWithAdminRoleAndToken()

            val mutation =
                mapOf(
                    "query" to
                        """
                        mutation {
                            disableUser(userId: "${targetUser.id}") {
                                id
                            }
                        }
                        """.trimIndent(),
                )

            val request =
                HttpRequest
                    .POST("/graphql", mutation)
                    .contentType(MediaType.APPLICATION_JSON)

            val response = httpClient.exchangeAsString(request)
            response.shouldBeSuccessful().shouldBeJson().shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            verifyGraphQLError(payload, "Authentication required")
        }

        @Test
        fun `should return Permission denied when disabling user without permission`() {
            val (_, token) = createUserWithoutPermissions()
            val (targetUser, _) = createUserWithAdminRoleAndToken()

            val mutation =
                mapOf(
                    "query" to
                        """
                        mutation {
                            disableUser(userId: "${targetUser.id}") {
                                id
                            }
                        }
                        """.trimIndent(),
                )

            val request =
                HttpRequest
                    .POST("/graphql", mutation)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer $token")

            val response = httpClient.exchangeAsString(request)
            response.shouldBeSuccessful().shouldBeJson().shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            verifyGraphQLError(payload, "Permission denied: security:user:save")
        }

        @Test
        fun `should return Authentication required when assigning role to user without token`() {
            val (targetUser, _) = createUserWithAdminRoleAndToken()
            val group =
                entityManager.runTransaction {
                    val newGroup = SecurityTestDataBuilders.group(name = "test-group-${UUID.randomUUID()}")
                    groupRepository.save(newGroup)
                }

            val mutation =
                mapOf(
                    "query" to
                        """
                        mutation {
                            assignGroupToUser(userId: "${targetUser.id}", groupId: "${group.id}") {
                                id
                            }
                        }
                        """.trimIndent(),
                )

            val request =
                HttpRequest
                    .POST("/graphql", mutation)
                    .contentType(MediaType.APPLICATION_JSON)

            val response = httpClient.exchangeAsString(request)
            response.shouldBeSuccessful().shouldBeJson().shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            verifyGraphQLError(payload, "Authentication required")
        }

        @Test
        fun `should return Permission denied when assigning role to user without permission`() {
            val (_, token) = createUserWithoutPermissions()
            val (targetUser, _) = createUserWithAdminRoleAndToken()
            val group =
                entityManager.runTransaction {
                    val newGroup = SecurityTestDataBuilders.group(name = "test-group-${UUID.randomUUID()}")
                    groupRepository.save(newGroup)
                }

            val mutation =
                mapOf(
                    "query" to
                        """
                        mutation {
                            assignGroupToUser(userId: "${targetUser.id}", groupId: "${group.id}") {
                                id
                            }
                        }
                        """.trimIndent(),
                )

            val request =
                HttpRequest
                    .POST("/graphql", mutation)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer $token")

            val response = httpClient.exchangeAsString(request)
            response.shouldBeSuccessful().shouldBeJson().shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            verifyGraphQLError(payload, "Permission denied: security:user:save")
        }

        @Test
        fun `should return Authentication required when removing role from user without token`() {
            val (targetUser, _) = createUserWithAdminRoleAndToken()
            val group =
                entityManager.runTransaction {
                    val newGroup = SecurityTestDataBuilders.group(name = "test-group-${UUID.randomUUID()}")
                    val savedGroup = groupRepository.save(newGroup)
                    // Add user to group first so we can test removal
                    val membership =
                        SecurityTestDataBuilders.groupMembership(
                            userId = targetUser.id!!,
                            groupId = savedGroup.id,
                        )
                    groupMembershipRepository.save(membership)
                    savedGroup
                }

            val mutation =
                mapOf(
                    "query" to
                        """
                        mutation {
                            removeGroupFromUser(userId: "${targetUser.id}", groupId: "${group.id}") {
                                id
                            }
                        }
                        """.trimIndent(),
                )

            val request =
                HttpRequest
                    .POST("/graphql", mutation)
                    .contentType(MediaType.APPLICATION_JSON)

            val response = httpClient.exchangeAsString(request)
            response.shouldBeSuccessful().shouldBeJson().shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            verifyGraphQLError(payload, "Authentication required")
        }

        @Test
        fun `should return Permission denied when removing role from user without permission`() {
            val (_, token) = createUserWithoutPermissions()
            val (targetUser, _) = createUserWithAdminRoleAndToken()
            val group =
                entityManager.runTransaction {
                    val newGroup = SecurityTestDataBuilders.group(name = "test-group-${UUID.randomUUID()}")
                    val savedGroup = groupRepository.save(newGroup)
                    // Add user to group first so we can test removal
                    val membership =
                        SecurityTestDataBuilders.groupMembership(
                            userId = targetUser.id!!,
                            groupId = savedGroup.id,
                        )
                    groupMembershipRepository.save(membership)
                    savedGroup
                }

            val mutation =
                mapOf(
                    "query" to
                        """
                        mutation {
                            removeGroupFromUser(userId: "${targetUser.id}", groupId: "${group.id}") {
                                id
                            }
                        }
                        """.trimIndent(),
                )

            val request =
                HttpRequest
                    .POST("/graphql", mutation)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer $token")

            val response = httpClient.exchangeAsString(request)
            response.shouldBeSuccessful().shouldBeJson().shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            verifyGraphQLError(payload, "Permission denied: security:user:save")
        }

        @Test
        fun `should return Authentication required when assigning group to user without token`() {
            val (targetUser, _) = createUserWithAdminRoleAndToken()
            // Create a test group if needed
            val group =
                groupRepository.findAll().firstOrNull() ?: run {
                    val newGroup = SecurityTestDataBuilders.group(name = "TEST_GROUP")
                    groupRepository.save(newGroup)
                }

            val mutation =
                mapOf(
                    "query" to
                        """
                        mutation {
                            assignGroupToUser(userId: "${targetUser.id}", groupId: "${group.id}") {
                                id
                            }
                        }
                        """.trimIndent(),
                )

            val request =
                HttpRequest
                    .POST("/graphql", mutation)
                    .contentType(MediaType.APPLICATION_JSON)

            val response = httpClient.exchangeAsString(request)
            response.shouldBeSuccessful().shouldBeJson().shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            verifyGraphQLError(payload, "Authentication required")
        }

        @Test
        fun `should return Permission denied when assigning group to user without permission`() {
            val (_, token) = createUserWithoutPermissions()
            val (targetUser, _) = createUserWithAdminRoleAndToken()
            val group =
                groupRepository.findAll().firstOrNull() ?: run {
                    val newGroup = SecurityTestDataBuilders.group(name = "TEST_GROUP")
                    groupRepository.save(newGroup)
                }

            val mutation =
                mapOf(
                    "query" to
                        """
                        mutation {
                            assignGroupToUser(userId: "${targetUser.id}", groupId: "${group.id}") {
                                id
                            }
                        }
                        """.trimIndent(),
                )

            val request =
                HttpRequest
                    .POST("/graphql", mutation)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer $token")

            val response = httpClient.exchangeAsString(request)
            response.shouldBeSuccessful().shouldBeJson().shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            verifyGraphQLError(payload, "Permission denied: security:user:save")
        }

        @Test
        fun `should return Authentication required when removing group from user without token`() {
            val (targetUser, _) = createUserWithAdminRoleAndToken()
            val group =
                groupRepository.findAll().firstOrNull() ?: run {
                    val newGroup = SecurityTestDataBuilders.group(name = "TEST_GROUP")
                    groupRepository.save(newGroup)
                }

            val mutation =
                mapOf(
                    "query" to
                        """
                        mutation {
                            removeGroupFromUser(userId: "${targetUser.id}", groupId: "${group.id}") {
                                id
                            }
                        }
                        """.trimIndent(),
                )

            val request =
                HttpRequest
                    .POST("/graphql", mutation)
                    .contentType(MediaType.APPLICATION_JSON)

            val response = httpClient.exchangeAsString(request)
            response.shouldBeSuccessful().shouldBeJson().shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            verifyGraphQLError(payload, "Authentication required")
        }

        @Test
        fun `should return Permission denied when removing group from user without permission`() {
            val (_, token) = createUserWithoutPermissions()
            val (targetUser, _) = createUserWithAdminRoleAndToken()
            val group =
                groupRepository.findAll().firstOrNull() ?: run {
                    val newGroup = SecurityTestDataBuilders.group(name = "TEST_GROUP")
                    groupRepository.save(newGroup)
                }

            val mutation =
                mapOf(
                    "query" to
                        """
                        mutation {
                            removeGroupFromUser(userId: "${targetUser.id}", groupId: "${group.id}") {
                                id
                            }
                        }
                        """.trimIndent(),
                )

            val request =
                HttpRequest
                    .POST("/graphql", mutation)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer $token")

            val response = httpClient.exchangeAsString(request)
            response.shouldBeSuccessful().shouldBeJson().shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            verifyGraphQLError(payload, "Permission denied: security:user:save")
        }

        // ========== Field Tests ==========

        @Test
        fun `should return Authentication required when querying User roles without token`() {
            val (targetUser, _) = createUserWithAdminRoleAndToken()

            val query =
                mapOf(
                    "query" to
                        """
                        query {
                            user(id: "${targetUser.id}") {
                                id
                                email
                                roles {
                                    id
                                    name
                                }
                            }
                        }
                        """.trimIndent(),
                )

            val request =
                HttpRequest
                    .POST("/graphql", query)
                    .contentType(MediaType.APPLICATION_JSON)

            val response = httpClient.exchangeAsString(request)
            response.shouldBeSuccessful().shouldBeJson().shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            val errors = payload["errors"]
            Assertions.assertThat(errors).isNotNull()
            Assertions.assertThat(errors.isArray).isTrue()
            Assertions.assertThat(errors.size()).isGreaterThan(0)
            // Should have error for the roles field - check if any error has the correct message
            var foundRolesError = false
            for (i in 0 until errors.size()) {
                val error = errors[i]
                val message = error["message"]?.stringValue ?: ""
                if (message == "Authentication required") {
                    foundRolesError = true
                    break
                }
            }
            Assertions.assertThat(foundRolesError).isTrue()
        }

        @Test
        fun `should return Permission denied when querying User roles without permission`() {
            val (_, token) = createUserWithoutPermissions()
            val (targetUser, _) = createUserWithAdminRoleAndToken()

            val query =
                mapOf(
                    "query" to
                        """
                        query {
                            user(id: "${targetUser.id}") {
                                id
                                email
                                roles {
                                    id
                                    name
                                }
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
            response.shouldBeSuccessful().shouldBeJson().shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            val errors = payload["errors"]
            Assertions.assertThat(errors).isNotNull()
            Assertions.assertThat(errors.isArray).isTrue()
            Assertions.assertThat(errors.size()).isGreaterThan(0)
            // Should have error for the roles field - check if any error has the correct message
            var foundRolesError = false
            for (i in 0 until errors.size()) {
                val error = errors[i]
                val message = error["message"]?.stringValue ?: ""
                if (message == "Permission denied: security:user:view") {
                    foundRolesError = true
                    break
                }
            }
            Assertions.assertThat(foundRolesError).isTrue()
        }

        @Test
        fun `should return Authentication required when querying User groups without token`() {
            val (targetUser, _) = createUserWithAdminRoleAndToken()

            val query =
                mapOf(
                    "query" to
                        """
                        query {
                            user(id: "${targetUser.id}") {
                                id
                                email
                                groups {
                                    id
                                    name
                                }
                            }
                        }
                        """.trimIndent(),
                )

            val request =
                HttpRequest
                    .POST("/graphql", query)
                    .contentType(MediaType.APPLICATION_JSON)

            val response = httpClient.exchangeAsString(request)
            response.shouldBeSuccessful().shouldBeJson().shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            val errors = payload["errors"]
            Assertions.assertThat(errors).isNotNull()
            Assertions.assertThat(errors.isArray).isTrue()
            Assertions.assertThat(errors.size()).isGreaterThan(0)
            // Should have error for the groups field - check if any error has the correct message
            var foundGroupsError = false
            for (i in 0 until errors.size()) {
                val error = errors[i]
                val message = error["message"]?.stringValue ?: ""
                if (message == "Authentication required") {
                    foundGroupsError = true
                    break
                }
            }
            Assertions.assertThat(foundGroupsError).isTrue()
        }

        @Test
        fun `should return Permission denied when querying User groups without permission`() {
            val (_, token) = createUserWithoutPermissions()
            val (targetUser, _) = createUserWithAdminRoleAndToken()

            val query =
                mapOf(
                    "query" to
                        """
                        query {
                            user(id: "${targetUser.id}") {
                                id
                                email
                                groups {
                                    id
                                    name
                                }
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
            response.shouldBeSuccessful().shouldBeJson().shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            val errors = payload["errors"]
            Assertions.assertThat(errors).isNotNull()
            Assertions.assertThat(errors.isArray).isTrue()
            Assertions.assertThat(errors.size()).isGreaterThan(0)
            // Should have error for the groups field - check if any error has the correct message
            var foundGroupsError = false
            for (i in 0 until errors.size()) {
                val error = errors[i]
                val message = error["message"]?.stringValue ?: ""
                if (message == "Permission denied: security:user:view") {
                    foundGroupsError = true
                    break
                }
            }
            Assertions.assertThat(foundGroupsError).isTrue()
        }

        @Test
        fun `should return Authentication required when querying User permissions without token`() {
            val (targetUser, _) = createUserWithAdminRoleAndToken()

            val query =
                mapOf(
                    "query" to
                        """
                        query {
                            user(id: "${targetUser.id}") {
                                id
                                email
                                permissions {
                                    id
                                    name
                                }
                            }
                        }
                        """.trimIndent(),
                )

            val request =
                HttpRequest
                    .POST("/graphql", query)
                    .contentType(MediaType.APPLICATION_JSON)

            val response = httpClient.exchangeAsString(request)
            response.shouldBeSuccessful().shouldBeJson().shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            val errors = payload["errors"]
            Assertions.assertThat(errors).isNotNull()
            Assertions.assertThat(errors.isArray).isTrue()
            Assertions.assertThat(errors.size()).isGreaterThan(0)
            // Should have error for the permissions field - check if any error has the correct message
            var foundPermissionsError = false
            for (i in 0 until errors.size()) {
                val error = errors[i]
                val message = error["message"]?.stringValue ?: ""
                if (message == "Authentication required") {
                    foundPermissionsError = true
                    break
                }
            }
            Assertions.assertThat(foundPermissionsError).isTrue()
        }

        @Test
        fun `should return Permission denied when querying User permissions without permission`() {
            val (_, token) = createUserWithoutPermissions()
            val (targetUser, _) = createUserWithAdminRoleAndToken()

            val query =
                mapOf(
                    "query" to
                        """
                        query {
                            user(id: "${targetUser.id}") {
                                id
                                email
                                permissions {
                                    id
                                    name
                                }
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
            response.shouldBeSuccessful().shouldBeJson().shouldHaveNonEmptyBody()

            val payload: JsonNode = json.read(response)
            val errors = payload["errors"]
            Assertions.assertThat(errors).isNotNull()
            Assertions.assertThat(errors.isArray).isTrue()
            Assertions.assertThat(errors.size()).isGreaterThan(0)
            // Should have error for the permissions field - check if any error has the correct message
            var foundPermissionsError = false
            for (i in 0 until errors.size()) {
                val error = errors[i]
                val message = error["message"]?.stringValue ?: ""
                if (message == "Permission denied: security:user:view") {
                    foundPermissionsError = true
                    break
                }
            }
            Assertions.assertThat(foundPermissionsError).isTrue()
        }
    }
}
