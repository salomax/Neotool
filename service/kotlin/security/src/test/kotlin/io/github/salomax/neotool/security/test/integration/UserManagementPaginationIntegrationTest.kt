package io.github.salomax.neotool.security.test.integration

import io.github.salomax.neotool.common.graphql.pagination.CursorEncoder
import io.github.salomax.neotool.common.graphql.pagination.OrderDirection
import io.github.salomax.neotool.common.graphql.pagination.PaginationConstants
import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.common.test.transaction.runTransaction
import io.github.salomax.neotool.security.model.UserEntity
import io.github.salomax.neotool.security.model.UserOrderBy
import io.github.salomax.neotool.security.model.UserOrderField
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.authentication.AuthenticationService
import io.github.salomax.neotool.security.service.management.UserManagementService
import io.github.salomax.neotool.security.test.SecurityTestDataBuilders
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import java.util.UUID

/**
 * Integration tests for User Management Service pagination.
 * Tests full flow from service layer to database, validating cursor-based pagination
 * with composite sorting (displayName/email, id).
 */
@MicronautTest(startApplication = true)
@DisplayName("User Management Pagination Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("user-management")
@Tag("pagination")
@Tag("database")
@TestMethodOrder(MethodOrderer.Random::class)
open class UserManagementPaginationIntegrationTest :
    BaseIntegrationTest(),
    PostgresIntegrationTest {
    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var userManagementService: UserManagementService

    @Inject
    lateinit var authenticationService: AuthenticationService

    @Inject
    lateinit var entityManager: EntityManager

    /**
     * Create a test user with database-generated UUID v7.
     * Returns the saved user with the generated ID.
     */
    private fun createTestUser(
        displayName: String? = null,
        email: String? = null,
    ): UserEntity {
        val user =
            SecurityTestDataBuilders.user(
                id = null,
                email = email ?: SecurityTestDataBuilders.uniqueEmail("pagination-test"),
                displayName = displayName,
            )
        return entityManager.runTransaction {
            val savedUser = authenticationService.saveUser(user)
            entityManager.flush()
            savedUser
        }
    }

    @AfterEach
    fun cleanupTestData() {
        try {
            userRepository.deleteAll()
            entityManager.flush()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    @Nested
    @DisplayName("Basic Pagination")
    inner class BasicPaginationTests {
        @Test
        fun `should paginate through multiple pages correctly`() {
            // Arrange: Create 25 users with different names to test pagination
            val pageSize = 10
            val totalUsers = 25
            val users = mutableListOf<UserEntity>()

            // Create users with names that will sort in a predictable order
            // Using names starting with different letters to ensure proper sorting
            val names =
                listOf(
                    "Alice",
                    "Bob",
                    "Charlie",
                    "David",
                    "Eve",
                    "Frank",
                    "Grace",
                    "Henry",
                    "Ivy",
                    "Jack",
                    "Kate",
                    "Liam",
                    "Mia",
                    "Noah",
                    "Olivia",
                    "Paul",
                    "Quinn",
                    "Rachel",
                    "Sam",
                    "Tina",
                    "Uma",
                    "Victor",
                    "Wendy",
                    "Xavier",
                    "Yara",
                )

            names.forEachIndexed { index, name ->
                val user =
                    createTestUser(
                        displayName = name,
                        email = SecurityTestDataBuilders.uniqueEmail("pagination-$index"),
                    )
                users.add(user)
            }

            // Act & Assert: Navigate through all pages
            var currentCursor: String? = null
            var allRetrievedUsers = mutableListOf<String>()
            var pageNumber = 0

            do {
                val result =
                    userManagementService.searchUsers(
                        query = null,
                        first = pageSize,
                        after = currentCursor,
                    )

                assertThat(result).isNotNull()
                assertThat(result.edges.map { it.node }).isNotEmpty()
                assertThat(result.totalCount).isEqualTo(totalUsers.toLong())

                // Collect all user names from this page
                val pageUserNames = result.edges.map { it.node.displayName ?: it.node.email }
                allRetrievedUsers.addAll(pageUserNames)

                // Verify page info
                // Note: hasPreviousPage is always false for forward-only cursor pagination
                assertThat(result.pageInfo.hasPreviousPage).isFalse()

                // Check if there are more pages
                val expectedHasNextPage = allRetrievedUsers.size < totalUsers
                assertThat(result.pageInfo.hasNextPage).isEqualTo(expectedHasNextPage)

                // Update cursor for next page
                currentCursor = result.pageInfo.endCursor
                pageNumber++

                // Safety check to prevent infinite loops
                assertThat(pageNumber).isLessThan(10) // Should not need more than 10 pages for 25 users
            } while (currentCursor != null && result.pageInfo.hasNextPage)

            // Assert: All users should be retrieved exactly once
            assertThat(allRetrievedUsers).hasSize(totalUsers)
            assertThat(allRetrievedUsers).containsExactlyInAnyOrderElementsOf(names)

            // Verify no duplicates
            assertThat(allRetrievedUsers).doesNotHaveDuplicates()
        }

        @Test
        fun `should handle pagination with users having same displayName`() {
            // Arrange: Create users with same displayName but different emails
            // This tests the composite sort (displayName, id)
            val sharedName = "John Doe"
            val user1 = createTestUser(displayName = sharedName, email = SecurityTestDataBuilders.uniqueEmail("john1"))
            val user2 = createTestUser(displayName = sharedName, email = SecurityTestDataBuilders.uniqueEmail("john2"))
            val user3 = createTestUser(displayName = sharedName, email = SecurityTestDataBuilders.uniqueEmail("john3"))

            // Add some users with different names
            val user4 = createTestUser(displayName = "Alice", email = SecurityTestDataBuilders.uniqueEmail("alice"))
            val user5 = createTestUser(displayName = "Zoe", email = SecurityTestDataBuilders.uniqueEmail("zoe"))

            // Act: Paginate through all users
            val page1 = userManagementService.searchUsers(query = null, first = 2, after = null)
            assertThat(page1.edges.map { it.node }).hasSize(2)
            assertThat(page1.pageInfo.hasNextPage).isTrue()

            val page2 =
                userManagementService.searchUsers(
                    query = null,
                    first = 2,
                    after = page1.pageInfo.endCursor,
                )
            assertThat(page2.edges.map { it.node }).hasSize(2)
            assertThat(page2.pageInfo.hasNextPage).isTrue()

            val page3 =
                userManagementService.searchUsers(
                    query = null,
                    first = 2,
                    after = page2.pageInfo.endCursor,
                )
            assertThat(page3.edges.map { it.node }).hasSize(1)
            assertThat(page3.pageInfo.hasNextPage).isFalse()

            // Assert: All users should be retrieved
            val allUsers = page1.edges.map { it.node } + page2.edges.map { it.node } + page3.edges.map { it.node }
            assertThat(allUsers).hasSize(5)
            val allUserIds = allUsers.map { it.id }.toSet()
            assertThat(allUserIds).containsExactlyInAnyOrder(
                user1.id,
                user2.id,
                user3.id,
                user4.id,
                user5.id,
            )
        }

        @Test
        fun `should handle pagination with users having null displayName`() {
            // Arrange: Create users with null displayName (should use email for sorting)
            val user1 = createTestUser(displayName = null, email = SecurityTestDataBuilders.uniqueEmail("a-user"))
            val user2 = createTestUser(displayName = null, email = SecurityTestDataBuilders.uniqueEmail("b-user"))
            val user3 = createTestUser(displayName = "Alice", email = SecurityTestDataBuilders.uniqueEmail("alice"))

            // Act: Paginate
            val page1 = userManagementService.searchUsers(query = null, first = 2, after = null)
            val page2 =
                userManagementService.searchUsers(
                    query = null,
                    first = 2,
                    after = page1.pageInfo.endCursor,
                )

            // Assert: All users retrieved
            val allUsers = page1.edges.map { it.node } + page2.edges.map { it.node }
            assertThat(allUsers).hasSize(3)
            val allUserIds = allUsers.map { it.id }.toSet()
            assertThat(allUserIds).containsExactlyInAnyOrder(user1.id, user2.id, user3.id)
        }

        @Test
        fun `should return correct totalCount across pages`() {
            // Arrange
            val totalUsers = 15
            repeat(totalUsers) { index ->
                createTestUser(
                    displayName = "User $index",
                    email = SecurityTestDataBuilders.uniqueEmail("user-$index"),
                )
            }

            // Act: Get first page
            val page1 = userManagementService.searchUsers(query = null, first = 5, after = null)

            // Assert: Total count should be consistent
            assertThat(page1.totalCount).isEqualTo(totalUsers.toLong())

            // Act: Get second page
            val page2 =
                userManagementService.searchUsers(
                    query = null,
                    first = 5,
                    after = page1.pageInfo.endCursor,
                )

            // Assert: Total count should still be the same
            assertThat(page2.totalCount).isEqualTo(totalUsers.toLong())
            assertThat(page1.totalCount).isEqualTo(page2.totalCount)
        }
    }

    @Nested
    @DisplayName("Pagination with Search Query")
    inner class PaginationWithSearchTests {
        @Test
        fun `should paginate search results correctly`() {
            // Arrange: Create users with and without "test" in name/email
            val testUsers =
                listOf(
                    createTestUser(displayName = "Test User 1", email = SecurityTestDataBuilders.uniqueEmail("test1")),
                    createTestUser(displayName = "Test User 2", email = SecurityTestDataBuilders.uniqueEmail("test2")),
                    createTestUser(displayName = "Test User 3", email = SecurityTestDataBuilders.uniqueEmail("test3")),
                    createTestUser(displayName = "Test User 4", email = SecurityTestDataBuilders.uniqueEmail("test4")),
                    createTestUser(displayName = "Test User 5", email = SecurityTestDataBuilders.uniqueEmail("test5")),
                )

            // Create non-matching users
            createTestUser(displayName = "Alice", email = SecurityTestDataBuilders.uniqueEmail("alice"))
            createTestUser(displayName = "Bob", email = SecurityTestDataBuilders.uniqueEmail("bob"))

            // Act: Search and paginate
            val page1 = userManagementService.searchUsers(query = "test", first = 2, after = null)
            assertThat(page1.edges.map { it.node }).hasSize(2)
            assertThat(page1.totalCount).isEqualTo(5L)
            assertThat(page1.pageInfo.hasNextPage).isTrue()

            val page2 =
                userManagementService.searchUsers(
                    query = "test",
                    first = 2,
                    after = page1.pageInfo.endCursor,
                )
            assertThat(page2.edges.map { it.node }).hasSize(2)
            assertThat(page2.totalCount).isEqualTo(5L)
            assertThat(page2.pageInfo.hasNextPage).isTrue()

            val page3 =
                userManagementService.searchUsers(
                    query = "test",
                    first = 2,
                    after = page2.pageInfo.endCursor,
                )
            assertThat(page3.edges.map { it.node }).hasSize(1)
            assertThat(page3.totalCount).isEqualTo(5L)
            assertThat(page3.pageInfo.hasNextPage).isFalse()

            // Assert: All test users retrieved
            val allUsers = page1.edges.map { it.node } + page2.edges.map { it.node } + page3.edges.map { it.node }
            assertThat(allUsers).hasSize(5)
            val allUserIds = allUsers.map { it.id }.toSet()
            assertThat(allUserIds).containsExactlyInAnyOrderElementsOf(testUsers.map { it.id })
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCaseTests {
        @Test
        fun `should handle empty result set`() {
            // Act
            val result = userManagementService.searchUsers(query = "nonexistent", first = 10, after = null)

            // Assert
            assertThat(result.edges.map { it.node }).isEmpty()
            assertThat(result.edges).isEmpty()
            assertThat(result.pageInfo.hasNextPage).isFalse()
            assertThat(result.pageInfo.hasPreviousPage).isFalse()
            assertThat(result.totalCount).isEqualTo(0L)
        }

        @Test
        fun `should handle single page of results`() {
            // Arrange
            createTestUser(displayName = "User 1", email = SecurityTestDataBuilders.uniqueEmail("user1"))
            createTestUser(displayName = "User 2", email = SecurityTestDataBuilders.uniqueEmail("user2"))

            // Act
            val result = userManagementService.searchUsers(query = null, first = 10, after = null)

            // Assert
            assertThat(result.edges.map { it.node }).hasSize(2)
            assertThat(result.pageInfo.hasNextPage).isFalse()
            assertThat(result.totalCount).isEqualTo(2L)
        }

        @Test
        fun `should handle page size larger than total results`() {
            // Arrange
            createTestUser(displayName = "User 1", email = SecurityTestDataBuilders.uniqueEmail("user1"))
            createTestUser(displayName = "User 2", email = SecurityTestDataBuilders.uniqueEmail("user2"))

            // Act
            val result = userManagementService.searchUsers(query = null, first = 100, after = null)

            // Assert
            assertThat(result.edges.map { it.node }).hasSize(2)
            assertThat(result.pageInfo.hasNextPage).isFalse()
            assertThat(result.totalCount).isEqualTo(2L)
        }

        @Test
        fun `should enforce max page size`() {
            // Arrange: Create more users than MAX_PAGE_SIZE
            repeat(PaginationConstants.MAX_PAGE_SIZE + 10) { index ->
                createTestUser(
                    displayName = "User $index",
                    email = SecurityTestDataBuilders.uniqueEmail("user-$index"),
                )
            }

            // Act
            // Request more than max
            val result =
                userManagementService.searchUsers(
                    query = null,
                    first = PaginationConstants.MAX_PAGE_SIZE + 50,
                    after = null,
                )

            // Assert: Should only return MAX_PAGE_SIZE items
            assertThat(result.edges.map { it.node }).hasSize(PaginationConstants.MAX_PAGE_SIZE)
            assertThat(result.pageInfo.hasNextPage).isTrue() // More items available
        }

        @Test
        fun `should throw exception for invalid cursor`() {
            // Act & Assert
            org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                userManagementService.searchUsers(
                    query = null,
                    first = 10,
                    after = "invalid-cursor",
                )
            }
        }

        @Test
        fun `should handle cursor pointing to non-existent user`() {
            // Arrange: Create a valid UUID cursor for a user that doesn't exist
            val nonExistentId = UUID.randomUUID()
            val invalidCursor = CursorEncoder.encodeCursor(nonExistentId)

            // Act: Should return empty result set (cursor predicate won't match anything)
            // The repository no longer validates cursor existence, it just uses the cursor for pagination
            val result =
                userManagementService.searchUsers(
                    query = null,
                    first = 10,
                    after = invalidCursor,
                )

            // Assert: Should return empty result set
            assertThat(result.edges.map { it.node }).isEmpty()
            assertThat(result.edges).isEmpty()
            assertThat(result.pageInfo.hasNextPage).isFalse()
        }
    }

    @Nested
    @DisplayName("Sort Order Validation")
    inner class SortOrderTests {
        @Test
        fun `should maintain consistent sort order across pages`() {
            // Arrange: Create users with names that will sort in a specific order
            val names = listOf("Alice", "Bob", "Charlie", "David", "Eve")
            val users =
                names.map { name ->
                    createTestUser(displayName = name, email = SecurityTestDataBuilders.uniqueEmail(name.lowercase()))
                }

            // Act: Get first page
            val page1 = userManagementService.searchUsers(query = null, first = 2, after = null)
            val page1Names = page1.edges.map { it.node.displayName ?: it.node.email }

            // Act: Get second page
            val page2 =
                userManagementService.searchUsers(
                    query = null,
                    first = 2,
                    after = page1.pageInfo.endCursor,
                )
            val page2Names = page2.edges.map { it.node.displayName ?: it.node.email }

            // Act: Get third page
            val page3 =
                userManagementService.searchUsers(
                    query = null,
                    first = 2,
                    after = page2.pageInfo.endCursor,
                )
            val page3Names = page3.edges.map { it.node.displayName ?: it.node.email }

            // Assert: Combined order should be correct
            val allNames = page1Names + page2Names + page3Names
            assertThat(allNames).containsExactlyElementsOf(names)
        }
    }

    @Nested
    @DisplayName("Sorting with orderBy")
    inner class SortingWithOrderByTests {
        @Test
        fun `should sort by EMAIL ASC when orderBy specified`() {
            // Arrange: Create users with different emails
            val user1 = createTestUser(displayName = "Zoe", email = "alice@example.com")
            val user2 = createTestUser(displayName = "Alice", email = "bob@example.com")
            val user3 = createTestUser(displayName = "Bob", email = "charlie@example.com")

            // Act: Sort by email ASC
            val result =
                userManagementService.searchUsers(
                    query = null,
                    first = 10,
                    after = null,
                    orderBy = listOf(UserOrderBy(UserOrderField.EMAIL, OrderDirection.ASC)),
                )

            // Assert: Should be sorted by email
            assertThat(result.edges.map { it.node }).hasSize(3)
            assertThat(result.edges[0].node.email).isEqualTo("alice@example.com")
            assertThat(result.edges[1].node.email).isEqualTo("bob@example.com")
            assertThat(result.edges[2].node.email).isEqualTo("charlie@example.com")
        }

        @Test
        fun `should sort by EMAIL DESC when orderBy specified`() {
            // Arrange: Create users with different emails
            val user1 = createTestUser(displayName = "Zoe", email = "alice@example.com")
            val user2 = createTestUser(displayName = "Alice", email = "bob@example.com")
            val user3 = createTestUser(displayName = "Bob", email = "charlie@example.com")

            // Act: Sort by email DESC
            val result =
                userManagementService.searchUsers(
                    query = null,
                    first = 10,
                    after = null,
                    orderBy = listOf(UserOrderBy(UserOrderField.EMAIL, OrderDirection.DESC)),
                )

            // Assert: Should be sorted by email descending
            assertThat(result.edges.map { it.node }).hasSize(3)
            assertThat(result.edges[0].node.email).isEqualTo("charlie@example.com")
            assertThat(result.edges[1].node.email).isEqualTo("bob@example.com")
            assertThat(result.edges[2].node.email).isEqualTo("alice@example.com")
        }

        // Note: Test for sorting by ENABLED was removed because enabled status is now stored
        // in Principal table and not available for direct sorting on User entity.

        @Test
        fun `should use default sort when orderBy is null`() {
            // Arrange: Create users with different display names
            val user1 = createTestUser(displayName = "Zoe", email = SecurityTestDataBuilders.uniqueEmail("zoe"))
            val user2 = createTestUser(displayName = "Alice", email = SecurityTestDataBuilders.uniqueEmail("alice"))
            val user3 = createTestUser(displayName = "Bob", email = SecurityTestDataBuilders.uniqueEmail("bob"))

            // Act: No orderBy specified (should default to DISPLAY_NAME ASC)
            val result =
                userManagementService.searchUsers(
                    query = null,
                    first = 10,
                    after = null,
                    orderBy = null,
                )

            // Assert: Should be sorted by displayName ASC (default)
            assertThat(result.edges.map { it.node }).hasSize(3)
            assertThat(result.edges[0].node.displayName).isEqualTo("Alice")
            assertThat(result.edges[1].node.displayName).isEqualTo("Bob")
            assertThat(result.edges[2].node.displayName).isEqualTo("Zoe")
        }

        @Test
        fun `should fallback to ID ASC when orderBy is empty array`() {
            // Arrange: Create users (order doesn't matter for this test)
            createTestUser(displayName = "User 1", email = SecurityTestDataBuilders.uniqueEmail("user1"))
            createTestUser(displayName = "User 2", email = SecurityTestDataBuilders.uniqueEmail("user2"))
            createTestUser(displayName = "User 3", email = SecurityTestDataBuilders.uniqueEmail("user3"))

            // Act: Empty orderBy array (should fallback to ID ASC)
            val result =
                userManagementService.searchUsers(
                    query = null,
                    first = 10,
                    after = null,
                    orderBy = emptyList(),
                )

            // Assert: Should return results (sorted by ID)
            assertThat(result.edges.map { it.node }).hasSize(3)
            // Verify IDs are in ascending order
            val ids = result.edges.map { it.node.id }
            assertThat(ids).isSorted()
        }

        @Test
        fun `should paginate correctly with custom orderBy`() {
            // Arrange: Create multiple users
            val emails = listOf("a@example.com", "b@example.com", "c@example.com", "d@example.com", "e@example.com")
            emails.forEach { email ->
                createTestUser(displayName = null, email = email)
            }

            // Act: Sort by email ASC, paginate
            val page1 =
                userManagementService.searchUsers(
                    query = null,
                    first = 2,
                    after = null,
                    orderBy = listOf(UserOrderBy(UserOrderField.EMAIL, OrderDirection.ASC)),
                )

            assertThat(page1.edges.map { it.node }).hasSize(2)
            assertThat(page1.edges[0].node.email).isEqualTo("a@example.com")
            assertThat(page1.edges[1].node.email).isEqualTo("b@example.com")
            assertThat(page1.pageInfo.hasNextPage).isTrue()

            // Act: Get next page
            val page2 =
                userManagementService.searchUsers(
                    query = null,
                    first = 2,
                    after = page1.pageInfo.endCursor,
                    orderBy = listOf(UserOrderBy(UserOrderField.EMAIL, OrderDirection.ASC)),
                )

            assertThat(page2.edges.map { it.node }).hasSize(2)
            assertThat(page2.edges[0].node.email).isEqualTo("c@example.com")
            assertThat(page2.edges[1].node.email).isEqualTo("d@example.com")
            assertThat(page2.pageInfo.hasNextPage).isTrue()

            // Act: Get last page
            val page3 =
                userManagementService.searchUsers(
                    query = null,
                    first = 2,
                    after = page2.pageInfo.endCursor,
                    orderBy = listOf(UserOrderBy(UserOrderField.EMAIL, OrderDirection.ASC)),
                )

            assertThat(page3.edges.map { it.node }).hasSize(1)
            assertThat(page3.edges[0].node.email).isEqualTo("e@example.com")
            assertThat(page3.pageInfo.hasNextPage).isFalse()
        }

        @Test
        fun `should handle composite cursor with field values for pagination`() {
            // Arrange: Create users with same displayName but different emails
            val user1 = createTestUser(displayName = "John", email = "john1@example.com")
            val user2 = createTestUser(displayName = "John", email = "john2@example.com")
            val user3 = createTestUser(displayName = "John", email = "john3@example.com")
            val user4 = createTestUser(displayName = "Alice", email = "alice@example.com")

            // Act: Sort by displayName ASC, then email ASC (implicit via ID)
            val page1 =
                userManagementService.searchUsers(
                    query = null,
                    first = 2,
                    after = null,
                    orderBy = listOf(UserOrderBy(UserOrderField.DISPLAY_NAME, OrderDirection.ASC)),
                )

            // Assert: First page should have Alice and first John
            assertThat(page1.edges.map { it.node }).hasSize(2)
            assertThat(page1.edges[0].node.displayName).isEqualTo("Alice")
            assertThat(page1.edges[1].node.displayName).isEqualTo("John")

            // Act: Get next page using composite cursor
            val page2 =
                userManagementService.searchUsers(
                    query = null,
                    first = 2,
                    after = page1.pageInfo.endCursor,
                    orderBy = listOf(UserOrderBy(UserOrderField.DISPLAY_NAME, OrderDirection.ASC)),
                )

            // Assert: Should continue with remaining John users
            assertThat(page2.edges.map { it.node }).hasSize(2)
            assertThat(page2.edges.map { it.node }.all { it.displayName == "John" }).isTrue()
        }

        @Test
        fun `should maintain sort order across pages with search query`() {
            // Arrange: Create users with "test" in name/email
            createTestUser(displayName = "Test User A", email = SecurityTestDataBuilders.uniqueEmail("test-a"))
            createTestUser(displayName = "Test User B", email = SecurityTestDataBuilders.uniqueEmail("test-b"))
            createTestUser(displayName = "Test User C", email = SecurityTestDataBuilders.uniqueEmail("test-c"))
            createTestUser(displayName = "Alice", email = SecurityTestDataBuilders.uniqueEmail("alice")) // Not matching

            // Act: Search with custom sort
            val page1 =
                userManagementService.searchUsers(
                    query = "test",
                    first = 2,
                    after = null,
                    orderBy = listOf(UserOrderBy(UserOrderField.DISPLAY_NAME, OrderDirection.DESC)),
                )

            assertThat(page1.edges.map { it.node }).hasSize(2)
            assertThat(page1.totalCount).isEqualTo(3L)
            // Should be sorted DESC by displayName
            assertThat(page1.edges[0].node.displayName).isEqualTo("Test User C")
            assertThat(page1.edges[1].node.displayName).isEqualTo("Test User B")

            // Act: Get next page
            val page2 =
                userManagementService.searchUsers(
                    query = "test",
                    first = 2,
                    after = page1.pageInfo.endCursor,
                    orderBy = listOf(UserOrderBy(UserOrderField.DISPLAY_NAME, OrderDirection.DESC)),
                )

            assertThat(page2.edges.map { it.node }).hasSize(1)
            assertThat(page2.edges[0].node.displayName).isEqualTo("Test User A")
        }
    }
}
