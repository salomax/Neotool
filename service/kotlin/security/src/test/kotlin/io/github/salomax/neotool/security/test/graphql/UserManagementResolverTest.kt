package io.github.salomax.neotool.security.test.graphql

import io.github.salomax.neotool.common.graphql.pagination.ConnectionBuilder
import io.github.salomax.neotool.common.graphql.pagination.PaginationConstants
import io.github.salomax.neotool.security.domain.rbac.Group
import io.github.salomax.neotool.security.domain.rbac.Permission
import io.github.salomax.neotool.security.domain.rbac.Role
import io.github.salomax.neotool.security.domain.rbac.User
import io.github.salomax.neotool.security.graphql.dto.UserConnectionDTO
import io.github.salomax.neotool.security.graphql.dto.UserDTO
import io.github.salomax.neotool.security.graphql.mapper.UserManagementMapper
import io.github.salomax.neotool.security.graphql.resolver.UserManagementResolver
import io.github.salomax.neotool.security.service.authorization.AuthorizationService
import io.github.salomax.neotool.security.service.management.UserManagementService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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

@DisplayName("UserManagementResolver Tests")
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

    @Nested
    @DisplayName("user() - Single User Query")
    inner class SingleUserQueryTests {
        @Test
        fun `should return user when found`() {
            // Arrange
            val userId = UUID.randomUUID()
            val user =
                User(
                    id = userId,
                    email = "user@example.com",
                    displayName = "Test User",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            val userDTO = UserDTO(id = userId.toString(), email = "user@example.com", displayName = "Test User")

            whenever(mapper.toUserId(userId.toString())).thenReturn(userId)
            whenever(userManagementService.getUserById(userId)).thenReturn(user)
            whenever(mapper.toUserDTO(user)).thenReturn(userDTO)

            // Act
            val result = userManagementResolver.user(userId.toString())

            // Assert
            assertThat(result).isNotNull
            assertThat(result?.id).isEqualTo(userId.toString())
            assertThat(result?.email).isEqualTo("user@example.com")
            verify(userManagementService).getUserById(userId)
        }

        @Test
        fun `should return null when user not found`() {
            // Arrange
            val userId = UUID.randomUUID()

            whenever(mapper.toUserId(userId.toString())).thenReturn(userId)
            whenever(userManagementService.getUserById(userId)).thenReturn(null)

            // Act
            val result = userManagementResolver.user(userId.toString())

            // Assert
            assertThat(result).isNull()
            verify(userManagementService).getUserById(userId)
        }

        @Test
        fun `should throw IllegalArgumentException for invalid user ID`() {
            // Arrange
            val invalidId = "not-a-uuid"

            whenever(mapper.toUserId(invalidId)).thenThrow(IllegalArgumentException("Invalid UUID format"))

            // Act & Assert
            assertThatThrownBy { userManagementResolver.user(invalidId) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Invalid user ID format")
        }
    }

    @Nested
    @DisplayName("users() - List/Search Query")
    inner class UsersQueryTests {
        @Test
        fun `should validate first parameter minimum value`() {
            // Act & Assert
            assertThatThrownBy { userManagementResolver.users(first = 0, after = null, query = null, orderBy = null) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Parameter 'first' must be at least 1")
        }

        @Test
        fun `should validate first parameter maximum value`() {
            // Act & Assert
            assertThatThrownBy {
                userManagementResolver.users(
                    first = PaginationConstants.MAX_PAGE_SIZE + 1,
                    after = null,
                    query = null,
                    orderBy = null,
                )
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Parameter 'first' must be at most")
        }

        @Test
        fun `should use default page size when first is null`() {
            // Arrange
            val connection =
                ConnectionBuilder.buildConnection<User>(
                    items = emptyList(),
                    hasMore = false,
                    encodeCursor = { it.id?.toString() ?: "" },
                    totalCount = 0L,
                )
            val pageInfo =
                io.github.salomax.neotool.security.graphql.dto.PageInfoDTO(
                    hasNextPage = false,
                    hasPreviousPage = false,
                )
            val connectionDTO = UserConnectionDTO(edges = emptyList(), pageInfo = pageInfo, totalCount = 0)

            whenever(mapper.toUserOrderByList(null)).thenReturn(emptyList())
            whenever(
                userManagementService.searchUsers(
                    null,
                    PaginationConstants.DEFAULT_PAGE_SIZE,
                    null,
                    emptyList(),
                ),
            )
                .thenReturn(connection)
            whenever(mapper.toUserConnectionDTO(connection)).thenReturn(connectionDTO)

            // Act
            val result = userManagementResolver.users(first = null, after = null, query = null, orderBy = null)

            // Assert
            assertThat(result).isNotNull
            verify(userManagementService).searchUsers(null, PaginationConstants.DEFAULT_PAGE_SIZE, null, emptyList())
        }

        @Test
        fun `should validate orderBy parameter`() {
            // Arrange
            val invalidOrderBy = listOf(mapOf("field" to "INVALID_FIELD"))

            whenever(mapper.toUserOrderByList(invalidOrderBy))
                .thenThrow(IllegalArgumentException("Invalid field: INVALID_FIELD"))

            // Act & Assert
            assertThatThrownBy {
                userManagementResolver.users(
                    first = 10,
                    after = null,
                    query = null,
                    orderBy = invalidOrderBy,
                )
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Invalid orderBy parameter")
        }
    }

    @Nested
    @DisplayName("enableUser() - Enable User Mutation")
    inner class EnableUserTests {
        @Test
        fun `should enable user and return user DTO`() {
            // Arrange
            val userId = UUID.randomUUID()
            val user =
                User(
                    id = userId,
                    email = "user@example.com",
                    displayName = "Test User",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            val userDTO = UserDTO(id = userId.toString(), email = "user@example.com", displayName = "Test User")

            whenever(mapper.toUserId(userId.toString())).thenReturn(userId)
            whenever(userManagementService.enableUser(userId)).thenReturn(user)
            whenever(mapper.toUserDTO(user)).thenReturn(userDTO)

            // Act
            val result = userManagementResolver.enableUser(userId.toString())

            // Assert
            assertThat(result).isNotNull
            assertThat(result.id).isEqualTo(userId.toString())
            assertThat(result.email).isEqualTo("user@example.com")
            verify(userManagementService).enableUser(userId)
        }

        @Test
        fun `should throw IllegalArgumentException for invalid user ID`() {
            // Arrange
            val invalidId = "not-a-uuid"

            whenever(mapper.toUserId(invalidId)).thenThrow(IllegalArgumentException("Invalid UUID format"))

            // Act & Assert
            assertThatThrownBy { userManagementResolver.enableUser(invalidId) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    @DisplayName("disableUser() - Disable User Mutation")
    inner class DisableUserTests {
        @Test
        fun `should disable user and return user DTO`() {
            // Arrange
            val userId = UUID.randomUUID()
            val user =
                User(
                    id = userId,
                    email = "user@example.com",
                    displayName = "Test User",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            val userDTO = UserDTO(id = userId.toString(), email = "user@example.com", displayName = "Test User")

            whenever(mapper.toUserId(userId.toString())).thenReturn(userId)
            whenever(userManagementService.disableUser(userId)).thenReturn(user)
            whenever(mapper.toUserDTO(user)).thenReturn(userDTO)

            // Act
            val result = userManagementResolver.disableUser(userId.toString())

            // Assert
            assertThat(result).isNotNull
            assertThat(result.id).isEqualTo(userId.toString())
            assertThat(result.email).isEqualTo("user@example.com")
            verify(userManagementService).disableUser(userId)
        }

        @Test
        fun `should throw IllegalArgumentException for invalid user ID`() {
            // Arrange
            val invalidId = "not-a-uuid"

            whenever(mapper.toUserId(invalidId)).thenThrow(IllegalArgumentException("Invalid UUID format"))

            // Act & Assert
            assertThatThrownBy { userManagementResolver.disableUser(invalidId) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    @DisplayName("resolveUserRoles() - Single User Roles")
    inner class ResolveUserRolesTests {
        @Test
        fun `should return roles for user`() {
            // Arrange
            val userId = UUID.randomUUID()
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
            val roles = listOf(role1, role2)

            whenever(authorizationService.getUserRoles(any(), any())).thenReturn(roles)

            // Act
            val result = userManagementResolver.resolveUserRoles(userId.toString())

            // Assert
            assertThat(result).hasSize(2)
            assertThat(result[0].name).isEqualTo("admin")
            assertThat(result[1].name).isEqualTo("editor")
            verify(authorizationService).getUserRoles(any(), any())
        }

        @Test
        fun `should throw IllegalArgumentException for invalid user ID`() {
            // Arrange
            val invalidId = "not-a-uuid"

            // Act & Assert
            assertThatThrownBy { userManagementResolver.resolveUserRoles(invalidId) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Invalid user ID format")
        }
    }

    @Nested
    @DisplayName("resolveUserPermissions() - Single User Permissions")
    inner class ResolveUserPermissionsTests {
        @Test
        fun `should return permissions for user`() {
            // Arrange
            val userId = UUID.randomUUID()
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
            val permissions = listOf(permission1, permission2)

            whenever(authorizationService.getUserPermissions(any(), any())).thenReturn(permissions)

            // Act
            val result = userManagementResolver.resolveUserPermissions(userId.toString())

            // Assert
            assertThat(result).hasSize(2)
            assertThat(result[0].name).isEqualTo("transaction:read")
            assertThat(result[1].name).isEqualTo("transaction:write")
            verify(authorizationService).getUserPermissions(any(), any())
        }

        @Test
        fun `should throw IllegalArgumentException for invalid user ID`() {
            // Arrange
            val invalidId = "not-a-uuid"

            // Act & Assert
            assertThatThrownBy { userManagementResolver.resolveUserPermissions(invalidId) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Invalid user ID format")
        }
    }
}
