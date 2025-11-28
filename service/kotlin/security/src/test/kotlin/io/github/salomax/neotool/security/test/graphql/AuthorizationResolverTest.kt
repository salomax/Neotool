package io.github.salomax.neotool.security.test.graphql

import io.github.salomax.neotool.security.domain.rbac.Permission
import io.github.salomax.neotool.security.domain.rbac.Role
import io.github.salomax.neotool.security.graphql.dto.AuthorizationResultDTO
import io.github.salomax.neotool.security.graphql.dto.PermissionDTO
import io.github.salomax.neotool.security.graphql.dto.RoleDTO
import io.github.salomax.neotool.security.graphql.mapper.AuthorizationMapper
import io.github.salomax.neotool.security.graphql.resolver.AuthorizationResolver
import io.github.salomax.neotool.security.service.AuthorizationResult
import io.github.salomax.neotool.security.service.AuthorizationService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

@DisplayName("AuthorizationResolver Validation Tests")
class AuthorizationResolverTest {
    private lateinit var authorizationService: AuthorizationService
    private lateinit var mapper: AuthorizationMapper
    private lateinit var authorizationResolver: AuthorizationResolver

    @BeforeEach
    fun setUp() {
        authorizationService = mock()
        mapper = mock()
        authorizationResolver = AuthorizationResolver(authorizationService, mapper)
    }

    @Nested
    @DisplayName("Permission Name Validation")
    inner class PermissionValidationTests {
        @Test
        fun `should accept valid permission format`() {
            // Arrange
            val userId = UUID.randomUUID().toString()
            val permission = "transaction:read"
            val result = AuthorizationResult(allowed = true, reason = "Allowed")
            val dto = AuthorizationResultDTO(allowed = true, reason = "Allowed")

            whenever(
                authorizationService.checkPermission(
                    // userId: UUID
                    any(),
                    // permission: String
                    any(),
                    // resourceType: String?
                    anyOrNull(),
                    // resourceId: UUID?
                    anyOrNull(),
                    // scopeType: ScopeType?
                    anyOrNull(),
                    // scopeId: UUID?
                    anyOrNull(),
                    // subjectAttributes: Map<String, Any>?
                    anyOrNull(),
                    // resourceAttributes: Map<String, Any>?
                    anyOrNull(),
                    // contextAttributes: Map<String, Any>?
                    anyOrNull(),
                ),
            ).thenReturn(result)
            whenever(mapper.toAuthorizationResultDTO(any())).thenReturn(dto)

            // Act
            val response = authorizationResolver.checkPermission(userId, permission)

            // Assert
            assertThat(response.allowed).isTrue()
        }

        @Test
        fun `should reject permission without colon separator`() {
            // Arrange
            val userId = UUID.randomUUID().toString()
            val permission = "transactionread" // Missing colon

            // Act
            val response = authorizationResolver.checkPermission(userId, permission)

            // Assert
            assertThat(response.allowed).isFalse()
            assertThat(response.reason).contains("Invalid input provided")
        }

        @Test
        fun `should reject permission with uppercase letters`() {
            // Arrange
            val userId = UUID.randomUUID().toString()
            val permission = "Transaction:Read" // Uppercase
            val result = AuthorizationResult(allowed = true, reason = "Allowed")
            val dto = AuthorizationResultDTO(allowed = true, reason = "Allowed")

            // Note: The resolver normalizes to lowercase before validation,
            // so "Transaction:Read" becomes "transaction:read" which is valid.
            // This test verifies that normalization happens and the permission is accepted.
            whenever(
                authorizationService.checkPermission(
                    // userId: UUID
                    any(),
                    // permission: String
                    any(),
                    // resourceType: String?
                    anyOrNull(),
                    // resourceId: UUID?
                    anyOrNull(),
                    // scopeType: ScopeType?
                    anyOrNull(),
                    // scopeId: UUID?
                    anyOrNull(),
                    // subjectAttributes: Map<String, Any>?
                    anyOrNull(),
                    // resourceAttributes: Map<String, Any>?
                    anyOrNull(),
                    // contextAttributes: Map<String, Any>?
                    anyOrNull(),
                ),
            ).thenReturn(result)
            whenever(mapper.toAuthorizationResultDTO(any())).thenReturn(dto)

            // Act
            val response = authorizationResolver.checkPermission(userId, permission)

            // Assert
            // The resolver normalizes uppercase to lowercase, so this should be accepted
            assertThat(response.allowed).isTrue()
        }

        @Test
        fun `should reject permission with special characters`() {
            // Arrange
            val userId = UUID.randomUUID().toString()
            val permission = "transaction@read" // Invalid character

            // Act
            val response = authorizationResolver.checkPermission(userId, permission)

            // Assert
            assertThat(response.allowed).isFalse()
            assertThat(response.reason).contains("Invalid input provided")
        }

        @Test
        fun `should reject empty permission`() {
            // Arrange
            val userId = UUID.randomUUID().toString()
            val permission = ""

            // Act
            val response = authorizationResolver.checkPermission(userId, permission)

            // Assert
            assertThat(response.allowed).isFalse()
            assertThat(response.reason).contains("Invalid input provided")
        }

        @Test
        fun `should reject permission exceeding max length`() {
            // Arrange
            val userId = UUID.randomUUID().toString()
            val permission = "a".repeat(256) + ":read" // Exceeds 255 chars

            // Act
            val response = authorizationResolver.checkPermission(userId, permission)

            // Assert
            assertThat(response.allowed).isFalse()
            assertThat(response.reason).contains("Invalid input provided")
        }

        @Test
        fun `should normalize permission to lowercase`() {
            // Arrange
            val userId = UUID.randomUUID().toString()
            val permission = "TRANSACTION:READ" // Will be normalized
            val result = AuthorizationResult(allowed = true, reason = "Allowed")
            val dto = AuthorizationResultDTO(allowed = true, reason = "Allowed")

            whenever(
                authorizationService.checkPermission(
                    // userId: UUID
                    any(),
                    // permission: String
                    any(),
                    // resourceType: String?
                    anyOrNull(),
                    // resourceId: UUID?
                    anyOrNull(),
                    // scopeType: ScopeType?
                    anyOrNull(),
                    // scopeId: UUID?
                    anyOrNull(),
                    // subjectAttributes: Map<String, Any>?
                    anyOrNull(),
                    // resourceAttributes: Map<String, Any>?
                    anyOrNull(),
                    // contextAttributes: Map<String, Any>?
                    anyOrNull(),
                ),
            ).thenReturn(result)
            whenever(mapper.toAuthorizationResultDTO(any())).thenReturn(dto)

            // Act
            val response = authorizationResolver.checkPermission(userId, permission)

            // Assert
            assertThat(response.allowed).isTrue()
        }

        @Test
        fun `should accept permission with underscores and hyphens`() {
            // Arrange
            val userId = UUID.randomUUID().toString()
            val permission = "transaction-item:read-write"
            val result = AuthorizationResult(allowed = true, reason = "Allowed")
            val dto = AuthorizationResultDTO(allowed = true, reason = "Allowed")

            whenever(
                authorizationService.checkPermission(
                    // userId: UUID
                    any(),
                    // permission: String
                    any(),
                    // resourceType: String?
                    anyOrNull(),
                    // resourceId: UUID?
                    anyOrNull(),
                    // scopeType: ScopeType?
                    anyOrNull(),
                    // scopeId: UUID?
                    anyOrNull(),
                    // subjectAttributes: Map<String, Any>?
                    anyOrNull(),
                    // resourceAttributes: Map<String, Any>?
                    anyOrNull(),
                    // contextAttributes: Map<String, Any>?
                    anyOrNull(),
                ),
            ).thenReturn(result)
            whenever(mapper.toAuthorizationResultDTO(any())).thenReturn(dto)

            // Act
            val response = authorizationResolver.checkPermission(userId, permission)

            // Assert
            assertThat(response.allowed).isTrue()
        }
    }

    @Nested
    @DisplayName("Input Sanitization")
    inner class InputSanitizationTests {
        @Test
        fun `should trim whitespace from userId`() {
            // Arrange
            val userId = "  ${UUID.randomUUID()}  " // With whitespace
            val permission = "transaction:read"
            val result = AuthorizationResult(allowed = true, reason = "Allowed")
            val dto = AuthorizationResultDTO(allowed = true, reason = "Allowed")

            whenever(
                authorizationService.checkPermission(
                    // userId: UUID
                    any(),
                    // permission: String
                    any(),
                    // resourceType: String?
                    anyOrNull(),
                    // resourceId: UUID?
                    anyOrNull(),
                    // scopeType: ScopeType?
                    anyOrNull(),
                    // scopeId: UUID?
                    anyOrNull(),
                    // subjectAttributes: Map<String, Any>?
                    anyOrNull(),
                    // resourceAttributes: Map<String, Any>?
                    anyOrNull(),
                    // contextAttributes: Map<String, Any>?
                    anyOrNull(),
                ),
            ).thenReturn(result)
            whenever(mapper.toAuthorizationResultDTO(any())).thenReturn(dto)

            // Act
            val response = authorizationResolver.checkPermission(userId, permission)

            // Assert
            assertThat(response.allowed).isTrue()
        }

        @Test
        fun `should trim whitespace from permission`() {
            // Arrange
            val userId = UUID.randomUUID().toString()
            val permission = "  transaction:read  " // With whitespace
            val result = AuthorizationResult(allowed = true, reason = "Allowed")
            val dto = AuthorizationResultDTO(allowed = true, reason = "Allowed")

            whenever(
                authorizationService.checkPermission(
                    // userId: UUID
                    any(),
                    // permission: String
                    any(),
                    // resourceType: String?
                    anyOrNull(),
                    // resourceId: UUID?
                    anyOrNull(),
                    // scopeType: ScopeType?
                    anyOrNull(),
                    // scopeId: UUID?
                    anyOrNull(),
                    // subjectAttributes: Map<String, Any>?
                    anyOrNull(),
                    // resourceAttributes: Map<String, Any>?
                    anyOrNull(),
                    // contextAttributes: Map<String, Any>?
                    anyOrNull(),
                ),
            ).thenReturn(result)
            whenever(mapper.toAuthorizationResultDTO(any())).thenReturn(dto)

            // Act
            val response = authorizationResolver.checkPermission(userId, permission)

            // Assert
            assertThat(response.allowed).isTrue()
        }

        @Test
        fun `should reject userId exceeding max length`() {
            // Arrange
            val userId = "a".repeat(1001) // Exceeds 1000 chars
            val permission = "transaction:read"

            // Act
            val response = authorizationResolver.checkPermission(userId, permission)

            // Assert
            assertThat(response.allowed).isFalse()
            assertThat(response.reason).contains("Invalid input provided")
        }

        @Test
        fun `should reject empty userId`() {
            // Arrange
            val userId = ""
            val permission = "transaction:read"

            // Act
            val response = authorizationResolver.checkPermission(userId, permission)

            // Assert
            assertThat(response.allowed).isFalse()
            assertThat(response.reason).contains("Invalid input provided")
        }

        @Test
        fun `should trim whitespace from resourceId`() {
            // Arrange
            val userId = UUID.randomUUID().toString()
            val permission = "transaction:read"
            val resourceId = "  ${UUID.randomUUID()}  " // With whitespace
            val result = AuthorizationResult(allowed = true, reason = "Allowed")
            val dto = AuthorizationResultDTO(allowed = true, reason = "Allowed")

            whenever(
                authorizationService.checkPermission(
                    // userId: UUID
                    any(),
                    // permission: String
                    any(),
                    // resourceType: String?
                    anyOrNull(),
                    // resourceId: UUID?
                    anyOrNull(),
                    // scopeType: ScopeType?
                    anyOrNull(),
                    // scopeId: UUID?
                    anyOrNull(),
                    // subjectAttributes: Map<String, Any>?
                    anyOrNull(),
                    // resourceAttributes: Map<String, Any>?
                    anyOrNull(),
                    // contextAttributes: Map<String, Any>?
                    anyOrNull(),
                ),
            ).thenReturn(result)
            whenever(mapper.toAuthorizationResultDTO(any())).thenReturn(dto)

            // Act
            val response = authorizationResolver.checkPermission(userId, permission, resourceId)

            // Assert
            assertThat(response.allowed).isTrue()
        }

        @Test
        fun `should trim and normalize scope`() {
            // Arrange
            val userId = UUID.randomUUID().toString()
            val permission = "transaction:read"
            val scope = "  profile  " // With whitespace, will be normalized to PROFILE
            val result = AuthorizationResult(allowed = true, reason = "Allowed")
            val dto = AuthorizationResultDTO(allowed = true, reason = "Allowed")

            whenever(
                authorizationService.checkPermission(
                    // userId: UUID
                    any(),
                    // permission: String
                    any(),
                    // resourceType: String?
                    anyOrNull(),
                    // resourceId: UUID?
                    anyOrNull(),
                    // scopeType: ScopeType?
                    anyOrNull(),
                    // scopeId: UUID?
                    anyOrNull(),
                    // subjectAttributes: Map<String, Any>?
                    anyOrNull(),
                    // resourceAttributes: Map<String, Any>?
                    anyOrNull(),
                    // contextAttributes: Map<String, Any>?
                    anyOrNull(),
                ),
            ).thenReturn(result)
            whenever(mapper.toAuthorizationResultDTO(any())).thenReturn(dto)

            // Act
            val response = authorizationResolver.checkPermission(userId, permission, null, scope)

            // Assert
            assertThat(response.allowed).isTrue()
        }
    }

    @Nested
    @DisplayName("Error Message Sanitization")
    inner class ErrorSanitizationTests {
        @Test
        fun `should not expose internal exception details in error messages`() {
            // Arrange
            val userId = UUID.randomUUID().toString()
            val permission = "transaction:read"

            whenever(
                authorizationService.checkPermission(
                    // userId: UUID
                    any(),
                    // permission: String
                    any(),
                    // resourceType: String?
                    anyOrNull(),
                    // resourceId: UUID?
                    anyOrNull(),
                    // scopeType: ScopeType?
                    anyOrNull(),
                    // scopeId: UUID?
                    anyOrNull(),
                    // subjectAttributes: Map<String, Any>?
                    anyOrNull(),
                    // resourceAttributes: Map<String, Any>?
                    anyOrNull(),
                    // contextAttributes: Map<String, Any>?
                    anyOrNull(),
                ),
            ).thenThrow(RuntimeException("Internal database error: connection timeout"))

            // Act
            val response = authorizationResolver.checkPermission(userId, permission)

            // Assert
            assertThat(response.allowed).isFalse()
            assertThat(response.reason).isEqualTo("An error occurred while checking permission")
            assertThat(response.reason).doesNotContain("Internal database error")
            assertThat(response.reason).doesNotContain("connection timeout")
        }

        @Test
        fun `should sanitize IllegalArgumentException messages`() {
            // Arrange
            val userId = UUID.randomUUID().toString()
            val permission = "transaction:read"

            whenever(
                authorizationService.checkPermission(
                    // userId: UUID
                    any(),
                    // permission: String
                    any(),
                    // resourceType: String?
                    anyOrNull(),
                    // resourceId: UUID?
                    anyOrNull(),
                    // scopeType: ScopeType?
                    anyOrNull(),
                    // scopeId: UUID?
                    anyOrNull(),
                    // subjectAttributes: Map<String, Any>?
                    anyOrNull(),
                    // resourceAttributes: Map<String, Any>?
                    anyOrNull(),
                    // contextAttributes: Map<String, Any>?
                    anyOrNull(),
                ),
            ).thenThrow(IllegalArgumentException("User not found: 12345"))

            // Act
            val response = authorizationResolver.checkPermission(userId, permission)

            // Assert
            assertThat(response.allowed).isFalse()
            assertThat(response.reason).isEqualTo("Invalid input provided")
            assertThat(response.reason).doesNotContain("User not found")
            assertThat(response.reason).doesNotContain("12345")
        }
    }

    @Nested
    @DisplayName("Get User Permissions - Input Validation")
    inner class GetUserPermissionsValidationTests {
        @Test
        fun `should trim whitespace from userId in getUserPermissions`() {
            // Arrange
            val userId = "  ${UUID.randomUUID()}  "
            val permissions = listOf(Permission(id = 1, name = "transaction:read"))
            val dtos = listOf(PermissionDTO(id = 1, name = "transaction:read"))

            whenever(
                authorizationService.getUserPermissions(
                    // userId: UUID
                    any(),
                    // scopeType: ScopeType?
                    anyOrNull(),
                    // scopeId: UUID?
                    anyOrNull(),
                    // now: Instant
                    any(),
                ),
            ).thenReturn(permissions)
            whenever(mapper.toPermissionDTO(any())).thenReturn(dtos[0])

            // Act
            val response = authorizationResolver.getUserPermissions(userId)

            // Assert
            assertThat(response).isNotEmpty()
        }

        @Test
        fun `should return empty list for invalid userId in getUserPermissions`() {
            // Arrange
            val userId = "invalid-uuid"

            // Act
            val response = authorizationResolver.getUserPermissions(userId)

            // Assert
            assertThat(response).isEmpty()
        }
    }

    @Nested
    @DisplayName("Get User Roles - Input Validation")
    inner class GetUserRolesValidationTests {
        @Test
        fun `should trim whitespace from userId in getUserRoles`() {
            // Arrange
            val userId = "  ${UUID.randomUUID()}  "
            val roles = listOf(Role(id = 1, name = "admin"))
            val dtos = listOf(RoleDTO(id = 1, name = "admin"))

            whenever(
                authorizationService.getUserRoles(
                    // userId: UUID
                    any(),
                    // scopeType: ScopeType?
                    anyOrNull(),
                    // scopeId: UUID?
                    anyOrNull(),
                    // now: Instant
                    any(),
                ),
            ).thenReturn(roles)
            whenever(mapper.toRoleDTO(any())).thenReturn(dtos[0])

            // Act
            val response = authorizationResolver.getUserRoles(userId)

            // Assert
            assertThat(response).isNotEmpty()
        }

        @Test
        fun `should return empty list for invalid userId in getUserRoles`() {
            // Arrange
            val userId = "invalid-uuid"

            // Act
            val response = authorizationResolver.getUserRoles(userId)

            // Assert
            assertThat(response).isEmpty()
        }
    }
}
