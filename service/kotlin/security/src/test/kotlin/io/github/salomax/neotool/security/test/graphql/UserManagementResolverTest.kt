package io.github.salomax.neotool.security.test.graphql

import io.github.salomax.neotool.security.domain.rbac.Group
import io.github.salomax.neotool.security.domain.rbac.Permission
import io.github.salomax.neotool.security.domain.rbac.Role
import io.github.salomax.neotool.security.graphql.mapper.UserManagementMapper
import io.github.salomax.neotool.security.graphql.resolver.UserManagementResolver
import io.github.salomax.neotool.security.service.authorization.AuthorizationService
import io.github.salomax.neotool.security.service.management.UserManagementService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

@DisplayName("UserManagementResolver Batch Methods Tests")
class UserManagementResolverTest {
    private lateinit var userManagementService: UserManagementService
    private lateinit var authorizationService: AuthorizationService
    private lateinit var mapper: UserManagementMapper
    private lateinit var userManagementResolver: UserManagementResolver

    @BeforeEach
    fun setUp() {
        userManagementService = mock()
        authorizationService = mock()
        mapper = mock()
        userManagementResolver =
            UserManagementResolver(
                userManagementService,
                authorizationService,
                mapper,
            )
    }

    @Nested
    @DisplayName("resolveUserRolesBatch")
    inner class ResolveUserRolesBatchTests {
        @Test
        fun `should return roles for multiple users`() {
            // Arrange
            val userId1 = UUID.randomUUID()
            val userId2 = UUID.randomUUID()
            val role1 =
                Role(
                    id = UUID.randomUUID(),
                    name = "admin",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            val role2 =
                Role(
                    id = UUID.randomUUID(),
                    name = "editor",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            whenever(authorizationService.getUserRolesBatch(any<List<UUID>>(), any()))
                .thenReturn(
                    mapOf(
                        userId1 to listOf(role1),
                        userId2 to listOf(role2),
                    ),
                )

            // Act
            val result = userManagementResolver.resolveUserRolesBatch(listOf(userId1.toString(), userId2.toString()))

            // Assert
            assertThat(result).hasSize(2)
            assertThat(result[userId1.toString()]).hasSize(1)
            assertThat(result[userId1.toString()]!![0].name).isEqualTo("admin")
            assertThat(result[userId2.toString()]).hasSize(1)
            assertThat(result[userId2.toString()]!![0].name).isEqualTo("editor")
            verify(authorizationService).getUserRolesBatch(any<List<UUID>>(), any())
        }

        @Test
        fun `should return empty map for empty user list`() {
            // Act
            val result = userManagementResolver.resolveUserRolesBatch(emptyList())

            // Assert
            assertThat(result).isEmpty()
        }

        @Test
        fun `should filter out invalid user IDs`() {
            // Arrange
            val userId1 = UUID.randomUUID()
            val invalidId = "not-a-uuid"
            val role1 =
                Role(
                    id = UUID.randomUUID(),
                    name = "admin",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            whenever(authorizationService.getUserRolesBatch(any<List<UUID>>(), any()))
                .thenReturn(mapOf(userId1 to listOf(role1)))

            // Act
            val result = userManagementResolver.resolveUserRolesBatch(listOf(userId1.toString(), invalidId))

            // Assert
            assertThat(result).hasSize(1)
            assertThat(result[userId1.toString()]).hasSize(1)
            assertThat(result).doesNotContainKey(invalidId)
        }

        @Test
        fun `should preserve order of requested user IDs`() {
            // Arrange
            val userId1 = UUID.randomUUID()
            val userId2 = UUID.randomUUID()
            val userId3 = UUID.randomUUID()
            val role1 =
                Role(
                    id = UUID.randomUUID(),
                    name = "admin",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            val role2 =
                Role(
                    id = UUID.randomUUID(),
                    name = "editor",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            whenever(authorizationService.getUserRolesBatch(any<List<UUID>>(), any()))
                .thenReturn(
                    mapOf(
                        userId1 to listOf(role1),
                        userId2 to emptyList(),
                        userId3 to listOf(role2),
                    ),
                )

            // Act
            val result =
                userManagementResolver.resolveUserRolesBatch(
                    listOf(userId1.toString(), userId2.toString(), userId3.toString()),
                )

            // Assert
            assertThat(result.keys.toList()).containsExactly(userId1.toString(), userId2.toString(), userId3.toString())
        }
    }

    @Nested
    @DisplayName("resolveUserGroupsBatch")
    inner class ResolveUserGroupsBatchTests {
        @Test
        fun `should return groups for multiple users`() {
            // Arrange
            val userId1 = UUID.randomUUID()
            val userId2 = UUID.randomUUID()
            val group1 =
                Group(
                    id = UUID.randomUUID(),
                    name = "Group One",
                    description = "Desc 1",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            val group2 =
                Group(
                    id = UUID.randomUUID(),
                    name = "Group Two",
                    description = "Desc 2",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            whenever(userManagementService.getUserGroupsBatch(any<List<UUID>>(), any()))
                .thenReturn(
                    mapOf(
                        userId1 to listOf(group1),
                        userId2 to listOf(group2),
                    ),
                )

            // Act
            val result = userManagementResolver.resolveUserGroupsBatch(listOf(userId1.toString(), userId2.toString()))

            // Assert
            assertThat(result).hasSize(2)
            assertThat(result[userId1.toString()]).hasSize(1)
            assertThat(result[userId1.toString()]!![0].name).isEqualTo("Group One")
            assertThat(result[userId2.toString()]).hasSize(1)
            assertThat(result[userId2.toString()]!![0].name).isEqualTo("Group Two")
            verify(userManagementService).getUserGroupsBatch(any<List<UUID>>(), any())
        }

        @Test
        fun `should return empty map for empty user list`() {
            // Act
            val result = userManagementResolver.resolveUserGroupsBatch(emptyList())

            // Assert
            assertThat(result).isEmpty()
        }

        @Test
        fun `should filter out invalid user IDs`() {
            // Arrange
            val userId1 = UUID.randomUUID()
            val invalidId = "not-a-uuid"
            val group1 =
                Group(
                    id = UUID.randomUUID(),
                    name = "Group One",
                    description = "Desc 1",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            whenever(userManagementService.getUserGroupsBatch(any<List<UUID>>(), any()))
                .thenReturn(mapOf(userId1 to listOf(group1)))

            // Act
            val result = userManagementResolver.resolveUserGroupsBatch(listOf(userId1.toString(), invalidId))

            // Assert
            assertThat(result).hasSize(1)
            assertThat(result[userId1.toString()]).hasSize(1)
            assertThat(result).doesNotContainKey(invalidId)
        }
    }

    @Nested
    @DisplayName("resolveUserPermissionsBatch")
    inner class ResolveUserPermissionsBatchTests {
        @Test
        fun `should return permissions for multiple users`() {
            // Arrange
            val userId1 = UUID.randomUUID()
            val userId2 = UUID.randomUUID()
            val permission1 =
                Permission(
                    id = UUID.randomUUID(),
                    name = "transaction:read",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            val permission2 =
                Permission(
                    id = UUID.randomUUID(),
                    name = "transaction:write",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            whenever(authorizationService.getUserPermissionsBatch(any<List<UUID>>(), any()))
                .thenReturn(
                    mapOf(
                        userId1 to listOf(permission1),
                        userId2 to listOf(permission2),
                    ),
                )

            // Act
            val result =
                userManagementResolver.resolveUserPermissionsBatch(
                    listOf(userId1.toString(), userId2.toString()),
                )

            // Assert
            assertThat(result).hasSize(2)
            assertThat(result[userId1.toString()]).hasSize(1)
            assertThat(result[userId1.toString()]!![0].name).isEqualTo("transaction:read")
            assertThat(result[userId2.toString()]).hasSize(1)
            assertThat(result[userId2.toString()]!![0].name).isEqualTo("transaction:write")
            verify(authorizationService).getUserPermissionsBatch(any<List<UUID>>(), any())
        }

        @Test
        fun `should return empty map for empty user list`() {
            // Act
            val result = userManagementResolver.resolveUserPermissionsBatch(emptyList())

            // Assert
            assertThat(result).isEmpty()
        }

        @Test
        fun `should filter out invalid user IDs`() {
            // Arrange
            val userId1 = UUID.randomUUID()
            val invalidId = "not-a-uuid"
            val permission1 =
                Permission(
                    id = UUID.randomUUID(),
                    name = "transaction:read",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            whenever(authorizationService.getUserPermissionsBatch(any<List<UUID>>(), any()))
                .thenReturn(mapOf(userId1 to listOf(permission1)))

            // Act
            val result = userManagementResolver.resolveUserPermissionsBatch(listOf(userId1.toString(), invalidId))

            // Assert
            assertThat(result).hasSize(1)
            assertThat(result[userId1.toString()]).hasSize(1)
            assertThat(result).doesNotContainKey(invalidId)
        }
    }
}
