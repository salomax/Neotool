package io.github.salomax.neotool.security.test.graphql.dataloader

import io.github.salomax.neotool.security.graphql.dataloader.UserRolesDataLoader
import io.github.salomax.neotool.security.graphql.dto.RoleDTO
import io.github.salomax.neotool.security.graphql.resolver.UserManagementResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@DisplayName("UserRolesDataLoader Tests")
class UserRolesDataLoaderTest {
    private lateinit var userManagementResolver: UserManagementResolver
    private lateinit var dataLoader: org.dataloader.DataLoader<String, List<RoleDTO>>

    @BeforeEach
    fun setUp() {
        userManagementResolver = mock()
        dataLoader = UserRolesDataLoader.create(userManagementResolver)
    }

    @Nested
    @DisplayName("Batching")
    inner class BatchingTests {
        @Test
        fun `should batch multiple load calls into single resolver call`() {
            // Arrange
            val userId1 = "user1"
            val userId2 = "user2"
            val role1 = RoleDTO(id = "1", name = "admin")
            val role2 = RoleDTO(id = "2", name = "editor")
            whenever(userManagementResolver.resolveUserRolesBatch(any<List<String>>()))
                .thenReturn(
                    mapOf(
                        userId1 to listOf(role1),
                        userId2 to listOf(role2),
                    ),
                )

            // Act
            val future1 = dataLoader.load(userId1)
            val future2 = dataLoader.load(userId2)
            // Dispatch to trigger batching
            dataLoader.dispatch()

            // Assert
            val result1 = future1.get()
            val result2 = future2.get()
            assertThat(result1).hasSize(1)
            assertThat(result1[0].name).isEqualTo("admin")
            assertThat(result2).hasSize(1)
            assertThat(result2[0].name).isEqualTo("editor")
            // Verify batch method was called once with both IDs
            verify(userManagementResolver, times(1)).resolveUserRolesBatch(any<List<String>>())
        }

        @Test
        fun `should return results in same order as load calls`() {
            // Arrange
            val userId1 = "user1"
            val userId2 = "user2"
            val userId3 = "user3"
            val role1 = RoleDTO(id = "1", name = "admin")
            val role2 = RoleDTO(id = "2", name = "editor")
            val role3 = RoleDTO(id = "3", name = "viewer")
            whenever(userManagementResolver.resolveUserRolesBatch(any<List<String>>()))
                .thenReturn(
                    mapOf(
                        userId1 to listOf(role1),
                        userId2 to listOf(role2),
                        userId3 to listOf(role3),
                    ),
                )

            // Act
            val future1 = dataLoader.load(userId1)
            val future2 = dataLoader.load(userId2)
            val future3 = dataLoader.load(userId3)
            dataLoader.dispatch()

            // Assert
            assertThat(future1.get()[0].name).isEqualTo("admin")
            assertThat(future2.get()[0].name).isEqualTo("editor")
            assertThat(future3.get()[0].name).isEqualTo("viewer")
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCasesTests {
        @Test
        fun `should return empty list for user with no roles`() {
            // Arrange
            val userId = "user1"
            whenever(userManagementResolver.resolveUserRolesBatch(any<List<String>>()))
                .thenReturn(mapOf(userId to emptyList()))

            // Act
            val future = dataLoader.load(userId)
            dataLoader.dispatch()

            // Assert
            assertThat(future.get()).isEmpty()
        }

        @Test
        fun `should return empty list when user ID not in batch result`() {
            // Arrange
            val userId = "user1"
            whenever(userManagementResolver.resolveUserRolesBatch(any<List<String>>()))
                .thenReturn(emptyMap())

            // Act
            val future = dataLoader.load(userId)
            dataLoader.dispatch()

            // Assert
            assertThat(future.get()).isEmpty()
        }

        @Test
        fun `should handle resolver errors gracefully`() {
            // Arrange
            val userId = "user1"
            whenever(userManagementResolver.resolveUserRolesBatch(any<List<String>>()))
                .thenThrow(RuntimeException("Database error"))

            // Act
            val future = dataLoader.load(userId)
            dataLoader.dispatch()

            // Assert
            assertThat(future.isCompletedExceptionally).isTrue()
        }
    }
}
