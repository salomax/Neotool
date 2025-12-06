package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.security.domain.rbac.Permission
import io.github.salomax.neotool.security.domain.rbac.Role
import io.github.salomax.neotool.security.model.UserEntity
import io.github.salomax.neotool.security.service.AuthContextFactory
import io.github.salomax.neotool.security.service.AuthorizationService
import io.github.salomax.neotool.security.test.SecurityTestDataBuilders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

@DisplayName("AuthContextFactory Unit Tests")
class AuthContextFactoryTest {
    private lateinit var authorizationService: AuthorizationService
    private lateinit var authContextFactory: AuthContextFactory

    @BeforeEach
    fun setUp() {
        authorizationService = mock()
        authContextFactory = AuthContextFactory(authorizationService)
    }

    @Nested
    @DisplayName("Build AuthContext")
    inner class BuildAuthContextTests {
        @Test
        fun `should build AuthContext with user with no roles or permissions`() {
            // Arrange
            val userId = UUID.randomUUID()
            val user = SecurityTestDataBuilders.user(id = userId, email = "test@example.com", displayName = "Test User")

            whenever(authorizationService.getUserRoles(any(), any())).thenReturn(emptyList())
            whenever(authorizationService.getUserPermissions(any(), any())).thenReturn(emptyList())

            // Act
            val authContext = authContextFactory.build(user)

            // Assert
            assertThat(authContext.userId).isEqualTo(userId)
            assertThat(authContext.email).isEqualTo("test@example.com")
            assertThat(authContext.displayName).isEqualTo("Test User")
            assertThat(authContext.roles).isEmpty()
            assertThat(authContext.permissions).isEmpty()
            assertThat(authContext.roles).isNotNull // Always non-null
            assertThat(authContext.permissions).isNotNull // Always non-null

            verify(authorizationService).getUserRoles(any(), any())
            verify(authorizationService).getUserPermissions(any(), any())
        }

        @Test
        fun `should build AuthContext with user with direct roles only`() {
            // Arrange
            val userId = UUID.randomUUID()
            val user = SecurityTestDataBuilders.user(id = userId, email = "test@example.com", displayName = "Test User")

            val roles =
                listOf(
                    Role(id = 1, name = "admin"),
                    Role(id = 2, name = "editor"),
                )
            val permissions =
                listOf(
                    Permission(id = 1, name = "transaction:read"),
                    Permission(id = 2, name = "transaction:write"),
                )

            whenever(authorizationService.getUserRoles(any(), any())).thenReturn(roles)
            whenever(authorizationService.getUserPermissions(any(), any())).thenReturn(permissions)

            // Act
            val authContext = authContextFactory.build(user)

            // Assert
            assertThat(authContext.userId).isEqualTo(userId)
            assertThat(authContext.email).isEqualTo("test@example.com")
            assertThat(authContext.displayName).isEqualTo("Test User")
            assertThat(authContext.roles).containsExactly("admin", "editor")
            assertThat(authContext.permissions).containsExactly("transaction:read", "transaction:write")
        }

        @Test
        fun `should build AuthContext with user with group-inherited roles only`() {
            // Arrange
            val userId = UUID.randomUUID()
            val user = SecurityTestDataBuilders.user(id = userId, email = "test@example.com", displayName = "Test User")

            val roles =
                listOf(
                    Role(id = 3, name = "viewer"),
                )
            val permissions =
                listOf(
                    Permission(id = 3, name = "transaction:read"),
                )

            whenever(authorizationService.getUserRoles(any(), any())).thenReturn(roles)
            whenever(authorizationService.getUserPermissions(any(), any())).thenReturn(permissions)

            // Act
            val authContext = authContextFactory.build(user)

            // Assert
            assertThat(authContext.userId).isEqualTo(userId)
            assertThat(authContext.roles).containsExactly("viewer")
            assertThat(authContext.permissions).containsExactly("transaction:read")
        }

        @Test
        fun `should build AuthContext with user with both direct and group-inherited roles`() {
            // Arrange
            val userId = UUID.randomUUID()
            val user = SecurityTestDataBuilders.user(id = userId, email = "test@example.com", displayName = "Test User")

            val roles =
                listOf(
                    Role(id = 1, name = "admin"),
                    Role(id = 2, name = "editor"),
                    // Group-inherited
                    Role(id = 3, name = "viewer"),
                )
            val permissions =
                listOf(
                    Permission(id = 1, name = "transaction:read"),
                    Permission(id = 2, name = "transaction:write"),
                    Permission(id = 3, name = "transaction:delete"),
                )

            whenever(authorizationService.getUserRoles(any(), any())).thenReturn(roles)
            whenever(authorizationService.getUserPermissions(any(), any())).thenReturn(permissions)

            // Act
            val authContext = authContextFactory.build(user)

            // Assert
            assertThat(authContext.userId).isEqualTo(userId)
            assertThat(authContext.roles).containsExactlyInAnyOrder("admin", "editor", "viewer")
            assertThat(authContext.permissions).containsExactlyInAnyOrder(
                "transaction:read",
                "transaction:write",
                "transaction:delete",
            )
        }

        @Test
        fun `should build AuthContext with user with null displayName`() {
            // Arrange
            val userId = UUID.randomUUID()
            val user = SecurityTestDataBuilders.user(id = userId, email = "test@example.com", displayName = null)

            whenever(authorizationService.getUserRoles(any(), any())).thenReturn(emptyList())
            whenever(authorizationService.getUserPermissions(any(), any())).thenReturn(emptyList())

            // Act
            val authContext = authContextFactory.build(user)

            // Assert
            assertThat(authContext.userId).isEqualTo(userId)
            assertThat(authContext.email).isEqualTo("test@example.com")
            assertThat(authContext.displayName).isNull()
            assertThat(authContext.roles).isEmpty()
            assertThat(authContext.permissions).isEmpty()
        }

        @Test
        fun `should handle exception when getUserRoles throws and return empty roles list`() {
            // Arrange
            val userId = UUID.randomUUID()
            val user = SecurityTestDataBuilders.user(id = userId, email = "test@example.com", displayName = "Test User")

            whenever(authorizationService.getUserRoles(any(), any())).thenThrow(RuntimeException("Database error"))
            whenever(authorizationService.getUserPermissions(any(), any())).thenReturn(emptyList())

            // Act
            val authContext = authContextFactory.build(user)

            // Assert
            assertThat(authContext.userId).isEqualTo(userId)
            assertThat(authContext.email).isEqualTo("test@example.com")
            assertThat(authContext.roles).isEmpty() // Should return empty list on error
            assertThat(authContext.permissions).isEmpty()
            assertThat(authContext.roles).isNotNull // Always non-null
        }

        @Test
        fun `should handle exception when getUserPermissions throws and return empty permissions list`() {
            // Arrange
            val userId = UUID.randomUUID()
            val user = SecurityTestDataBuilders.user(id = userId, email = "test@example.com", displayName = "Test User")

            val roles = listOf(Role(id = 1, name = "admin"))

            whenever(authorizationService.getUserRoles(any(), any())).thenReturn(roles)
            whenever(authorizationService.getUserPermissions(any(), any()))
                .thenThrow(RuntimeException("Database error"))

            // Act
            val authContext = authContextFactory.build(user)

            // Assert
            assertThat(authContext.userId).isEqualTo(userId)
            assertThat(authContext.email).isEqualTo("test@example.com")
            assertThat(authContext.roles).containsExactly("admin")
            assertThat(authContext.permissions).isEmpty() // Should return empty list on error
            assertThat(authContext.permissions).isNotNull // Always non-null
        }

        @Test
        fun `should handle exceptions from both getUserRoles and getUserPermissions`() {
            // Arrange
            val userId = UUID.randomUUID()
            val user = SecurityTestDataBuilders.user(id = userId, email = "test@example.com", displayName = "Test User")

            whenever(authorizationService.getUserRoles(any(), any()))
                .thenThrow(RuntimeException("Roles error"))
            whenever(authorizationService.getUserPermissions(any(), any()))
                .thenThrow(RuntimeException("Permissions error"))

            // Act
            val authContext = authContextFactory.build(user)

            // Assert
            assertThat(authContext.userId).isEqualTo(userId)
            assertThat(authContext.email).isEqualTo("test@example.com")
            assertThat(authContext.roles).isEmpty()
            assertThat(authContext.permissions).isEmpty()
            assertThat(authContext.roles).isNotNull
            assertThat(authContext.permissions).isNotNull
        }

        @Test
        fun `should throw IllegalStateException when user id is null`() {
            // Arrange
            val user =
                UserEntity(
                    id = null,
                    email = "test@example.com",
                    displayName = "Test User",
                )

            // Act & Assert
            assertThrows<IllegalStateException> {
                authContextFactory.build(user)
            }
        }

        @Test
        fun `should extract role names correctly from roles`() {
            // Arrange
            val userId = UUID.randomUUID()
            val user = SecurityTestDataBuilders.user(id = userId, email = "test@example.com", displayName = "Test User")

            val roles =
                listOf(
                    Role(id = 1, name = "role1"),
                    Role(id = 2, name = "role2"),
                    Role(id = 3, name = "role3"),
                )

            whenever(authorizationService.getUserRoles(any(), any())).thenReturn(roles)
            whenever(authorizationService.getUserPermissions(any(), any())).thenReturn(emptyList())

            // Act
            val authContext = authContextFactory.build(user)

            // Assert
            assertThat(authContext.roles).containsExactly("role1", "role2", "role3")
        }

        @Test
        fun `should extract permission names correctly from permissions`() {
            // Arrange
            val userId = UUID.randomUUID()
            val user = SecurityTestDataBuilders.user(id = userId, email = "test@example.com", displayName = "Test User")

            val permissions =
                listOf(
                    Permission(id = 1, name = "permission1"),
                    Permission(id = 2, name = "permission2"),
                    Permission(id = 3, name = "permission3"),
                )

            whenever(authorizationService.getUserRoles(any(), any())).thenReturn(emptyList())
            whenever(authorizationService.getUserPermissions(any(), any())).thenReturn(permissions)

            // Act
            val authContext = authContextFactory.build(user)

            // Assert
            assertThat(authContext.permissions).containsExactly("permission1", "permission2", "permission3")
        }
    }

    @Nested
    @DisplayName("Provider-Agnostic Behavior")
    inner class ProviderAgnosticTests {
        @Test
        fun `should produce identical AuthContext for same user regardless of password authentication`() {
            // Arrange - Simulate user authenticated via password
            val userId = UUID.randomUUID()
            val user =
                SecurityTestDataBuilders.user(
                    id = userId,
                    email = "test@example.com",
                    displayName = "Test User",
                    passwordHash = "hashed-password",
                )

            val roles =
                listOf(
                    Role(id = 1, name = "admin"),
                    Role(id = 2, name = "editor"),
                )
            val permissions =
                listOf(
                    Permission(id = 1, name = "transaction:read"),
                    Permission(id = 2, name = "transaction:write"),
                )

            whenever(authorizationService.getUserRoles(any(), any())).thenReturn(roles)
            whenever(authorizationService.getUserPermissions(any(), any())).thenReturn(permissions)

            // Act
            val authContext = authContextFactory.build(user)

            // Assert
            assertThat(authContext.userId).isEqualTo(userId)
            assertThat(authContext.email).isEqualTo("test@example.com")
            assertThat(authContext.roles).containsExactly("admin", "editor")
            assertThat(authContext.permissions).containsExactly("transaction:read", "transaction:write")
        }

        @Test
        fun `should produce identical AuthContext for same user regardless of OAuth authentication`() {
            // Arrange - Simulate user authenticated via OAuth (no password hash)
            val userId = UUID.randomUUID()
            // OAuth users don't have passwords
            val user =
                SecurityTestDataBuilders.user(
                    id = userId,
                    email = "test@example.com",
                    displayName = "OAuth User",
                    passwordHash = null,
                )

            val roles =
                listOf(
                    Role(id = 1, name = "admin"),
                    Role(id = 2, name = "editor"),
                )
            val permissions =
                listOf(
                    Permission(id = 1, name = "transaction:read"),
                    Permission(id = 2, name = "transaction:write"),
                )

            whenever(authorizationService.getUserRoles(any(), any())).thenReturn(roles)
            whenever(authorizationService.getUserPermissions(any(), any())).thenReturn(permissions)

            // Act
            val authContext = authContextFactory.build(user)

            // Assert
            assertThat(authContext.userId).isEqualTo(userId)
            assertThat(authContext.email).isEqualTo("test@example.com")
            assertThat(authContext.roles).containsExactly("admin", "editor")
            assertThat(authContext.permissions).containsExactly("transaction:read", "transaction:write")
        }

        @Test
        fun `should produce identical AuthContext for same user via password and OAuth authentication`() {
            // Arrange - Same user, different auth methods
            val userId = UUID.randomUUID()
            val email = "test@example.com"

            // User authenticated via password
            val passwordUser =
                SecurityTestDataBuilders.user(
                    id = userId,
                    email = email,
                    displayName = "Test User",
                    passwordHash = "hashed-password",
                )

            // Same user authenticated via OAuth
            val oauthUser =
                SecurityTestDataBuilders.user(
                    id = userId,
                    email = email,
                    displayName = "Test User",
                    passwordHash = null,
                )

            val roles =
                listOf(
                    Role(id = 1, name = "admin"),
                    Role(id = 2, name = "viewer"),
                )
            val permissions =
                listOf(
                    Permission(id = 1, name = "transaction:read"),
                    Permission(id = 2, name = "transaction:delete"),
                )

            whenever(authorizationService.getUserRoles(any(), any())).thenReturn(roles)
            whenever(authorizationService.getUserPermissions(any(), any())).thenReturn(permissions)

            // Act
            val passwordAuthContext = authContextFactory.build(passwordUser)
            val oauthAuthContext = authContextFactory.build(oauthUser)

            // Assert - Both should produce identical AuthContext
            assertThat(passwordAuthContext.userId).isEqualTo(oauthAuthContext.userId)
            assertThat(passwordAuthContext.email).isEqualTo(oauthAuthContext.email)
            assertThat(passwordAuthContext.roles).isEqualTo(oauthAuthContext.roles)
            assertThat(passwordAuthContext.permissions).isEqualTo(oauthAuthContext.permissions)
            assertThat(passwordAuthContext.roles).containsExactly("admin", "viewer")
            assertThat(passwordAuthContext.permissions).containsExactly("transaction:read", "transaction:delete")
        }

        @Test
        fun `should produce identical AuthContext for same user via Google OAuth provider`() {
            // Arrange - Simulate user authenticated via Google OAuth
            val userId = UUID.randomUUID()
            val user =
                SecurityTestDataBuilders.user(
                    id = userId,
                    email = "test@gmail.com",
                    displayName = "Google User",
                    passwordHash = null,
                )

            val roles =
                listOf(
                    Role(id = 1, name = "user"),
                )
            val permissions =
                listOf(
                    Permission(id = 1, name = "profile:read"),
                )

            whenever(authorizationService.getUserRoles(any(), any())).thenReturn(roles)
            whenever(authorizationService.getUserPermissions(any(), any())).thenReturn(permissions)

            // Act
            val authContext = authContextFactory.build(user)

            // Assert
            assertThat(authContext.userId).isEqualTo(userId)
            assertThat(authContext.email).isEqualTo("test@gmail.com")
            assertThat(authContext.roles).containsExactly("user")
            assertThat(authContext.permissions).containsExactly("profile:read")
        }

        @Test
        fun `should produce identical AuthContext for same user regardless of future OAuth provider`() {
            // Arrange - Simulate user authenticated via future OAuth provider (e.g., Microsoft, GitHub)
            val userId = UUID.randomUUID()
            val user =
                SecurityTestDataBuilders.user(
                    id = userId,
                    email = "test@example.com",
                    displayName = "Future Provider User",
                    passwordHash = null,
                )

            val roles =
                listOf(
                    Role(id = 1, name = "admin"),
                    Role(id = 2, name = "editor"),
                    Role(id = 3, name = "viewer"),
                )
            val permissions =
                listOf(
                    Permission(id = 1, name = "transaction:read"),
                    Permission(id = 2, name = "transaction:write"),
                    Permission(id = 3, name = "transaction:delete"),
                )

            whenever(authorizationService.getUserRoles(any(), any())).thenReturn(roles)
            whenever(authorizationService.getUserPermissions(any(), any())).thenReturn(permissions)

            // Act
            val authContext = authContextFactory.build(user)

            // Assert - Should work identically regardless of provider
            assertThat(authContext.userId).isEqualTo(userId)
            assertThat(authContext.email).isEqualTo("test@example.com")
            assertThat(authContext.roles).containsExactlyInAnyOrder("admin", "editor", "viewer")
            assertThat(authContext.permissions).containsExactlyInAnyOrder(
                "transaction:read",
                "transaction:write",
                "transaction:delete",
            )
        }

        @Test
        fun `should handle exceptions consistently regardless of authentication method`() {
            // Arrange - User from password auth
            val passwordUser =
                SecurityTestDataBuilders.user(
                    id = UUID.randomUUID(),
                    email = "password@example.com",
                    passwordHash = "hashed",
                )

            // User from OAuth
            val oauthUser =
                SecurityTestDataBuilders.user(
                    id = UUID.randomUUID(),
                    email = "oauth@example.com",
                    passwordHash = null,
                )

            whenever(authorizationService.getUserRoles(any(), any())).thenThrow(RuntimeException("Database error"))
            whenever(
                authorizationService.getUserPermissions(any(), any()),
            ).thenThrow(RuntimeException("Database error"))

            // Act
            val passwordAuthContext = authContextFactory.build(passwordUser)
            val oauthAuthContext = authContextFactory.build(oauthUser)

            // Assert - Both should handle exceptions the same way
            assertThat(passwordAuthContext.roles).isEmpty()
            assertThat(passwordAuthContext.permissions).isEmpty()
            assertThat(oauthAuthContext.roles).isEmpty()
            assertThat(oauthAuthContext.permissions).isEmpty()
            assertThat(passwordAuthContext.roles).isNotNull
            assertThat(passwordAuthContext.permissions).isNotNull
            assertThat(oauthAuthContext.roles).isNotNull
            assertThat(oauthAuthContext.permissions).isNotNull
        }
    }
}
