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

    /**
     * Helper method to mock successful authorization check.
     * Reduces duplication across tests.
     */
    private fun mockSuccessfulAuthorization() {
        whenever(
            authorizationService.checkPermission(
                principal = any(),
                permission = any(),
                resourceType = anyOrNull(),
                resourceId = anyOrNull(),
                resourcePattern = anyOrNull(),
                subjectAttributes = any(),
                resourceAttributes = anyOrNull(),
                contextAttributes = anyOrNull(),
            ),
        ).thenReturn(
            AuthorizationResult(
                allowed = true,
                reason = "User has permission",
            ),
        )
    }

    /**
     * Helper method to mock failed authorization check.
     * Reduces duplication across tests.
     */
    private fun mockFailedAuthorization(reason: String = "User does not have permission") {
        whenever(
            authorizationService.checkPermission(
                principal = any(),
                permission = any(),
                resourceType = anyOrNull(),
                resourceId = anyOrNull(),
                resourcePattern = anyOrNull(),
                subjectAttributes = any(),
                resourceAttributes = anyOrNull(),
                contextAttributes = anyOrNull(),
            ),
        ).thenReturn(
            AuthorizationResult(
                allowed = false,
                reason = reason,
            ),
        )
    }

    /**
     * Helper method to verify checkPermission was called with expected arguments.
     * Reduces duplication across tests.
     */
    private fun verifyCheckPermissionCalled(
        principal: RequestPrincipal,
        permission: String,
        resourceType: String? = null,
        resourceId: UUID? = null,
        resourceAttributes: Map<String, Any>? = null,
        contextAttributes: Map<String, Any>? = null,
    ) {
        verify(authorizationService).checkPermission(
            principal = principal,
            permission = permission,
            resourceType = resourceType,
            resourceId = resourceId,
            resourcePattern = null,
            subjectAttributes = mapOf("principalPermissions" to principal.permissionsFromToken),
            resourceAttributes = resourceAttributes,
            contextAttributes = contextAttributes,
        )
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

            mockSuccessfulAuthorization()

            // Act & Assert - should not throw
            authorizationManager.require(principal, action)

            // Verify that subjectAttributes were enriched with permissions from token
            verifyCheckPermissionCalled(
                principal = principal,
                permission = action,
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

            mockFailedAuthorization(reason)

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

            mockSuccessfulAuthorization()

            // Act
            authorizationManager.require(
                principal = principal,
                action = action,
                resourceType = resourceType,
                resourceId = resourceId,
            )

            // Assert
            verifyCheckPermissionCalled(
                principal = principal,
                permission = action,
                resourceType = resourceType,
                resourceId = resourceId,
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

            mockSuccessfulAuthorization()

            // Act
            authorizationManager.require(
                principal = principal,
                action = action,
                resourceAttributes = resourceAttributes,
                contextAttributes = contextAttributes,
            )

            // Assert
            verifyCheckPermissionCalled(
                principal = principal,
                permission = action,
                resourceAttributes = resourceAttributes,
                contextAttributes = contextAttributes,
            )
        }
    }
}
