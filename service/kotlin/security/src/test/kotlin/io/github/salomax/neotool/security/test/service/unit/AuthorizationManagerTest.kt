package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.security.service.AuthorizationManager
import io.github.salomax.neotool.security.service.AuthorizationResult
import io.github.salomax.neotool.security.service.AuthorizationService
import io.github.salomax.neotool.security.service.RequestPrincipal
import io.github.salomax.neotool.security.service.exception.AuthorizationDeniedException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

@DisplayName("AuthorizationManager Unit Tests")
class AuthorizationManagerTest {
    private lateinit var authorizationService: AuthorizationService
    private lateinit var authorizationManager: AuthorizationManager

    @BeforeEach
    fun setUp() {
        authorizationService = mock()
        authorizationManager = AuthorizationManager(authorizationService)
    }

    @Nested
    @DisplayName("require")
    inner class RequireTests {
        @Test
        fun `should not throw exception when permission is allowed`() {
            // Arrange
            val userId = UUID.randomUUID()
            val principal =
                RequestPrincipal(
                    userId = userId,
                    token = "valid-token",
                    permissionsFromToken = listOf("security:user:view"),
                )
            val action = "security:user:view"

            whenever(
                authorizationService.checkPermission(
                    userId = any(),
                    permission = any(),
                    resourceType = anyOrNull(),
                    resourceId = anyOrNull(),
                    subjectAttributes = anyOrNull(),
                    resourceAttributes = anyOrNull(),
                    contextAttributes = anyOrNull(),
                ),
            ).thenReturn(
                AuthorizationResult(
                    allowed = true,
                    reason = "User has permission",
                ),
            )

            // Act & Assert - should not throw
            authorizationManager.require(principal, action)

            // Verify that subjectAttributes were enriched with permissions from token
            verify(authorizationService).checkPermission(
                userId = userId,
                permission = action,
                resourceType = null,
                resourceId = null,
                subjectAttributes = mapOf("principalPermissions" to principal.permissionsFromToken),
                resourceAttributes = null,
                contextAttributes = null,
            )
        }

        @Test
        fun `should throw AuthorizationDeniedException when permission is denied`() {
            // Arrange
            val userId = UUID.randomUUID()
            val principal =
                RequestPrincipal(
                    userId = userId,
                    token = "valid-token",
                    permissionsFromToken = emptyList(),
                )
            val action = "security:user:view"
            val reason = "User does not have permission"

            whenever(
                authorizationService.checkPermission(
                    userId = any(),
                    permission = any(),
                    resourceType = anyOrNull(),
                    resourceId = anyOrNull(),
                    subjectAttributes = anyOrNull(),
                    resourceAttributes = anyOrNull(),
                    contextAttributes = anyOrNull(),
                ),
            ).thenReturn(
                AuthorizationResult(
                    allowed = false,
                    reason = reason,
                ),
            )

            // Act & Assert
            assertThatThrownBy { authorizationManager.require(principal, action) }
                .isInstanceOf(AuthorizationDeniedException::class.java)
                .hasMessageContaining("User $userId lacks permission '$action'")
                .hasMessageContaining(reason)
        }

        @Test
        fun `should pass resourceType and resourceId to authorization service`() {
            // Arrange
            val userId = UUID.randomUUID()
            val resourceId = UUID.randomUUID()
            val principal =
                RequestPrincipal(
                    userId = userId,
                    token = "valid-token",
                    permissionsFromToken = listOf("security:user:view"),
                )
            val action = "security:user:view"
            val resourceType = "user"

            whenever(
                authorizationService.checkPermission(
                    userId = any(),
                    permission = any(),
                    resourceType = anyOrNull(),
                    resourceId = anyOrNull(),
                    subjectAttributes = anyOrNull(),
                    resourceAttributes = anyOrNull(),
                    contextAttributes = anyOrNull(),
                ),
            ).thenReturn(
                AuthorizationResult(
                    allowed = true,
                    reason = "User has permission",
                ),
            )

            // Act
            authorizationManager.require(
                principal = principal,
                action = action,
                resourceType = resourceType,
                resourceId = resourceId,
            )

            // Assert
            verify(authorizationService).checkPermission(
                userId = userId,
                permission = action,
                resourceType = resourceType,
                resourceId = resourceId,
                subjectAttributes = mapOf("principalPermissions" to principal.permissionsFromToken),
                resourceAttributes = null,
                contextAttributes = null,
            )
        }

        @Test
        fun `should pass resourceAttributes and contextAttributes to authorization service`() {
            // Arrange
            val userId = UUID.randomUUID()
            val principal =
                RequestPrincipal(
                    userId = userId,
                    token = "valid-token",
                    permissionsFromToken = listOf("security:user:view"),
                )
            val action = "security:user:view"
            val resourceAttributes = mapOf("status" to "active")
            val contextAttributes = mapOf("ip" to "127.0.0.1")

            whenever(
                authorizationService.checkPermission(
                    userId = any(),
                    permission = any(),
                    resourceType = anyOrNull(),
                    resourceId = anyOrNull(),
                    subjectAttributes = anyOrNull(),
                    resourceAttributes = anyOrNull(),
                    contextAttributes = anyOrNull(),
                ),
            ).thenReturn(
                AuthorizationResult(
                    allowed = true,
                    reason = "User has permission",
                ),
            )

            // Act
            authorizationManager.require(
                principal = principal,
                action = action,
                resourceAttributes = resourceAttributes,
                contextAttributes = contextAttributes,
            )

            // Assert
            verify(authorizationService).checkPermission(
                userId = userId,
                permission = action,
                resourceType = null,
                resourceId = null,
                subjectAttributes = mapOf("principalPermissions" to principal.permissionsFromToken),
                resourceAttributes = resourceAttributes,
                contextAttributes = contextAttributes,
            )
        }
    }
}
