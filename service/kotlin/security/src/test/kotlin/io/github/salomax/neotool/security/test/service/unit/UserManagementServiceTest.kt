package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.common.graphql.pagination.CompositeCursor
import io.github.salomax.neotool.common.graphql.pagination.CursorEncoder
import io.github.salomax.neotool.common.graphql.pagination.OrderDirection
import io.github.salomax.neotool.common.graphql.pagination.PaginationConstants
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.security.domain.UserManagement
import io.github.salomax.neotool.security.model.PrincipalEntity
import io.github.salomax.neotool.security.model.UserEntity
import io.github.salomax.neotool.security.model.UserOrderBy
import io.github.salomax.neotool.security.model.UserOrderField
import io.github.salomax.neotool.security.repo.GroupMembershipRepository
import io.github.salomax.neotool.security.repo.GroupRepository
import io.github.salomax.neotool.security.repo.PrincipalRepository
import io.github.salomax.neotool.security.repo.RoleRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.repo.UserRepositoryCustom
import io.github.salomax.neotool.security.service.management.UserManagementService
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
    private lateinit var roleRepository: RoleRepository
    private lateinit var groupMembershipRepository: GroupMembershipRepository
    private lateinit var groupRepository: GroupRepository
    private lateinit var principalRepository: PrincipalRepository
    private lateinit var userManagementService: UserManagementService

    @BeforeEach
    fun setUp() {
        userRepository = mock()
        userSearchRepository = mock()
        roleRepository = mock()
        groupMembershipRepository = mock()
        groupRepository = mock()
        principalRepository = mock()
        userManagementService =
            UserManagementService(
                userRepository,
                userSearchRepository,
                groupMembershipRepository,
                groupRepository,
                principalRepository,
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
            val defaultOrderBy =
                listOf(
                    UserOrderBy(UserOrderField.DISPLAY_NAME, OrderDirection.ASC),
                    UserOrderBy(UserOrderField.ID, OrderDirection.ASC),
                )
            whenever(
                userSearchRepository.searchByNameOrEmail(
                    null,
                    PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                    null,
                    defaultOrderBy,
                ),
            ).thenReturn(listOf(user1, user2))
            whenever(userSearchRepository.countByNameOrEmail(null))
                .thenReturn(2L)

            // Act
            val result = userManagementService.searchUsers(query = null, after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(2)
            assertThat(result.pageInfo.hasNextPage).isFalse()
            verify(
                userSearchRepository,
            ).searchByNameOrEmail(
                null,
                PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                null,
                defaultOrderBy,
            )
        }

        @Test
        fun `should list users with custom page size`() {
            // Arrange
            val first = 10
            val user1 = SecurityTestDataBuilders.user(id = UUID.randomUUID(), email = "user1@example.com")
            val defaultOrderBy =
                listOf(
                    UserOrderBy(UserOrderField.DISPLAY_NAME, OrderDirection.ASC),
                    UserOrderBy(UserOrderField.ID, OrderDirection.ASC),
                )
            whenever(userSearchRepository.searchByNameOrEmail(null, first + 1, null, defaultOrderBy))
                .thenReturn(listOf(user1))
            whenever(userSearchRepository.countByNameOrEmail(null))
                .thenReturn(1L)

            // Act
            val result = userManagementService.searchUsers(query = null, first = first, after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(1)
            verify(userSearchRepository).searchByNameOrEmail(null, first + 1, null, defaultOrderBy)
        }

        @Test
        fun `should enforce max page size`() {
            // Arrange
            val first = 200 // Exceeds MAX_PAGE_SIZE
            val defaultOrderBy =
                listOf(
                    UserOrderBy(UserOrderField.DISPLAY_NAME, OrderDirection.ASC),
                    UserOrderBy(UserOrderField.ID, OrderDirection.ASC),
                )
            whenever(
                userSearchRepository.searchByNameOrEmail(
                    null,
                    PaginationConstants.MAX_PAGE_SIZE + 1,
                    null,
                    defaultOrderBy,
                ),
            ).thenReturn(emptyList())
            whenever(userSearchRepository.countByNameOrEmail(null))
                .thenReturn(0L)

            // Act
            val result = userManagementService.searchUsers(query = null, first = first, after = null)

            // Assert
            assertThat(result).isNotNull()
            verify(
                userSearchRepository,
            ).searchByNameOrEmail(null, PaginationConstants.MAX_PAGE_SIZE + 1, null, defaultOrderBy)
        }

        @Test
        fun `should list users with cursor pagination using legacy UUID cursor`() {
            // Arrange
            val afterId = UUID.randomUUID()
            val afterCursor = CursorEncoder.encodeCursor(afterId)
            val user1 = SecurityTestDataBuilders.user(id = UUID.randomUUID(), email = "user1@example.com")
            val defaultOrderBy =
                listOf(
                    UserOrderBy(UserOrderField.DISPLAY_NAME, OrderDirection.ASC),
                    UserOrderBy(UserOrderField.ID, OrderDirection.ASC),
                )
            // Service converts legacy cursor to composite cursor with empty fieldValues
            val compositeCursor = CompositeCursor(emptyMap(), afterId.toString())
            whenever(
                userSearchRepository.searchByNameOrEmail(
                    null,
                    PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                    compositeCursor,
                    defaultOrderBy,
                ),
            ).thenReturn(listOf(user1))
            whenever(userSearchRepository.countByNameOrEmail(null))
                .thenReturn(1L)

            // Act
            val result = userManagementService.searchUsers(query = null, after = afterCursor)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(1)
            verify(
                userSearchRepository,
            ).searchByNameOrEmail(null, PaginationConstants.DEFAULT_PAGE_SIZE + 1, compositeCursor, defaultOrderBy)
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
            val defaultOrderBy =
                listOf(
                    UserOrderBy(UserOrderField.DISPLAY_NAME, OrderDirection.ASC),
                    UserOrderBy(UserOrderField.ID, OrderDirection.ASC),
                )
            whenever(
                userSearchRepository.searchByNameOrEmail(
                    null,
                    PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                    null,
                    defaultOrderBy,
                ),
            ).thenReturn(users)
            whenever(userSearchRepository.countByNameOrEmail(null))
                .thenReturn(21L)

            // Act
            val result = userManagementService.searchUsers(query = null, after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(PaginationConstants.DEFAULT_PAGE_SIZE)
            assertThat(result.pageInfo.hasNextPage).isTrue()
        }

        @Test
        fun `should return empty connection when no users exist`() {
            // Arrange
            val defaultOrderBy =
                listOf(
                    UserOrderBy(UserOrderField.DISPLAY_NAME, OrderDirection.ASC),
                    UserOrderBy(UserOrderField.ID, OrderDirection.ASC),
                )
            whenever(
                userSearchRepository.searchByNameOrEmail(
                    null,
                    PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                    null,
                    defaultOrderBy,
                ),
            ).thenReturn(emptyList())
            whenever(userSearchRepository.countByNameOrEmail(null))
                .thenReturn(0L)

            // Act
            val result = userManagementService.searchUsers(query = null, after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).isEmpty()
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
            verify(userSearchRepository, never()).searchByNameOrEmail(any(), any(), any(), any())
        }
    }

    @Nested
    @DisplayName("Sorting with orderBy")
    inner class SortingTests {
        @Test
        fun `should use default sort when orderBy is null`() {
            // Arrange
            val user1 =
                SecurityTestDataBuilders.user(
                    id = UUID.randomUUID(),
                    email = "user1@example.com",
                    displayName = "Alice",
                )
            val user2 =
                SecurityTestDataBuilders.user(
                    id = UUID.randomUUID(),
                    email = "user2@example.com",
                    displayName = "Bob",
                )
            val defaultOrderBy =
                listOf(
                    UserOrderBy(UserOrderField.DISPLAY_NAME, OrderDirection.ASC),
                    UserOrderBy(UserOrderField.ID, OrderDirection.ASC),
                )
            whenever(
                userSearchRepository.searchByNameOrEmail(
                    null,
                    PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                    null,
                    defaultOrderBy,
                ),
            ).thenReturn(listOf(user1, user2))
            whenever(userSearchRepository.countByNameOrEmail(null))
                .thenReturn(2L)

            // Act
            val result = userManagementService.searchUsers(query = null, after = null, orderBy = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(2)
            verify(
                userSearchRepository,
            ).searchByNameOrEmail(
                null,
                PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                null,
                defaultOrderBy,
            )
        }

        @Test
        fun `should fallback to ID ASC when orderBy is empty array`() {
            // Arrange
            val user1 = SecurityTestDataBuilders.user(id = UUID.randomUUID(), email = "user1@example.com")
            val idOnlyOrderBy = listOf(UserOrderBy(UserOrderField.ID, OrderDirection.ASC))
            whenever(
                userSearchRepository.searchByNameOrEmail(
                    null,
                    PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                    null,
                    idOnlyOrderBy,
                ),
            ).thenReturn(listOf(user1))
            whenever(userSearchRepository.countByNameOrEmail(null))
                .thenReturn(1L)

            // Act
            val result =
                userManagementService.searchUsers(
                    query = null,
                    after = null,
                    orderBy = emptyList(),
                )

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(1)
            verify(
                userSearchRepository,
            ).searchByNameOrEmail(null, PaginationConstants.DEFAULT_PAGE_SIZE + 1, null, idOnlyOrderBy)
        }

        @Test
        fun `should sort by EMAIL ASC when specified`() {
            // Arrange
            val user1 =
                SecurityTestDataBuilders.user(
                    id = UUID.randomUUID(),
                    email = "alice@example.com",
                    displayName = "Alice",
                )
            val user2 =
                SecurityTestDataBuilders.user(
                    id = UUID.randomUUID(),
                    email = "bob@example.com",
                    displayName = "Bob",
                )
            val orderBy =
                listOf(
                    UserOrderBy(UserOrderField.EMAIL, OrderDirection.ASC),
                    UserOrderBy(UserOrderField.ID, OrderDirection.ASC),
                )
            whenever(
                userSearchRepository.searchByNameOrEmail(
                    null,
                    PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                    null,
                    orderBy,
                ),
            ).thenReturn(listOf(user1, user2))
            whenever(userSearchRepository.countByNameOrEmail(null))
                .thenReturn(2L)

            // Act
            val result =
                userManagementService.searchUsers(
                    query = null,
                    after = null,
                    orderBy = listOf(UserOrderBy(UserOrderField.EMAIL, OrderDirection.ASC)),
                )

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(2)
            verify(
                userSearchRepository,
            ).searchByNameOrEmail(
                null,
                PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                null,
                orderBy,
            )
        }

        @Test
        fun `should sort by EMAIL DESC when specified`() {
            // Arrange
            val user1 =
                SecurityTestDataBuilders.user(
                    id = UUID.randomUUID(),
                    email = "bob@example.com",
                    displayName = "Bob",
                )
            val user2 =
                SecurityTestDataBuilders.user(
                    id = UUID.randomUUID(),
                    email = "alice@example.com",
                    displayName = "Alice",
                )
            val orderBy =
                listOf(
                    UserOrderBy(UserOrderField.EMAIL, OrderDirection.DESC),
                    UserOrderBy(UserOrderField.ID, OrderDirection.ASC),
                )
            whenever(
                userSearchRepository.searchByNameOrEmail(
                    null,
                    PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                    null,
                    orderBy,
                ),
            ).thenReturn(listOf(user1, user2))
            whenever(userSearchRepository.countByNameOrEmail(null))
                .thenReturn(2L)

            // Act
            val result =
                userManagementService.searchUsers(
                    query = null,
                    after = null,
                    orderBy = listOf(UserOrderBy(UserOrderField.EMAIL, OrderDirection.DESC)),
                )

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(2)
            verify(
                userSearchRepository,
            ).searchByNameOrEmail(
                null,
                PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                null,
                orderBy,
            )
        }

        @Test
        fun `should use composite cursor for pagination with orderBy`() {
            // Arrange
            val userId = UUID.randomUUID()
            val fieldValues = mapOf("displayName" to "Alice", "email" to "alice@example.com")
            val compositeCursor = CompositeCursor(fieldValues, userId.toString())
            val cursor = CursorEncoder.encodeCompositeCursor(fieldValues, userId)
            val user1 =
                SecurityTestDataBuilders.user(
                    id = UUID.randomUUID(),
                    email = "bob@example.com",
                    displayName = "Bob",
                )
            val orderBy =
                listOf(
                    UserOrderBy(UserOrderField.DISPLAY_NAME, OrderDirection.ASC),
                    UserOrderBy(UserOrderField.ID, OrderDirection.ASC),
                )
            whenever(
                userSearchRepository.searchByNameOrEmail(
                    null,
                    PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                    compositeCursor,
                    orderBy,
                ),
            ).thenReturn(listOf(user1))
            whenever(userSearchRepository.countByNameOrEmail(null))
                .thenReturn(1L)

            // Act
            val result =
                userManagementService.searchUsers(
                    query = null,
                    after = cursor,
                    orderBy =
                        listOf(
                            UserOrderBy(UserOrderField.DISPLAY_NAME, OrderDirection.ASC),
                        ),
                )

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(1)
            verify(
                userSearchRepository,
            ).searchByNameOrEmail(
                null,
                PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                compositeCursor,
                orderBy,
            )
        }

        @Test
        fun `should encode composite cursor with field values in result`() {
            // Arrange
            val user1 =
                SecurityTestDataBuilders.user(
                    id = UUID.randomUUID(),
                    email = "alice@example.com",
                    displayName = "Alice",
                )
            val orderBy =
                listOf(
                    UserOrderBy(UserOrderField.EMAIL, OrderDirection.ASC),
                    UserOrderBy(UserOrderField.ID, OrderDirection.ASC),
                )
            whenever(
                userSearchRepository.searchByNameOrEmail(
                    null,
                    PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                    null,
                    orderBy,
                ),
            ).thenReturn(listOf(user1))
            whenever(userSearchRepository.countByNameOrEmail(null))
                .thenReturn(1L)

            // Act
            val result =
                userManagementService.searchUsers(
                    query = null,
                    after = null,
                    orderBy = listOf(UserOrderBy(UserOrderField.EMAIL, OrderDirection.ASC)),
                )

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges).hasSize(1)
            val cursor = result.edges.first().cursor
            assertThat(cursor).isNotNull()
            // Verify cursor can be decoded
            val decoded = CursorEncoder.decodeCompositeCursorToUuid(cursor)
            assertThat(decoded.id).isEqualTo(user1.id.toString())
            assertThat(decoded.fieldValues).containsKey("email")
        }

        @Test
        fun `should remove ID from orderBy and always append it as final sort`() {
            // Arrange
            val user1 = SecurityTestDataBuilders.user(id = UUID.randomUUID(), email = "user1@example.com")
            // Even if ID is in orderBy, it should be moved to the end
            val orderBy =
                listOf(
                    UserOrderBy(UserOrderField.EMAIL, OrderDirection.ASC),
                    UserOrderBy(UserOrderField.ID, OrderDirection.ASC),
                )
            whenever(
                userSearchRepository.searchByNameOrEmail(
                    null,
                    PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                    null,
                    orderBy,
                ),
            ).thenReturn(listOf(user1))
            whenever(userSearchRepository.countByNameOrEmail(null))
                .thenReturn(1L)

            // Act
            val result =
                userManagementService.searchUsers(
                    query = null,
                    after = null,
                    orderBy =
                        listOf(
                            UserOrderBy(UserOrderField.EMAIL, OrderDirection.ASC),
                            // Should be overridden to ASC
                            UserOrderBy(UserOrderField.ID, OrderDirection.DESC),
                        ),
                )

            // Assert
            assertThat(result).isNotNull()
            // Verify ID is always last with ASC
            verify(
                userSearchRepository,
            ).searchByNameOrEmail(
                null,
                PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                null,
                orderBy,
            )
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
            val defaultOrderBy =
                listOf(
                    UserOrderBy(UserOrderField.DISPLAY_NAME, OrderDirection.ASC),
                    UserOrderBy(UserOrderField.ID, OrderDirection.ASC),
                )
            whenever(
                userSearchRepository.searchByNameOrEmail(
                    query,
                    PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                    null,
                    defaultOrderBy,
                ),
            ).thenReturn(listOf(user1))
            whenever(userSearchRepository.countByNameOrEmail(query))
                .thenReturn(1L)

            // Act
            val result = userManagementService.searchUsers(query, after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(1)
            assertThat(
                result.edges
                    .first()
                    .node.email,
            ).isEqualTo("john@example.com")
            assertThat(result.totalCount).isEqualTo(1L)
            verify(
                userSearchRepository,
            ).searchByNameOrEmail(query, PaginationConstants.DEFAULT_PAGE_SIZE + 1, null, defaultOrderBy)
            verify(userSearchRepository).countByNameOrEmail(query)
        }

        @Test
        fun `should search users by email`() {
            // Arrange
            val query = "example.com"
            val user1 = SecurityTestDataBuilders.user(id = UUID.randomUUID(), email = "user@example.com")
            val defaultOrderBy =
                listOf(
                    UserOrderBy(UserOrderField.DISPLAY_NAME, OrderDirection.ASC),
                    UserOrderBy(UserOrderField.ID, OrderDirection.ASC),
                )
            whenever(
                userSearchRepository.searchByNameOrEmail(
                    query,
                    PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                    null,
                    defaultOrderBy,
                ),
            ).thenReturn(listOf(user1))
            whenever(userSearchRepository.countByNameOrEmail(query))
                .thenReturn(1L)

            // Act
            val result = userManagementService.searchUsers(query, after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(1)
            assertThat(result.totalCount).isEqualTo(1L)
            verify(
                userSearchRepository,
            ).searchByNameOrEmail(query, PaginationConstants.DEFAULT_PAGE_SIZE + 1, null, defaultOrderBy)
            verify(userSearchRepository).countByNameOrEmail(query)
        }

        @Test
        fun `should search users with pagination`() {
            // Arrange
            val query = "test"
            val afterId = UUID.randomUUID()
            val afterCursor = CursorEncoder.encodeCursor(afterId)
            val user1 = SecurityTestDataBuilders.user(id = UUID.randomUUID(), email = "test@example.com")
            val defaultOrderBy =
                listOf(
                    UserOrderBy(UserOrderField.DISPLAY_NAME, OrderDirection.ASC),
                    UserOrderBy(UserOrderField.ID, OrderDirection.ASC),
                )
            val compositeCursor = CompositeCursor(emptyMap(), afterId.toString())
            whenever(
                userSearchRepository.searchByNameOrEmail(
                    query,
                    PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                    compositeCursor,
                    defaultOrderBy,
                ),
            ).thenReturn(listOf(user1))
            whenever(userSearchRepository.countByNameOrEmail(query))
                .thenReturn(5L)

            // Act
            val result = userManagementService.searchUsers(query, after = afterCursor)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(1)
            assertThat(result.totalCount).isEqualTo(5L)
            verify(
                userSearchRepository,
            ).searchByNameOrEmail(query, PaginationConstants.DEFAULT_PAGE_SIZE + 1, compositeCursor, defaultOrderBy)
            verify(userSearchRepository).countByNameOrEmail(query)
        }

        @Test
        fun `should return empty connection when no users match search`() {
            // Arrange
            val query = "nonexistent"
            val defaultOrderBy =
                listOf(
                    UserOrderBy(UserOrderField.DISPLAY_NAME, OrderDirection.ASC),
                    UserOrderBy(UserOrderField.ID, OrderDirection.ASC),
                )
            whenever(
                userSearchRepository.searchByNameOrEmail(
                    query,
                    PaginationConstants.DEFAULT_PAGE_SIZE + 1,
                    null,
                    defaultOrderBy,
                ),
            ).thenReturn(emptyList())
            whenever(userSearchRepository.countByNameOrEmail(query))
                .thenReturn(0L)

            // Act
            val result = userManagementService.searchUsers(query, after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).isEmpty()
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
            verify(userSearchRepository, never()).searchByNameOrEmail(any(), any(), any(), any())
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

            // Mock disabled principal
            val disabledPrincipal =
                PrincipalEntity(
                    id = UUID.randomUUID(),
                    principalType = PrincipalType.USER,
                    externalId = userId.toString(),
                    enabled = false,
                )

            whenever(userRepository.findById(userId)).thenReturn(Optional.of(userEntity))
            whenever(principalRepository.findByPrincipalTypeAndExternalId(PrincipalType.USER, userId.toString()))
                .thenReturn(Optional.of(disabledPrincipal))
            whenever(principalRepository.update(any())).thenAnswer {
                it.arguments[0] as PrincipalEntity
            }

            // Act
            val result = userManagementService.enableUser(userId)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.id).isEqualTo(userId)
            verify(userRepository).findById(userId)
            verify(principalRepository).update(any())
        }

        @Test
        fun `should throw exception when user not found`() {
            // Arrange
            val userId = UUID.randomUUID()
            whenever(userRepository.findById(userId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                userManagementService.enableUser(userId)
            }.also { exception ->
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

            // Mock enabled principal
            val enabledPrincipal =
                PrincipalEntity(
                    id = UUID.randomUUID(),
                    principalType = PrincipalType.USER,
                    externalId = userId.toString(),
                    enabled = true,
                )

            whenever(userRepository.findById(userId)).thenReturn(Optional.of(userEntity))
            whenever(principalRepository.findByPrincipalTypeAndExternalId(PrincipalType.USER, userId.toString()))
                .thenReturn(Optional.of(enabledPrincipal))

            // Act & Assert
            assertThrows<IllegalStateException> {
                userManagementService.enableUser(userId)
            }.also { exception ->
                assertThat(exception.message).contains("already enabled")
            }
            verify(userRepository).findById(userId)
            verify(principalRepository, never()).update(any())
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

            // Mock enabled principal
            val enabledPrincipal =
                PrincipalEntity(
                    id = UUID.randomUUID(),
                    principalType = PrincipalType.USER,
                    externalId = userId.toString(),
                    enabled = true,
                )

            whenever(userRepository.findById(userId)).thenReturn(Optional.of(userEntity))
            whenever(principalRepository.findByPrincipalTypeAndExternalId(PrincipalType.USER, userId.toString()))
                .thenReturn(Optional.of(enabledPrincipal))
            whenever(principalRepository.update(any())).thenAnswer {
                it.arguments[0] as PrincipalEntity
            }

            // Act
            val result = userManagementService.disableUser(userId)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.id).isEqualTo(userId)
            verify(userRepository).findById(userId)
            verify(principalRepository).update(any())
        }

        @Test
        fun `should throw exception when user not found`() {
            // Arrange
            val userId = UUID.randomUUID()
            whenever(userRepository.findById(userId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                userManagementService.disableUser(userId)
            }.also { exception ->
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

            // Mock disabled principal
            val disabledPrincipal =
                PrincipalEntity(
                    id = UUID.randomUUID(),
                    principalType = PrincipalType.USER,
                    externalId = userId.toString(),
                    enabled = false,
                )

            whenever(userRepository.findById(userId)).thenReturn(Optional.of(userEntity))
            whenever(principalRepository.findByPrincipalTypeAndExternalId(PrincipalType.USER, userId.toString()))
                .thenReturn(Optional.of(disabledPrincipal))

            // Act & Assert
            assertThrows<IllegalStateException> {
                userManagementService.disableUser(userId)
            }.also { exception ->
                assertThat(exception.message).contains("already disabled")
            }
            verify(userRepository).findById(userId)
            verify(userRepository, never()).update(any())
        }
    }

    @Nested
    @DisplayName("Update User")
    inner class UpdateUserTests {
        @Test
        fun `should update user displayName`() {
            // Arrange
            val userId = UUID.randomUUID()
            val existingUser =
                SecurityTestDataBuilders.user(
                    id = userId,
                    email = "test@example.com",
                    displayName = "Old Name",
                )
            whenever(userRepository.findById(userId)).thenReturn(Optional.of(existingUser))
            whenever(userRepository.update(any())).thenAnswer { it.arguments[0] as UserEntity }

            // Act
            val command =
                UserManagement.UpdateUserCommand(
                    userId = userId,
                    displayName = "New Name",
                )
            val result = userManagementService.updateUser(command)

            // Assert
            assertThat(result.displayName).isEqualTo("New Name")
            assertThat(result.email).isEqualTo("test@example.com") // Email unchanged
            verify(userRepository).update(existingUser)
            assertThat(existingUser.displayName).isEqualTo("New Name")
            assertThat(existingUser.updatedAt).isAfter(existingUser.createdAt)
        }

        @Test
        fun `should update displayName to null`() {
            // Arrange
            val userId = UUID.randomUUID()
            val existingUser =
                SecurityTestDataBuilders.user(
                    id = userId,
                    email = "test@example.com",
                    displayName = "Old Name",
                )
            whenever(userRepository.findById(userId)).thenReturn(Optional.of(existingUser))
            whenever(userRepository.update(any())).thenAnswer { it.arguments[0] as UserEntity }

            // Act
            val command =
                UserManagement.UpdateUserCommand(
                    userId = userId,
                    displayName = null,
                )
            val result = userManagementService.updateUser(command)

            // Assert
            assertThat(result.displayName).isNull()
            assertThat(result.email).isEqualTo("test@example.com") // Email unchanged
            verify(userRepository).update(existingUser)
            assertThat(existingUser.displayName).isNull()
        }

        @Test
        fun `should throw IllegalArgumentException when user not found`() {
            // Arrange
            val userId = UUID.randomUUID()
            whenever(userRepository.findById(userId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                val command =
                    UserManagement.UpdateUserCommand(
                        userId = userId,
                        displayName = "New Name",
                    )
                userManagementService.updateUser(command)
            }.also { exception ->
                assertThat(exception.message).contains("User not found")
            }
            verify(userRepository).findById(userId)
            verify(userRepository, never()).update(any())
        }

        @Test
        fun `should throw IllegalArgumentException when displayName is blank`() {
            // Arrange
            val userId = UUID.randomUUID()

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                UserManagement.UpdateUserCommand(
                    userId = userId,
                    // blank string
                    displayName = "   ",
                )
            }.also { exception ->
                assertThat(exception.message).contains("cannot be blank")
            }
        }
    }

    @Nested
    @DisplayName("Batch Methods Tests")
    inner class BatchMethodsTests {
        @Test
        fun `getUserGroupsBatch should return groups for multiple users`() {
            // Arrange
            val userId1 = UUID.randomUUID()
            val userId2 = UUID.randomUUID()
            val groupId1 = UUID.randomUUID()
            val groupId2 = UUID.randomUUID()
            val membership1 =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId1,
                    groupId = groupId1,
                )
            val membership2 =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId2,
                    groupId = groupId2,
                )
            val group1 = SecurityTestDataBuilders.group(id = groupId1, name = "Group One")
            val group2 = SecurityTestDataBuilders.group(id = groupId2, name = "Group Two")

            whenever(
                groupMembershipRepository.findActiveMembershipsByUserIds(any<List<UUID>>(), any()),
            ).thenReturn(listOf(membership1, membership2))
            whenever(groupRepository.findByIdIn(any<List<UUID>>())).thenReturn(listOf(group1, group2))

            // Act
            val result = userManagementService.getUserGroupsBatch(listOf(userId1, userId2))

            // Assert
            assertThat(result).hasSize(2)
            assertThat(result[userId1]).hasSize(1)
            assertThat(result[userId1]!![0].name).isEqualTo("Group One")
            assertThat(result[userId2]).hasSize(1)
            assertThat(result[userId2]!![0].name).isEqualTo("Group Two")
            verify(groupMembershipRepository).findActiveMembershipsByUserIds(any<List<UUID>>(), any())
        }

        @Test
        fun `getUserGroupsBatch should return empty map for empty user list`() {
            // Act
            val result = userManagementService.getUserGroupsBatch(emptyList())

            // Assert
            assertThat(result).isEmpty()
        }

        @Test
        fun `getUserGroupsBatch should return empty lists for users with no groups`() {
            // Arrange
            val userId = UUID.randomUUID()
            whenever(
                groupMembershipRepository.findActiveMembershipsByUserIds(any<List<UUID>>(), any()),
            ).thenReturn(emptyList())

            // Act
            val result = userManagementService.getUserGroupsBatch(listOf(userId))

            // Assert
            assertThat(result).hasSize(1)
            assertThat(result[userId]).isEmpty()
        }

        @Test
        fun `getUserGroupsBatch should handle users with multiple groups`() {
            // Arrange
            val userId = UUID.randomUUID()
            val groupId1 = UUID.randomUUID()
            val groupId2 = UUID.randomUUID()
            val membership1 =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId,
                    groupId = groupId1,
                )
            val membership2 =
                SecurityTestDataBuilders.groupMembership(
                    userId = userId,
                    groupId = groupId2,
                )
            val group1 = SecurityTestDataBuilders.group(id = groupId1, name = "Group One")
            val group2 = SecurityTestDataBuilders.group(id = groupId2, name = "Group Two")

            whenever(
                groupMembershipRepository.findActiveMembershipsByUserIds(any<List<UUID>>(), any()),
            ).thenReturn(listOf(membership1, membership2))
            whenever(groupRepository.findByIdIn(any<List<UUID>>())).thenReturn(listOf(group1, group2))

            // Act
            val result = userManagementService.getUserGroupsBatch(listOf(userId))

            // Assert
            assertThat(result).hasSize(1)
            assertThat(result[userId]).hasSize(2)
            assertThat(result[userId]!!.map { it.name }).containsExactlyInAnyOrder("Group One", "Group Two")
        }
    }
}
