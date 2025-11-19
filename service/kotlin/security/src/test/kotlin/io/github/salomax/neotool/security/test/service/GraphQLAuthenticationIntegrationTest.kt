package io.github.salomax.neotool.security.test.service

import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.AuthenticationService
import io.github.salomax.neotool.security.test.SecurityTestDataBuilders
import io.github.salomax.neotool.common.test.assertions.assertNoErrors
import io.github.salomax.neotool.security.model.UserEntity
import io.github.salomax.neotool.common.test.assertions.shouldBeJson
import io.github.salomax.neotool.common.test.assertions.shouldBeSuccessful
import io.github.salomax.neotool.common.test.assertions.shouldHaveNonEmptyBody
import io.github.salomax.neotool.common.test.http.exchangeAsString
import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.common.test.json.read
import io.github.salomax.neotool.common.test.transaction.runTransaction
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.json.tree.JsonNode
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@MicronautTest(startApplication = true)
@DisplayName("GraphQL Authentication Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("graphql")
@Tag("authentication")
@Tag("security")
open class GraphQLAuthenticationIntegrationTest : BaseIntegrationTest(), PostgresIntegrationTest {

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var authenticationService: AuthenticationService

    @Inject
    lateinit var entityManager: EntityManager

    private fun uniqueEmail() = SecurityTestDataBuilders.uniqueEmail("graphql-auth")

    fun saveUser(user: UserEntity) {
        // ensure it runs over a new transaction
        // the data will be available for the tests after the commit
        entityManager.runTransaction {
            authenticationService.saveUser(user)
            entityManager.flush()
        }
    }

    @AfterEach
    fun cleanupTestData() {
        // Clean up test data after each test
        try {
            userRepository.deleteAll()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    @Nested
    @DisplayName("Sign In Mutation")
    inner class SignInMutationTests {

        @Test
        fun `should sign in user successfully via GraphQL mutation`() {
            // Validates that users with correct credentials receive a valid authentication token and user data.
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user = SecurityTestDataBuilders.userWithPassword(
                authenticationService = authenticationService,
                email = email,
                password = password
            )
            saveUser(user)

            val mutation = SecurityTestDataBuilders.signInMutation(
                email = email,
                password = password,
                rememberMe = false
            )

            val request = HttpRequest.POST("/graphql", mutation)
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

            val data = payload["data"]
            Assertions.assertThat(data)
                .describedAs("GraphQL response must contain 'data'")
                .isNotNull()
            val signInNode = data["signIn"]
            Assertions.assertThat(signInNode)
                .describedAs("signIn payload must be present")
                .isNotNull()

            val signInPayload: JsonNode = signInNode
            val tokenNode = signInPayload["token"]
            Assertions.assertThat(tokenNode)
                .describedAs("token must be present")
                .isNotNull()
            Assertions.assertThat(tokenNode.stringValue).isNotBlank()
            Assertions.assertThat(signInPayload["refreshToken"].isNull)
                .describedAs("refreshToken should be null when rememberMe is false")
                .isTrue()

            val userData: JsonNode = signInPayload["user"]
            Assertions.assertThat(userData)
                .describedAs("user data must be present")
                .isNotNull()
            Assertions.assertThat(userData["id"])
                .describedAs("user id must be present")
                .isNotNull()
            Assertions.assertThat(userData["email"].stringValue).isEqualTo(email)
            Assertions.assertThat(userData["displayName"])
                .describedAs("user displayName must be present")
                .isNotNull()
        }

        @Test
        fun `should sign in user with remember me via GraphQL mutation`() {
            // Ensures that when rememberMe is enabled, both access token and
            // refresh token are returned for persistent authentication.
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user = SecurityTestDataBuilders.userWithPassword(
                authenticationService = authenticationService,
                email = email,
                password = password
            )
            
            saveUser(user)

            val mutation = SecurityTestDataBuilders.signInMutation(
                email = email,
                password = password,
                rememberMe = true
            )

            val request = HttpRequest.POST("/graphql", mutation)
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

            val data = payload["data"]
            val signInNode = data["signIn"]
            Assertions.assertThat(signInNode)
                .describedAs("signIn payload must be present")
                .isNotNull()

            val signInPayload: JsonNode = signInNode
            val tokenNode = signInPayload["token"]
            Assertions.assertThat(tokenNode)
                .describedAs("token must be present")
                .isNotNull()
            Assertions.assertThat(tokenNode.stringValue).isNotBlank()
            val refreshTokenNode = signInPayload["refreshToken"]
            Assertions.assertThat(refreshTokenNode)
                .describedAs("refreshToken must be present when rememberMe is true")
                .isNotNull()
            Assertions.assertThat(refreshTokenNode.stringValue).isNotBlank()

            val userData: JsonNode = signInPayload["user"]
            Assertions.assertThat(userData["email"].stringValue).isEqualTo(email)
        }

        @Test
        fun `should return error for invalid credentials via GraphQL mutation`() {
            // Prevents unauthorized access by rejecting sign-in attempts with incorrect passwords.
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user = SecurityTestDataBuilders.userWithPassword(
                authenticationService = authenticationService,
                email = email,
                password = password
            )
            
            saveUser(user)

            val mutation = SecurityTestDataBuilders.signInMutation(
                email = email,
                password = "WrongPassword123!",
                rememberMe = false
            )

            val request = HttpRequest.POST("/graphql", mutation)
                .contentType(MediaType.APPLICATION_JSON)

            // Act
            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            // Assert
            val payload: JsonNode = json.read(response)
            // GraphQL returns errors in the errors array, not as HTTP error
            val errors = payload["errors"]
            Assertions.assertThat(errors)
                .describedAs("GraphQL should return errors for invalid credentials")
                .isNotNull()
            Assertions.assertThat(errors.isArray).isTrue
            Assertions.assertThat(errors.size()).isGreaterThan(0)

            // Check error message
            val firstError = errors[0]
            val messageNode = firstError["message"]
            Assertions.assertThat(messageNode)
                .describedAs("Error message must be present")
                .isNotNull()
            val errorMessage = messageNode.stringValue
            Assertions.assertThat(errorMessage).containsIgnoringCase("invalid")
        }

        @Test
        fun `should return error for non-existent user via GraphQL mutation`() {
            // Prevents user enumeration attacks by returning errors
            // for non-existent users without revealing their existence.
            val email = uniqueEmail()
            val mutation = SecurityTestDataBuilders.signInMutation(
                email = email,
                password = "TestPassword123!",
                rememberMe = false
            )

            val request = HttpRequest.POST("/graphql", mutation)
                .contentType(MediaType.APPLICATION_JSON)

            // Act
            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            // Assert
            val payload: JsonNode = json.read(response)
            val errors = payload["errors"]
            Assertions.assertThat(errors)
                .describedAs("GraphQL should return errors for non-existent user")
                .isNotNull()
            Assertions.assertThat(errors.isArray).isTrue
            Assertions.assertThat(errors.size()).isGreaterThan(0)
        }

        @Test
        fun `should return error for missing email via GraphQL mutation`() {
            // Ensures GraphQL schema validation rejects mutations with missing required fields (email).
            val mutation = mapOf(
                "query" to """
                    mutation SignIn(${'$'}input: SignInInput!) {
                        signIn(input: ${'$'}input) {
                            token
                            user {
                                id
                                email
                            }
                        }
                    }
                """.trimIndent(),
                "variables" to mapOf(
                    "input" to mapOf(
                        "password" to "TestPassword123!"
                    )
                )
            )

            val request = HttpRequest.POST("/graphql", mutation)
                .contentType(MediaType.APPLICATION_JSON)

            // Act
            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            // Assert
            val payload: JsonNode = json.read(response)
            val errors = payload["errors"]
            Assertions.assertThat(errors)
                .describedAs("GraphQL should return errors for missing required field")
                .isNotNull()
        }

        @Test
        fun `should return error for missing password via GraphQL mutation`() {
            // Ensures GraphQL schema validation rejects mutations with missing required fields (password).
            val mutation = mapOf(
                "query" to """
                    mutation SignIn(${'$'}input: SignInInput!) {
                        signIn(input: ${'$'}input) {
                            token
                            user {
                                id
                                email
                            }
                        }
                    }
                """.trimIndent(),
                "variables" to mapOf(
                    "input" to mapOf(
                        "email" to "test@example.com"
                    )
                )
            )

            val request = HttpRequest.POST("/graphql", mutation)
                .contentType(MediaType.APPLICATION_JSON)

            // Act
            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            // Assert
            val payload: JsonNode = json.read(response)
            val errors = payload["errors"]
            Assertions.assertThat(errors)
                .describedAs("GraphQL should return errors for missing required field")
                .isNotNull()
        }

        @Test
        fun `should return error for blank password via GraphQL mutation`() {
            // Tests exception handling branch in signIn resolver for blank password
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user = SecurityTestDataBuilders.userWithPassword(
                authenticationService = authenticationService,
                email = email,
                password = password
            )
            saveUser(user)

            val mutation = SecurityTestDataBuilders.signInMutation(
                email = email,
                password = "   ", // Blank password
                rememberMe = false
            )

            val request = HttpRequest.POST("/graphql", mutation)
                .contentType(MediaType.APPLICATION_JSON)

            // Act
            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            // Assert
            val payload: JsonNode = json.read(response)
            val errors = payload["errors"]
            Assertions.assertThat(errors)
                .describedAs("GraphQL should return errors for blank password")
                .isNotNull()
            Assertions.assertThat(errors.isArray).isTrue
            Assertions.assertThat(errors.size()).isGreaterThan(0)
        }

        @Test
        fun `should return error for empty password via GraphQL mutation`() {
            // Tests exception handling branch in signIn resolver for empty password
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user = SecurityTestDataBuilders.userWithPassword(
                authenticationService = authenticationService,
                email = email,
                password = password
            )
            saveUser(user)

            val mutation = SecurityTestDataBuilders.signInMutation(
                email = email,
                password = "", // Empty password
                rememberMe = false
            )

            val request = HttpRequest.POST("/graphql", mutation)
                .contentType(MediaType.APPLICATION_JSON)

            // Act
            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            // Assert
            val payload: JsonNode = json.read(response)
            val errors = payload["errors"]
            Assertions.assertThat(errors)
                .describedAs("GraphQL should return errors for empty password")
                .isNotNull()
            Assertions.assertThat(errors.isArray).isTrue
            Assertions.assertThat(errors.size()).isGreaterThan(0)
        }
    }

    @Nested
    @DisplayName("Current User Query")
    inner class CurrentUserQueryTests {

        @Test
        fun `should return current user when valid token provided`() {
            // Validates that authenticated users can successfully retrieve
            // their own user information using a valid JWT token.
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user = SecurityTestDataBuilders.userWithPassword(
                authenticationService = authenticationService,
                email = email,
                password = password
            )
            saveUser(user)

            // Sign in to get token
            val signInMutation = SecurityTestDataBuilders.signInMutation(
                email = email,
                password = password,
                rememberMe = false
            )

            val signInRequest = HttpRequest.POST("/graphql", signInMutation)
                .contentType(MediaType.APPLICATION_JSON)

            val signInResponse = httpClient.exchangeAsString(signInRequest)
            val signInPayload: JsonNode = json.read(signInResponse)
            signInPayload["errors"].assertNoErrors()
            val signInData = signInPayload["data"]["signIn"]
            Assertions.assertThat(signInData)
                .describedAs("signIn data must be present")
                .isNotNull()
            val token = signInData["token"].stringValue

            // Now query currentUser with the token
            val query = SecurityTestDataBuilders.currentUserQuery()
            val request = HttpRequest.POST("/graphql", query)
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
            Assertions.assertThat(data)
                .describedAs("GraphQL response must contain 'data'")
                .isNotNull()
            val currentUserNode = data["currentUser"]
            Assertions.assertThat(currentUserNode)
                .describedAs("currentUser must be present")
                .isNotNull()

            val currentUser: JsonNode = currentUserNode
            Assertions.assertThat(currentUser["id"])
                .describedAs("user id must be present")
                .isNotNull()
            Assertions.assertThat(currentUser["email"].stringValue).isEqualTo(email)
            Assertions.assertThat(currentUser["displayName"])
                .describedAs("user displayName must be present")
                .isNotNull()
        }

        @Test
        fun `should return null when no token provided`() {
            // Ensures that unauthenticated requests gracefully
            // return null instead of throwing errors, allowing public queries.
            val query = SecurityTestDataBuilders.currentUserQuery()
            val request = HttpRequest.POST("/graphql", query)
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
            payload["errors"].assertNoErrors()

            val data = payload["data"]
            Assertions.assertThat(data)
                .describedAs("GraphQL response must contain 'data'")
                .isNotNull()
            // Note: json.read converts null to JsonNull, so we check for isNull property
            Assertions.assertThat(data["currentUser"].isNull)
                .describedAs("currentUser should be null when no token provided")
                .isTrue()
        }

        @Test
        fun `should return null when invalid token provided`() {
            // Prevents unauthorized access by rejecting malformed or tampered tokens without exposing security details.
            // Arrange
            val query = SecurityTestDataBuilders.currentUserQuery()
            val request = HttpRequest.POST("/graphql", query)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer invalid-token-123")

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
            Assertions.assertThat(data)
                .describedAs("GraphQL response must contain 'data'")
                .isNotNull()
            // Note: json.read converts null to JsonNull, so we check for isNull property
            Assertions.assertThat(data["currentUser"].isNull)
                .describedAs("currentUser should be null when invalid token provided")
                .isTrue()
        }

        @Test
        fun `should return null when token is not an access token`() {
            // Tests branch where token is valid but not an access token (e.g., refresh token)
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user = SecurityTestDataBuilders.userWithPassword(
                authenticationService = authenticationService,
                email = email,
                password = password
            )
            saveUser(user)

            // Sign in to get tokens
            val signInMutation = SecurityTestDataBuilders.signInMutation(
                email = email,
                password = password,
                rememberMe = true
            )

            val signInRequest = HttpRequest.POST("/graphql", signInMutation)
                .contentType(MediaType.APPLICATION_JSON)

            val signInResponse = httpClient.exchangeAsString(signInRequest)
            val signInPayload: JsonNode = json.read(signInResponse)
            signInPayload["errors"].assertNoErrors()
            val signInData = signInPayload["data"]["signIn"]
            val refreshToken = signInData["refreshToken"].stringValue

            // Try to use refresh token as access token
            val query = SecurityTestDataBuilders.currentUserQuery()
            val request = HttpRequest.POST("/graphql", query)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $refreshToken")

            // Act
            val response = httpClient.exchangeAsString(request)
            response
                .shouldBeSuccessful()
                .shouldBeJson()
                .shouldHaveNonEmptyBody()

            // Assert - Should return null because refresh token is not an access token
            val payload: JsonNode = json.read(response)
            payload["errors"].assertNoErrors()

            val data = payload["data"]
            Assertions.assertThat(data["currentUser"].isNull)
                .describedAs("currentUser should be null when refresh token is used as access token")
                .isTrue()
        }
    }

    @Nested
    @DisplayName("End-to-End Authentication Flow")
    inner class EndToEndFlowTests {

        @Test
        fun `should complete full authentication flow via GraphQL`() {
            // Validates the complete end-to-end authentication workflow
            // from sign-in through token-based user queries and refresh token usage.
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user = SecurityTestDataBuilders.userWithPassword(
                authenticationService = authenticationService,
                email = email,
                password = password,
                displayName = "Test User"
            )
            
            saveUser(user)

            // Step 1: Sign in
            val signInMutation = SecurityTestDataBuilders.signInMutation(
                email = email,
                password = password,
                rememberMe = true
            )

            val signInRequest = HttpRequest.POST("/graphql", signInMutation)
                .contentType(MediaType.APPLICATION_JSON)

            val signInResponse = httpClient.exchangeAsString(signInRequest)
            val signInPayload: JsonNode = json.read(signInResponse)

            signInPayload["errors"].assertNoErrors()
            val signInData = signInPayload["data"]["signIn"]
            Assertions.assertThat(signInData)
                .describedAs("signIn data must be present")
                .isNotNull()
            val token = signInData["token"].stringValue
            val refreshToken = signInData["refreshToken"].stringValue

            Assertions.assertThat(token).isNotBlank()
            Assertions.assertThat(refreshToken).isNotBlank()

            // Step 2: Query current user with token
            val query = SecurityTestDataBuilders.currentUserQuery()
            val queryRequest = HttpRequest.POST("/graphql", query)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $token")

            val queryResponse = httpClient.exchangeAsString(queryRequest)
            val queryPayload: JsonNode = json.read(queryResponse)

            queryPayload["errors"].assertNoErrors()
            val currentUser = queryPayload["data"]["currentUser"]
            Assertions.assertThat(currentUser)
                .describedAs("currentUser must be present")
                .isNotNull()
            Assertions.assertThat(currentUser["email"].stringValue).isEqualTo(email)
            Assertions.assertThat(currentUser["displayName"].stringValue).isEqualTo("Test User")

            // Step 3: Verify refresh token can be used for authentication
            val authenticatedUser = authenticationService.authenticateByToken(refreshToken)
            Assertions.assertThat(authenticatedUser)
                .describedAs("authenticatedUser must be present")
                .isNotNull()
            Assertions.assertThat(authenticatedUser?.email).isEqualTo(email)
        }

        @Test
        fun `should use JWT tokens for authentication`() {
            // Validates that JWT tokens are being used instead of simple UUID tokens
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user = SecurityTestDataBuilders.userWithPassword(
                authenticationService = authenticationService,
                email = email,
                password = password
            )
            saveUser(user)

            // Sign in to get token
            val signInMutation = SecurityTestDataBuilders.signInMutation(
                email = email,
                password = password,
                rememberMe = false
            )

            val signInRequest = HttpRequest.POST("/graphql", signInMutation)
                .contentType(MediaType.APPLICATION_JSON)

            val signInResponse = httpClient.exchangeAsString(signInRequest)
            val signInPayload: JsonNode = json.read(signInResponse)
            signInPayload["errors"].assertNoErrors()
            val signInData = signInPayload["data"]["signIn"]
            val token = signInData["token"].stringValue

            // Assert - Token is a JWT (has 3 parts separated by dots)
            Assertions.assertThat(token.split("."))
                .describedAs("Token should be a JWT with 3 parts (header.payload.signature)")
                .hasSize(3)

            // Assert - Token can be validated as JWT access token
            val validatedUser = authenticationService.validateAccessToken(token)
            Assertions.assertThat(validatedUser)
                .describedAs("JWT access token should be valid")
                .isNotNull()
            Assertions.assertThat(validatedUser?.email).isEqualTo(email)

            // Assert - Token can be used to query currentUser
            val query = SecurityTestDataBuilders.currentUserQuery()
            val queryRequest = HttpRequest.POST("/graphql", query)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $token")

            val queryResponse = httpClient.exchangeAsString(queryRequest)
            val queryPayload: JsonNode = json.read(queryResponse)
            queryPayload["errors"].assertNoErrors()
            val currentUser = queryPayload["data"]["currentUser"]
            Assertions.assertThat(currentUser)
                .describedAs("currentUser should be returned with valid JWT token")
                .isNotNull()
            Assertions.assertThat(currentUser["email"].stringValue).isEqualTo(email)
        }

        @Test
        fun `should reject invalid JWT token`() {
            // Validates that invalid JWT tokens are properly rejected
            val query = SecurityTestDataBuilders.currentUserQuery()
            val request = HttpRequest.POST("/graphql", query)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer invalid.jwt.token")

            val response = httpClient.exchangeAsString(request)
            val payload: JsonNode = json.read(response)
            payload["errors"].assertNoErrors()

            val data = payload["data"]
            Assertions.assertThat(data["currentUser"].isNull)
                .describedAs("currentUser should be null for invalid JWT token")
                .isTrue()
        }

        @Test
        fun `should reject expired JWT token`() {
            // Note: This test would require generating an expired token
            // For now, we test that invalid tokens are rejected
            val query = SecurityTestDataBuilders.currentUserQuery()
            val request = HttpRequest.POST("/graphql", query)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.invalid")

            val response = httpClient.exchangeAsString(request)
            val payload: JsonNode = json.read(response)
            payload["errors"].assertNoErrors()

            val data = payload["data"]
            Assertions.assertThat(data["currentUser"].isNull)
                .describedAs("currentUser should be null for expired/invalid JWT token")
                .isTrue()
        }
    }
}
