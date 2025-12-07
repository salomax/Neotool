package io.github.salomax.neotool.security.test.http

import io.github.salomax.neotool.common.test.http.exchangeAsString
import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.common.test.transaction.runTransaction
import io.github.salomax.neotool.security.domain.rbac.SecurityPermissions
import io.github.salomax.neotool.security.model.UserEntity
import io.github.salomax.neotool.security.repo.PermissionRepository
import io.github.salomax.neotool.security.repo.RoleAssignmentRepository
import io.github.salomax.neotool.security.repo.RoleRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.AuthContextFactory
import io.github.salomax.neotool.security.service.AuthenticationService
import io.github.salomax.neotool.security.service.EmailService
import io.github.salomax.neotool.security.service.MockEmailService
import io.github.salomax.neotool.security.test.SecurityTestDataBuilders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.util.UUID

/**
 * Integration tests for REST authorization infrastructure.
 *
 * Tests the [RequiresAuthorization] annotation, [AuthorizationInterceptor],
 * and exception handlers using a fake [ProtectedController] that is only
 * available in test scope.
 */
@MicronautTest(startApplication = true)
@DisplayName("Authorization Interceptor Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("security")
@Tag("http")
open class AuthorizationInterceptorIntegrationTest : BaseIntegrationTest(), PostgresIntegrationTest {
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
    lateinit var roleAssignmentRepository: RoleAssignmentRepository

    @Inject
    lateinit var permissionRepository: PermissionRepository

    private val mockEmailService: MockEmailService
        get() = emailService as MockEmailService

    private fun uniqueEmail() = SecurityTestDataBuilders.uniqueEmail("rest-auth")

    /**
     * Helper to create a user with specific permissions and generate an access token.
     */
    private fun createUserWithPermissionsAndToken(permissions: List<String>): Pair<UserEntity, String> {
        val email = uniqueEmail()
        val password = "TestPassword123!"
        val user =
            SecurityTestDataBuilders.userWithPassword(
                authenticationService = authenticationService,
                email = email,
                password = password,
            )
        saveUser(user)

        // Create a role for this user
        val roleName = "TEST_ROLE_${UUID.randomUUID().toString().take(8)}"
        val role = roleRepository.save(SecurityTestDataBuilders.role(name = roleName))

        // Create and link permissions to role
        entityManager.runTransaction {
            permissions.forEach { permissionName ->
                val permission =
                    permissionRepository.findByName(permissionName).orElseGet {
                        permissionRepository.save(SecurityTestDataBuilders.permission(name = permissionName))
                    }
                roleRepository.assignPermissionToRole(role.id!!, permission.id!!)
            }
            entityManager.flush()
        }

        // Assign role to user
        entityManager.runTransaction {
            val roleAssignment =
                SecurityTestDataBuilders.roleAssignment(
                    userId = user.id!!,
                    roleId = role.id!!,
                )
            roleAssignmentRepository.save(roleAssignment)
            entityManager.flush()
        }

        // Generate token with permissions
        val authContext = authContextFactory.build(user)
        val token = authenticationService.generateAccessToken(authContext)

        return Pair(user, token)
    }

    /**
     * Helper to create a user without any permissions and generate an access token.
     */
    private fun createUserWithoutPermissionsAndToken(): Pair<UserEntity, String> {
        val email = uniqueEmail()
        val password = "TestPassword123!"
        val user =
            SecurityTestDataBuilders.userWithPassword(
                authenticationService = authenticationService,
                email = email,
                password = password,
            )
        saveUser(user)

        // Generate token (user has no permissions)
        val authContext = authContextFactory.build(user)
        val token = authenticationService.generateAccessToken(authContext)

        return Pair(user, token)
    }

    private fun saveUser(user: UserEntity) {
        entityManager.runTransaction {
            authenticationService.saveUser(user)
            entityManager.flush()
        }
    }

    @AfterEach
    fun cleanupTestData() {
        try {
            roleAssignmentRepository.deleteAll()
            userRepository.deleteAll()
            mockEmailService.clearSentEmails()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    @Nested
    @DisplayName("GET /protected/view endpoint")
    inner class ViewEndpointTests {
        @Test
        fun `should return 401 when Authorization header is missing`() {
            // Arrange
            val request =
                HttpRequest.GET<Any>("/protected/view")

            // Act & Assert
            val exception =
                assertThrows<HttpClientResponseException> {
                    httpClient.exchangeAsString(request)
                }

            assertThat(exception.status).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @Test
        fun `should return 401 when Authorization header has invalid format`() {
            // Arrange
            val request =
                HttpRequest.GET<Any>("/protected/view")
                    .header("Authorization", "InvalidFormat token123")

            // Act & Assert
            val exception =
                assertThrows<HttpClientResponseException> {
                    httpClient.exchangeAsString(request)
                }

            assertThat(exception.status).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @Test
        fun `should return 401 when token is invalid`() {
            // Arrange
            val request =
                HttpRequest.GET<Any>("/protected/view")
                    .header("Authorization", "Bearer invalid-token-12345")

            // Act & Assert
            val exception =
                assertThrows<HttpClientResponseException> {
                    httpClient.exchangeAsString(request)
                }

            assertThat(exception.status).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @Test
        fun `should return 403 when token is valid but user lacks permission`() {
            // Arrange
            val (_, token) = createUserWithoutPermissionsAndToken()
            val request =
                HttpRequest.GET<Any>("/protected/view")
                    .header("Authorization", "Bearer $token")

            // Act & Assert
            val exception =
                assertThrows<HttpClientResponseException> {
                    httpClient.exchangeAsString(request)
                }

            assertThat(exception.status).isEqualTo(HttpStatus.FORBIDDEN)
        }

        @Test
        fun `should return 200 when token is valid and user has permission`() {
            // Arrange
            val (_, token) =
                createUserWithPermissionsAndToken(listOf(SecurityPermissions.SECURITY_USER_VIEW))
            val request =
                HttpRequest.GET<Any>("/protected/view")
                    .header("Authorization", "Bearer $token")

            // Act
            val response = httpClient.exchangeAsString(request)

            // Assert
            assertThat(response.status()).isEqualTo(HttpStatus.OK)
            assertThat(response.body()).isEqualTo("ok")
        }
    }

    @Nested
    @DisplayName("POST /protected/save endpoint")
    inner class SaveEndpointTests {
        @Test
        fun `should return 401 when Authorization header is missing`() {
            // Arrange
            val request =
                HttpRequest.POST("/protected/save", "")

            // Act & Assert
            val exception =
                assertThrows<HttpClientResponseException> {
                    httpClient.exchangeAsString(request)
                }

            assertThat(exception.status).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @Test
        fun `should return 401 when token is invalid`() {
            // Arrange
            val request =
                HttpRequest.POST("/protected/save", "")
                    .header("Authorization", "Bearer invalid-token-12345")

            // Act & Assert
            val exception =
                assertThrows<HttpClientResponseException> {
                    httpClient.exchangeAsString(request)
                }

            assertThat(exception.status).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @Test
        fun `should return 403 when token is valid but user lacks permission`() {
            // Arrange
            val (_, token) =
                createUserWithPermissionsAndToken(listOf(SecurityPermissions.SECURITY_USER_VIEW))
            val request =
                HttpRequest.POST("/protected/save", "")
                    .header("Authorization", "Bearer $token")

            // Act & Assert
            val exception =
                assertThrows<HttpClientResponseException> {
                    httpClient.exchangeAsString(request)
                }

            assertThat(exception.status).isEqualTo(HttpStatus.FORBIDDEN)
        }

        @Test
        fun `should return 200 when token is valid and user has permission`() {
            // Arrange
            val (_, token) =
                createUserWithPermissionsAndToken(listOf(SecurityPermissions.SECURITY_USER_SAVE))
            val request =
                HttpRequest.POST("/protected/save", "")
                    .header("Authorization", "Bearer $token")

            // Act
            val response = httpClient.exchangeAsString(request)

            // Assert
            assertThat(response.status()).isEqualTo(HttpStatus.OK)
            assertThat(response.body()).isEqualTo("saved")
        }
    }
}
