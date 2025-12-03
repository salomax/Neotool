package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.common.graphql.pagination.CursorEncoder
import io.github.salomax.neotool.common.graphql.pagination.PaginationConstants
import io.github.salomax.neotool.security.repo.GroupMembershipRepository
import io.github.salomax.neotool.security.repo.GroupRepository
import io.github.salomax.neotool.security.repo.RoleAssignmentRepository
import io.github.salomax.neotool.security.repo.RoleRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.repo.UserRepositoryCustom
import io.github.salomax.neotool.security.service.UserManagementService
import io.github.salomax.neotool.security.test.SecurityTestDataBuilders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

@DisplayName("UserManagementService Unit Tests")
class UserManagementServiceTest {
    private lateinit var userRepository: UserRepository
    private lateinit var userSearchRepository: UserRepositoryCustom
    private lateinit var roleAssignmentRepository: RoleAssignmentRepository
    private lateinit var roleRepository: RoleRepository
    private lateinit var groupMembershipRepository: GroupMembershipRepository
    private lateinit var groupRepository: GroupRepository
    private lateinit var userManagementService: UserManagementService

    @BeforeEach
    fun setUp() {
        userRepository = mock()
        userSearchRepository = mock()
        roleAssignmentRepository = mock()
        roleRepository = mock()
        groupMembershipRepository = mock()
        groupRepository = mock()
        userManagementService =
            UserManagementService(
                userRepository,
                userSearchRepository,
                roleAssignmentRepository,
                roleRepository,
                groupMembershipRepository,
                groupRepository,
            )
    }

    @Nested
    @DisplayName("List Users")
    inner class ListUsersTests {
        @Test
        fun `should list users with default page size`() {
            // Arrange
            val user1 =
                SecurityTestDataBuilders.user(
                    id = UUID.randomUUID(),
                    email = "user1@example.com",
                    displayName = "User One",
                )
            val user2 =
                SecurityTestDataBuilders.user(
                    id = UUID.randomUUID(),
                    email = "user2@example.com",
                    displayName = "User Two",
                )
            whenever(userSearchRepository.searchByNameOrEmail(null, PaginationConstants.DEFAULT_PAGE_SIZE + 1, null))
                .thenReturn(listOf(user1, user2))
            whenever(userSearchRepository.countByNameOrEmail(null))
                .thenReturn(2L)

            // Act
            val result = userManagementService.searchUsers(query = null, after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.nodes).hasSize(2)
            assertThat(result.pageInfo.hasNextPage).isFalse()
            verify(userSearchRepository).searchByNameOrEmail(null, PaginationConstants.DEFAULT_PAGE_SIZE + 1, null)
        }

        @Test
        fun `should list users with custom page size`() {
            // Arrange
            val first = 10
            val user1 = SecurityTestDataBuilders.user(id = UUID.randomUUID(), email = "user1@example.com")
            whenever(userSearchRepository.searchByNameOrEmail(null, first + 1, null))
                .thenReturn(listOf(user1))
            whenever(userSearchRepository.countByNameOrEmail(null))
                .thenReturn(1L)

            // Act
            val result = userManagementService.searchUsers(query = null, first = first, after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.nodes).hasSize(1)
            verify(userSearchRepository).searchByNameOrEmail(null, first + 1, null)
        }

        @Test
        fun `should enforce max page size`() {
            // Arrange
            val first = 200 // Exceeds MAX_PAGE_SIZE
            whenever(userSearchRepository.searchByNameOrEmail(null, PaginationConstants.MAX_PAGE_SIZE + 1, null))
                .thenReturn(emptyList())
            whenever(userSearchRepository.countByNameOrEmail(null))
                .thenReturn(0L)

            // Act
            val result = userManagementService.searchUsers(query = null, first = first, after = null)

            // Assert
            assertThat(result).isNotNull()
            verify(userSearchRepository).searchByNameOrEmail(null, PaginationConstants.MAX_PAGE_SIZE + 1, null)
        }

        @Test
        fun `should list users with cursor pagination`() {
            // Arrange
            val afterId = UUID.randomUUID()
            val afterCursor = CursorEncoder.encodeCursor(afterId as UUID)
            val user1 = SecurityTestDataBuilders.user(id = UUID.randomUUID(), email = "user1@example.com")
            whenever(userSearchRepository.searchByNameOrEmail(null, PaginationConstants.DEFAULT_PAGE_SIZE + 1, afterId))
                .thenReturn(listOf(user1))
            whenever(userSearchRepository.countByNameOrEmail(null))
                .thenReturn(1L)

            // Act
            val result = userManagementService.searchUsers(query = null, after = afterCursor)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.nodes).hasSize(1)
            verify(userSearchRepository).searchByNameOrEmail(null, PaginationConstants.DEFAULT_PAGE_SIZE + 1, afterId)
        }

        @Test
        fun `should indicate has more when results exceed page size`() {
            // Arrange
            val users =
                (1..21).map {
                    SecurityTestDataBuilders.user(
                        id = UUID.randomUUID(),
                        email = "user$it@example.com",
                    )
                }
            whenever(userSearchRepository.searchByNameOrEmail(null, PaginationConstants.DEFAULT_PAGE_SIZE + 1, null))
                .thenReturn(users)
            whenever(userSearchRepository.countByNameOrEmail(null))
                .thenReturn(21L)

            // Act
            val result = userManagementService.searchUsers(query = null, after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.nodes).hasSize(PaginationConstants.DEFAULT_PAGE_SIZE)
            assertThat(result.pageInfo.hasNextPage).isTrue()
        }

        @Test
        fun `should return empty connection when no users exist`() {
            // Arrange
            whenever(userSearchRepository.searchByNameOrEmail(null, PaginationConstants.DEFAULT_PAGE_SIZE + 1, null))
                .thenReturn(emptyList())
            whenever(userSearchRepository.countByNameOrEmail(null))
                .thenReturn(0L)

            // Act
            val result = userManagementService.searchUsers(query = null, after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.nodes).isEmpty()
            assertThat(result.edges).isEmpty()
            assertThat(result.pageInfo.hasNextPage).isFalse()
        }

        @Test
        fun `should throw exception for invalid cursor`() {
            // Arrange
            val invalidCursor = "invalid-cursor"

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                userManagementService.searchUsers(query = null, after = invalidCursor)
            }
            verify(userSearchRepository, never()).searchByNameOrEmail(any(), any(), any())
        }
    }

    @Nested
    @DisplayName("Search Users")
    inner class SearchUsersTests {
        @Test
        fun `should search users by name`() {
            // Arrange
            val query = "john"
            val user1 =
                SecurityTestDataBuilders.user(
                    id = UUID.randomUUID(),
                    email = "john@example.com",
                    displayName = "John Doe",
                )
            whenever(
                userSearchRepository.searchByNameOrEmail(
                    query,
                    PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                    null,
                ),
            )
                .thenReturn(listOf(user1))
            whenever(userSearchRepository.countByNameOrEmail(query))
                .thenReturn(1L)

            // Act
            val result = userManagementService.searchUsers(query, after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.nodes).hasSize(1)
            assertThat(result.nodes.first().email).isEqualTo("john@example.com")
            assertThat(result.totalCount).isEqualTo(1L)
            verify(userSearchRepository).searchByNameOrEmail(query, PaginationConstants.DEFAULT_PAGE_SIZE + 1, null)
            verify(userSearchRepository).countByNameOrEmail(query)
        }

        @Test
        fun `should search users by email`() {
            // Arrange
            val query = "example.com"
            val user1 = SecurityTestDataBuilders.user(id = UUID.randomUUID(), email = "user@example.com")
            whenever(userSearchRepository.searchByNameOrEmail(query, PaginationConstants.DEFAULT_PAGE_SIZE + 1, null))
                .thenReturn(listOf(user1))
            whenever(userSearchRepository.countByNameOrEmail(query))
                .thenReturn(1L)

            // Act
            val result = userManagementService.searchUsers(query, after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.nodes).hasSize(1)
            assertThat(result.totalCount).isEqualTo(1L)
            verify(userSearchRepository).searchByNameOrEmail(query, PaginationConstants.DEFAULT_PAGE_SIZE + 1, null)
            verify(userSearchRepository).countByNameOrEmail(query)
        }

        @Test
        fun `should search users with pagination`() {
            // Arrange
            val query = "test"
            val afterId = UUID.randomUUID()
            val afterCursor = CursorEncoder.encodeCursor(afterId as UUID)
            val user1 = SecurityTestDataBuilders.user(id = UUID.randomUUID(), email = "test@example.com")
            whenever(userSearchRepository.searchByNameOrEmail(query, PaginationConstants.DEFAULT_PAGE_SIZE + 1, afterId))
                .thenReturn(listOf(user1))
            whenever(userSearchRepository.countByNameOrEmail(query))
                .thenReturn(5L)

            // Act
            val result = userManagementService.searchUsers(query, after = afterCursor)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.nodes).hasSize(1)
            assertThat(result.totalCount).isEqualTo(5L)
            verify(userSearchRepository).searchByNameOrEmail(query, PaginationConstants.DEFAULT_PAGE_SIZE + 1, afterId)
            verify(userSearchRepository).countByNameOrEmail(query)
        }

        @Test
        fun `should return empty connection when no users match search`() {
            // Arrange
            val query = "nonexistent"
            whenever(userSearchRepository.searchByNameOrEmail(query, PaginationConstants.DEFAULT_PAGE_SIZE + 1, null))
                .thenReturn(emptyList())
            whenever(userSearchRepository.countByNameOrEmail(query))
                .thenReturn(0L)

            // Act
            val result = userManagementService.searchUsers(query, after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.nodes).isEmpty()
            assertThat(result.pageInfo.hasNextPage).isFalse()
            assertThat(result.totalCount).isEqualTo(0L)
            verify(userSearchRepository).countByNameOrEmail(query)
        }

        @Test
        fun `should throw exception for invalid cursor in search`() {
            // Arrange
            val query = "test"
            val invalidCursor = "invalid-cursor"

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                userManagementService.searchUsers(query, after = invalidCursor)
            }
            verify(userSearchRepository, never()).searchByNameOrEmail(any(), any(), any())
        }
    }

    @Nested
    @DisplayName("Enable User")
    inner class EnableUserTests {
        @Test
        fun `should enable user successfully`() {
            // Arrange
            val userId = UUID.randomUUID()
            val userEntity =
                SecurityTestDataBuilders.user(
                    id = userId,
                    email = "user@example.com",
                    displayName = "Test User",
                )
            userEntity.enabled = false
            whenever(userRepository.findById(userId)).thenReturn(Optional.of(userEntity))
            whenever(userRepository.update(any())).thenAnswer {
                it.arguments[0] as io.github.salomax.neotool.security.model.UserEntity
            }

            // Act
            val result = userManagementService.enableUser(userId)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.id).isEqualTo(userId)
            assertThat(userEntity.enabled).isTrue()
            verify(userRepository).findById(userId)
            verify(userRepository).update(any())
        }

        @Test
        fun `should throw exception when user not found`() {
            // Arrange
            val userId = UUID.randomUUID()
            whenever(userRepository.findById(userId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                userManagementService.enableUser(userId)
            }
                .also { exception ->
                    assertThat(exception.message).contains("User not found")
                }
            verify(userRepository).findById(userId)
            verify(userRepository, never()).update(any())
        }

        @Test
        fun `should throw exception when user already enabled`() {
            // Arrange
            val userId = UUID.randomUUID()
            val userEntity =
                SecurityTestDataBuilders.user(
                    id = userId,
                    email = "user@example.com",
                )
            userEntity.enabled = true
            whenever(userRepository.findById(userId)).thenReturn(Optional.of(userEntity))

            // Act & Assert
            assertThrows<IllegalStateException> {
                userManagementService.enableUser(userId)
            }
                .also { exception ->
                    assertThat(exception.message).contains("already enabled")
                }
            verify(userRepository).findById(userId)
            verify(userRepository, never()).update(any())
        }
    }

    @Nested
    @DisplayName("Disable User")
    inner class DisableUserTests {
        @Test
        fun `should disable user successfully`() {
            // Arrange
            val userId = UUID.randomUUID()
            val userEntity =
                SecurityTestDataBuilders.user(
                    id = userId,
                    email = "user@example.com",
                    displayName = "Test User",
                )
            userEntity.enabled = true
            whenever(userRepository.findById(userId)).thenReturn(Optional.of(userEntity))
            whenever(userRepository.update(any())).thenAnswer {
                it.arguments[0] as io.github.salomax.neotool.security.model.UserEntity
            }

            // Act
            val result = userManagementService.disableUser(userId)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.id).isEqualTo(userId)
            assertThat(userEntity.enabled).isFalse()
            verify(userRepository).findById(userId)
            verify(userRepository).update(any())
        }

        @Test
        fun `should throw exception when user not found`() {
            // Arrange
            val userId = UUID.randomUUID()
            whenever(userRepository.findById(userId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                userManagementService.disableUser(userId)
            }
                .also { exception ->
                    assertThat(exception.message).contains("User not found")
                }
            verify(userRepository).findById(userId)
            verify(userRepository, never()).update(any())
        }

        @Test
        fun `should throw exception when user already disabled`() {
            // Arrange
            val userId = UUID.randomUUID()
            val userEntity = SecurityTestDataBuilders.user(id = userId, email = "user@example.com")
            userEntity.enabled = false
            whenever(userRepository.findById(userId)).thenReturn(Optional.of(userEntity))

            // Act & Assert
            assertThrows<IllegalStateException> {
                userManagementService.disableUser(userId)
            }
                .also { exception ->
                    assertThat(exception.message).contains("already disabled")
                }
            verify(userRepository).findById(userId)
            verify(userRepository, never()).update(any())
        }
    }
}
