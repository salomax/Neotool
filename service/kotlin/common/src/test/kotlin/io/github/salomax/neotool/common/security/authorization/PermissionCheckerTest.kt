package io.github.salomax.neotool.common.security.authorization

import io.github.salomax.neotool.common.security.exception.AuthorizationDeniedException
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

@DisplayName("PermissionChecker Unit Tests")
class PermissionCheckerTest {
    private lateinit var permissionChecker: PermissionChecker

    @BeforeEach
    fun setUp() {
        permissionChecker = PermissionChecker()
    }

    @Nested
    @DisplayName("User Principal Tests")
    inner class UserPrincipalTests {
        @Test
        fun `should grant permission when user has the required permission`() {
            // Arrange
            val userId = UUID.randomUUID()
            val token = "user-token"
            val permission = "assets:asset:view"
            val principal =
                RequestPrincipal(
                    principalType = PrincipalType.USER,
                    userId = userId,
                    serviceId = null,
                    token = token,
                    permissionsFromToken = listOf("assets:asset:view", "assets:asset:edit"),
                    userPermissions = null,
                )

            // Act & Assert - Should not throw
            permissionChecker.require(principal, permission)
        }

        @Test
        fun `should deny permission when user lacks the required permission`() {
            // Arrange
            val userId = UUID.randomUUID()
            val token = "user-token"
            val permission = "assets:asset:delete"
            val principal =
                RequestPrincipal(
                    principalType = PrincipalType.USER,
                    userId = userId,
                    serviceId = null,
                    token = token,
                    permissionsFromToken = listOf("assets:asset:view", "assets:asset:edit"),
                    userPermissions = null,
                )

            // Act & Assert
            assertThatThrownBy { permissionChecker.require(principal, permission) }
                .isInstanceOf(AuthorizationDeniedException::class.java)
                .hasMessageContaining("User $userId lacks permission '$permission'")
        }

        @Test
        fun `should deny permission when user has no permissions`() {
            // Arrange
            val userId = UUID.randomUUID()
            val token = "user-token"
            val permission = "assets:asset:view"
            val principal =
                RequestPrincipal(
                    principalType = PrincipalType.USER,
                    userId = userId,
                    serviceId = null,
                    token = token,
                    permissionsFromToken = emptyList(),
                    userPermissions = null,
                )

            // Act & Assert
            assertThatThrownBy { permissionChecker.require(principal, permission) }
                .isInstanceOf(AuthorizationDeniedException::class.java)
                .hasMessageContaining("User $userId lacks permission '$permission'")
        }
    }

    @Nested
    @DisplayName("Service Principal Tests")
    inner class ServicePrincipalTests {
        @Test
        fun `should grant permission when service has the required permission`() {
            // Arrange
            val serviceId = UUID.randomUUID()
            val token = "service-token"
            val permission = "assets:asset:view"
            val principal =
                RequestPrincipal(
                    principalType = PrincipalType.SERVICE,
                    userId = null,
                    serviceId = serviceId,
                    token = token,
                    permissionsFromToken = listOf("assets:asset:view", "assets:asset:edit"),
                    userPermissions = null,
                )

            // Act & Assert - Should not throw
            permissionChecker.require(principal, permission)
        }

        @Test
        fun `should deny permission when service lacks the required permission`() {
            // Arrange
            val serviceId = UUID.randomUUID()
            val token = "service-token"
            val permission = "assets:asset:delete"
            val principal =
                RequestPrincipal(
                    principalType = PrincipalType.SERVICE,
                    userId = null,
                    serviceId = serviceId,
                    token = token,
                    permissionsFromToken = listOf("assets:asset:view", "assets:asset:edit"),
                    userPermissions = null,
                )

            // Act & Assert
            assertThatThrownBy { permissionChecker.require(principal, permission) }
                .isInstanceOf(AuthorizationDeniedException::class.java)
                .hasMessageContaining("Service $serviceId lacks permission '$permission'")
        }

        @Test
        fun `should deny permission when service has no permissions`() {
            // Arrange
            val serviceId = UUID.randomUUID()
            val token = "service-token"
            val permission = "assets:asset:view"
            val principal =
                RequestPrincipal(
                    principalType = PrincipalType.SERVICE,
                    userId = null,
                    serviceId = serviceId,
                    token = token,
                    permissionsFromToken = emptyList(),
                    userPermissions = null,
                )

            // Act & Assert
            assertThatThrownBy { permissionChecker.require(principal, permission) }
                .isInstanceOf(AuthorizationDeniedException::class.java)
                .hasMessageContaining("Service $serviceId lacks permission '$permission'")
        }
    }

    @Nested
    @DisplayName("Service Token with User Context Tests")
    inner class ServiceTokenWithUserContextTests {
        @Test
        fun `should use user permissions when service token has user context and user permissions`() {
            // Arrange
            val serviceId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val token = "service-token-with-user-context"
            val permission = "assets:asset:view"
            val servicePermissions = listOf("service:admin")
            val userPermissions = listOf("assets:asset:view", "assets:asset:edit")
            val principal =
                RequestPrincipal(
                    principalType = PrincipalType.SERVICE,
                    userId = userId,
                    serviceId = serviceId,
                    token = token,
                    permissionsFromToken = servicePermissions,
                    userPermissions = userPermissions,
                )

            // Act & Assert - Should use user permissions, not service permissions
            permissionChecker.require(principal, permission)

            // Verify it used user permissions by checking that service permission would fail
            val serviceOnlyPermission = "service:admin"
            assertThatThrownBy { permissionChecker.require(principal, serviceOnlyPermission) }
                .isInstanceOf(AuthorizationDeniedException::class.java)
                .hasMessageContaining(
                    "Service $serviceId (with user $userId) lacks permission '$serviceOnlyPermission'",
                )
        }

        @Test
        fun `should deny permission when user permissions do not include required permission`() {
            // Arrange
            val serviceId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val token = "service-token-with-user-context"
            val permission = "assets:asset:delete"
            val servicePermissions = listOf("service:admin")
            val userPermissions = listOf("assets:asset:view", "assets:asset:edit")
            val principal =
                RequestPrincipal(
                    principalType = PrincipalType.SERVICE,
                    userId = userId,
                    serviceId = serviceId,
                    token = token,
                    permissionsFromToken = servicePermissions,
                    userPermissions = userPermissions,
                )

            // Act & Assert
            assertThatThrownBy { permissionChecker.require(principal, permission) }
                .isInstanceOf(AuthorizationDeniedException::class.java)
                .hasMessageContaining("Service $serviceId (with user $userId) lacks permission '$permission'")
        }

        @Test
        fun `should fall back to service permissions when service token has user_id but no user_permissions`() {
            // Arrange
            val serviceId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val token = "service-token-with-user-id-only"
            val permission = "service:admin"
            val servicePermissions = listOf("service:admin", "service:read")
            val principal =
                RequestPrincipal(
                    principalType = PrincipalType.SERVICE,
                    userId = userId,
                    serviceId = serviceId,
                    token = token,
                    permissionsFromToken = servicePermissions,
                    // No user permissions provided
                    userPermissions = null,
                )

            // Act & Assert - Should use service permissions (fallback)
            permissionChecker.require(principal, permission)

            // Verify it used service permissions by checking that a permission not in service perms would fail
            val userOnlyPermission = "assets:asset:view"
            assertThatThrownBy { permissionChecker.require(principal, userOnlyPermission) }
                .isInstanceOf(AuthorizationDeniedException::class.java)
                .hasMessageContaining("Service $serviceId (with user $userId) lacks permission '$userOnlyPermission'")
        }

        @Test
        fun `should include user context in error message when service token has user_id`() {
            // Arrange
            val serviceId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val token = "service-token-with-user-context"
            val permission = "assets:asset:delete"
            val principal =
                RequestPrincipal(
                    principalType = PrincipalType.SERVICE,
                    userId = userId,
                    serviceId = serviceId,
                    token = token,
                    permissionsFromToken = listOf("service:admin"),
                    userPermissions = listOf("assets:asset:view"),
                )

            // Act & Assert
            assertThatThrownBy { permissionChecker.require(principal, permission) }
                .isInstanceOf(AuthorizationDeniedException::class.java)
                .hasMessageContaining("Service $serviceId (with user $userId) lacks permission '$permission'")
        }

        @Test
        fun `should not include user context in error message when service token has no user_id`() {
            // Arrange
            val serviceId = UUID.randomUUID()
            val token = "service-token-without-user-context"
            val permission = "assets:asset:delete"
            val principal =
                RequestPrincipal(
                    principalType = PrincipalType.SERVICE,
                    userId = null,
                    serviceId = serviceId,
                    token = token,
                    permissionsFromToken = listOf("service:admin"),
                    userPermissions = null,
                )

            // Act & Assert
            assertThatThrownBy { permissionChecker.require(principal, permission) }
                .isInstanceOf(AuthorizationDeniedException::class.java)
                .hasMessageContaining("Service $serviceId lacks permission '$permission'")
                .hasMessageNotContaining("with user")
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCasesTests {
        @Test
        fun `should handle empty permission string`() {
            // Arrange
            val userId = UUID.randomUUID()
            val principal =
                RequestPrincipal(
                    principalType = PrincipalType.USER,
                    userId = userId,
                    serviceId = null,
                    token = "token",
                    permissionsFromToken = listOf(""),
                    userPermissions = null,
                )

            // Act & Assert - Empty string permission should be checked
            permissionChecker.require(principal, "")
        }

        @Test
        fun `should handle permission with special characters`() {
            // Arrange
            val userId = UUID.randomUUID()
            val permission = "assets:asset:view:special:chars:123"
            val principal =
                RequestPrincipal(
                    principalType = PrincipalType.USER,
                    userId = userId,
                    serviceId = null,
                    token = "token",
                    permissionsFromToken = listOf(permission),
                    userPermissions = null,
                )

            // Act & Assert - Should not throw
            permissionChecker.require(principal, permission)
        }

        @Test
        fun `should be case sensitive for permissions`() {
            // Arrange
            val userId = UUID.randomUUID()
            val principal =
                RequestPrincipal(
                    principalType = PrincipalType.USER,
                    userId = userId,
                    serviceId = null,
                    token = "token",
                    permissionsFromToken = listOf("assets:asset:view"),
                    userPermissions = null,
                )

            // Act & Assert - Case sensitive check
            assertThatThrownBy { permissionChecker.require(principal, "Assets:Asset:View") }
                .isInstanceOf(AuthorizationDeniedException::class.java)
        }
    }
}
