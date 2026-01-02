package io.github.salomax.neotool.security.test.service.integration

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
import io.github.salomax.neotool.security.repo.PasswordResetAttemptRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.email.EmailService
import io.github.salomax.neotool.security.service.email.MockEmailService
import io.github.salomax.neotool.security.service.authentication.AuthenticationService
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
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("Password Reset GraphQL Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("graphql")
@Tag("password-reset")
@Tag("security")
@TestMethodOrder(MethodOrderer.Random::class)
open class PasswordResetGraphQLIntegrationTest :
    BaseIntegrationTest(),
    PostgresIntegrationTest {
    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var passwordResetAttemptRepository: PasswordResetAttemptRepository

    @Inject
    lateinit var authenticationService: AuthenticationService

    @Inject
    lateinit var entityManager: EntityManager

    @Inject
    lateinit var emailService: EmailService

    /**
     * Get the MockEmailService instance for test assertions.
     * The MockEmailServiceFactory automatically provides MockEmailService in tests.
     */
    private val mockEmailService: MockEmailService
        get() = emailService as MockEmailService

    private fun uniqueEmail() = SecurityTestDataBuilders.uniqueEmail("graphql-password-reset")

    fun saveUser(user: UserEntity) {
        entityManager.runTransaction {
            userRepository.save(user)
            entityManager.flush()
        }
    }

    @AfterEach
    fun cleanupTestData() {
        try {
            passwordResetAttemptRepository.deleteAll()
            userRepository.deleteAll()
            // Clear sent emails for clean test state
            mockEmailService.clearSentEmails()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    @Nested
    @DisplayName("Request Password Reset Mutation")
    inner class RequestPasswordResetMutationTests {
        @Test
        fun `should request password reset successfully via GraphQL mutation`() {
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

            val data = payload["data"]
            Assertions
                .assertThat(data)
                .describedAs("GraphQL response must contain 'data'")
                .isNotNull()
            val requestPasswordResetNode = data["requestPasswordReset"]
            Assertions
                .assertThat(requestPasswordResetNode)
                .describedAs("requestPasswordReset payload must be present")
                .isNotNull()

            val requestPasswordResetPayload: JsonNode = requestPasswordResetNode
            Assertions
                .assertThat(requestPasswordResetPayload["success"].booleanValue)
                .describedAs("success should be true")
                .isTrue()
            Assertions
                .assertThat(requestPasswordResetPayload["message"].stringValue)
                .describedAs("message should be present")
                .isNotBlank()

            // Verify token was saved to database
            entityManager.flush()
            entityManager.clear()
            val savedUser = userRepository.findByEmail(email)
            Assertions
                .assertThat(savedUser?.passwordResetToken)
                .describedAs("Password reset token should be saved")
                .isNotNull()

            // Verify email was sent (using MockEmailService)
            val sentEmails = mockEmailService.getSentEmails(email)
            Assertions
                .assertThat(sentEmails)
                .describedAs("Email should be sent")
                .hasSize(1)
            val sentEmail = sentEmails[0]
            Assertions.assertThat(sentEmail.email).isEqualTo(email)
            Assertions.assertThat(sentEmail.token).isEqualTo(savedUser?.passwordResetToken)
            Assertions.assertThat(sentEmail.locale).isEqualTo("en")
        }

        @Test
        fun `should return success even for non-existent email`() {
            val email = uniqueEmail()

            val mutation = SecurityTestDataBuilders.requestPasswordResetMutation(email = email)

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

            val data = payload["data"]
            val requestPasswordResetNode = data["requestPasswordReset"]
            Assertions
                .assertThat(requestPasswordResetNode["success"].booleanValue)
                .describedAs("Should return success even for non-existent email (security)")
                .isTrue()
        }

        @Test
        fun `should return error for missing email`() {
            val mutation =
                mapOf(
                    "query" to
                        """
                        mutation RequestPasswordReset(${'$'}input: RequestPasswordResetInput!) {
                            requestPasswordReset(input: ${'$'}input) {
                                success
                                message
                            }
                        }
                        """.trimIndent(),
                    "variables" to
                        mapOf(
                            "input" to mapOf<String, Any>(),
                        ),
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
            val errors = payload["errors"]
            Assertions
                .assertThat(errors)
                .describedAs("GraphQL should return errors for missing required field")
                .isNotNull()
        }

        @Test
        fun `should handle locale parameter`() {
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
                    locale = "pt",
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

            val data = payload["data"]
            val requestPasswordResetNode = data["requestPasswordReset"]
            Assertions.assertThat(requestPasswordResetNode["success"].booleanValue).isTrue()
        }
    }

    @Nested
    @DisplayName("Reset Password Mutation")
    inner class ResetPasswordMutationTests {
        @Test
        fun `should reset password successfully via GraphQL mutation`() {
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

            // Request password reset first
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

            val data = payload["data"]
            Assertions
                .assertThat(data)
                .describedAs("GraphQL response must contain 'data'")
                .isNotNull()
            val resetPasswordNode = data["resetPassword"]
            Assertions
                .assertThat(resetPasswordNode)
                .describedAs("resetPassword payload must be present")
                .isNotNull()

            val resetPasswordPayload: JsonNode = resetPasswordNode
            Assertions
                .assertThat(resetPasswordPayload["success"].booleanValue)
                .describedAs("success should be true")
                .isTrue()
            Assertions
                .assertThat(resetPasswordPayload["message"].stringValue)
                .describedAs("message should be present")
                .isNotBlank()

            // Verify password was changed
            entityManager.flush()
            entityManager.clear()
            val authenticatedUser = authenticationService.authenticate(email, newPassword)
            Assertions
                .assertThat(authenticatedUser)
                .describedAs("Should be able to authenticate with new password")
                .isNotNull()

            val oldPasswordWorks = authenticationService.authenticate(email, oldPassword)
            Assertions
                .assertThat(oldPasswordWorks)
                .describedAs("Should not be able to authenticate with old password")
                .isNull()
        }

        @Test
        fun `should return error for invalid token`() {
            val invalidToken = UUID.randomUUID().toString()
            val newPassword = "NewPassword123!"

            val mutation =
                SecurityTestDataBuilders.resetPasswordMutation(
                    token = invalidToken,
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
            val errors = payload["errors"]
            Assertions
                .assertThat(errors)
                .describedAs("GraphQL should return errors for invalid token")
                .isNotNull()
            Assertions.assertThat(errors.isArray).isTrue
            Assertions.assertThat(errors.size()).isGreaterThan(0)

            val firstError = errors[0]
            val messageNode = firstError["message"]
            Assertions
                .assertThat(messageNode)
                .describedAs("Error message must be present")
                .isNotNull()
            val errorMessage = messageNode.stringValue
            Assertions.assertThat(errorMessage).containsIgnoringCase("invalid")
        }

        @Test
        fun `should return error for expired token`() {
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            val expiredToken = UUID.randomUUID().toString()
            user.passwordResetToken = expiredToken
            user.passwordResetExpiresAt = Instant.now().minus(1, ChronoUnit.HOURS)
            saveUser(user)

            val mutation =
                SecurityTestDataBuilders.resetPasswordMutation(
                    token = expiredToken,
                    newPassword = "NewPassword123!",
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
            val errors = payload["errors"]
            Assertions
                .assertThat(errors)
                .describedAs("GraphQL should return errors for expired token")
                .isNotNull()
        }

        @Test
        fun `should return error for weak password`() {
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            saveUser(user)

            entityManager.runTransaction {
                authenticationService.requestPasswordReset(email, "en")
                entityManager.flush()
            }
            val savedUser = userRepository.findByEmail(email)
            val token = savedUser!!.passwordResetToken!!

            val mutation =
                SecurityTestDataBuilders.resetPasswordMutation(
                    token = token,
                    newPassword = "weak",
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
            val errors = payload["errors"]
            Assertions
                .assertThat(errors)
                .describedAs("GraphQL should return errors for weak password")
                .isNotNull()
            Assertions.assertThat(errors.isArray).isTrue
            Assertions.assertThat(errors.size()).isGreaterThan(0)

            val firstError = errors[0]
            val messageNode = firstError["message"]
            val errorMessage = messageNode.stringValue
            Assertions.assertThat(errorMessage).containsIgnoringCase("password")
        }

        @Test
        fun `should return error for missing token`() {
            val mutation =
                mapOf(
                    "query" to
                        """
                        mutation ResetPassword(${'$'}input: ResetPasswordInput!) {
                            resetPassword(input: ${'$'}input) {
                                success
                                message
                            }
                        }
                        """.trimIndent(),
                    "variables" to
                        mapOf(
                            "input" to
                                mapOf(
                                    "newPassword" to "NewPassword123!",
                                ),
                        ),
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
            val errors = payload["errors"]
            Assertions
                .assertThat(errors)
                .describedAs("GraphQL should return errors for missing required field")
                .isNotNull()
        }

        @Test
        fun `should return error for missing newPassword`() {
            val mutation =
                mapOf(
                    "query" to
                        """
                        mutation ResetPassword(${'$'}input: ResetPasswordInput!) {
                            resetPassword(input: ${'$'}input) {
                                success
                                message
                            }
                        }
                        """.trimIndent(),
                    "variables" to
                        mapOf(
                            "input" to
                                mapOf(
                                    "token" to "test-token",
                                ),
                        ),
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
            val errors = payload["errors"]
            Assertions
                .assertThat(errors)
                .describedAs("GraphQL should return errors for missing required field")
                .isNotNull()
        }
    }

    @Nested
    @DisplayName("End-to-End Password Reset Flow")
    inner class EndToEndFlowTests {
        @Test
        fun `should complete full password reset flow via GraphQL`() {
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

            // Step 1: Request password reset
            val requestMutation = SecurityTestDataBuilders.requestPasswordResetMutation(email = email)
            val requestRequest =
                HttpRequest
                    .POST("/graphql", requestMutation)
                    .contentType(MediaType.APPLICATION_JSON)

            val requestResponse = httpClient.exchangeAsString(requestRequest)
            val requestPayload: JsonNode = json.read(requestResponse)
            requestPayload["errors"].assertNoErrors()
            val requestData = requestPayload["data"]["requestPasswordReset"]
            Assertions.assertThat(requestData["success"].booleanValue).isTrue()

            // Step 2: Get token from database
            entityManager.flush()
            entityManager.clear()
            val savedUser = userRepository.findByEmail(email)
            Assertions.assertThat(savedUser?.passwordResetToken).isNotNull()
            val token = savedUser!!.passwordResetToken!!

            // Step 3: Reset password
            val resetMutation =
                SecurityTestDataBuilders.resetPasswordMutation(
                    token = token,
                    newPassword = newPassword,
                )
            val resetRequest =
                HttpRequest
                    .POST("/graphql", resetMutation)
                    .contentType(MediaType.APPLICATION_JSON)

            val resetResponse = httpClient.exchangeAsString(resetRequest)
            val resetPayload: JsonNode = json.read(resetResponse)
            resetPayload["errors"].assertNoErrors()
            val resetData = resetPayload["data"]["resetPassword"]
            Assertions.assertThat(resetData["success"].booleanValue).isTrue()

            // Step 4: Verify new password works
            entityManager.flush()
            entityManager.clear()
            val authenticatedUser = authenticationService.authenticate(email, newPassword)
            Assertions.assertThat(authenticatedUser).isNotNull()
            Assertions.assertThat(authenticatedUser?.email).isEqualTo(email)

            // Step 5: Verify old password doesn't work
            val oldPasswordWorks = authenticationService.authenticate(email, oldPassword)
            Assertions.assertThat(oldPasswordWorks).isNull()
        }
    }
}
