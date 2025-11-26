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
import io.github.salomax.neotool.security.model.UserEntity
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.AuthenticationService
import io.github.salomax.neotool.security.service.EmailService
import io.github.salomax.neotool.security.service.MockEmailService
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
open class SecurityGraphQLIntegrationTest : BaseIntegrationTest(), PostgresIntegrationTest {
    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var authenticationService: AuthenticationService

    @Inject
    lateinit var entityManager: EntityManager

    @Inject
    lateinit var emailService: EmailService

    private val mockEmailService: MockEmailService
        get() = emailService as MockEmailService

    private fun uniqueEmail() = SecurityTestDataBuilders.uniqueEmail("graphql-security")

    fun saveUser(user: UserEntity) {
        entityManager.runTransaction {
            authenticationService.saveUser(user)
            entityManager.flush()
        }
    }

    @AfterEach
    fun cleanupTestData() {
        try {
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
                HttpRequest.POST("/graphql", query)
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
                HttpRequest.POST("/graphql", query)
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
                HttpRequest.POST("/graphql", query)
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
                HttpRequest.POST("/graphql", query)
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
                HttpRequest.POST("/graphql", query)
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
                HttpRequest.POST("/graphql", query)
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
                HttpRequest.POST("/graphql", signInMutation)
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
                HttpRequest.POST("/graphql", query)
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
                HttpRequest.POST("/graphql", mutation)
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
                HttpRequest.POST("/graphql", mutation)
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
                HttpRequest.POST("/graphql", mutation)
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
                HttpRequest.POST("/graphql", mutation)
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
}
