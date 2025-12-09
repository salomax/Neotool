package io.github.salomax.neotool.security.test.graphql.dataloader

import io.github.salomax.neotool.security.graphql.dataloader.UserGroupsDataLoader
import io.github.salomax.neotool.security.graphql.dto.GroupDTO
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

@DisplayName("UserGroupsDataLoader Tests")
class UserGroupsDataLoaderTest {
    private lateinit var userManagementResolver: UserManagementResolver
    private lateinit var dataLoader: org.dataloader.DataLoader<String, List<GroupDTO>>

    @BeforeEach
    fun setUp() {
        userManagementResolver = mock()
        dataLoader = UserGroupsDataLoader.create(userManagementResolver)
    }

    @Nested
    @DisplayName("Batching")
    inner class BatchingTests {
        @Test
        fun `should batch multiple load calls into single resolver call`() {
            // Arrange
            val userId1 = "user1"
            val userId2 = "user2"
            val group1 = GroupDTO(id = "group1", name = "Group One", description = "Desc 1")
            val group2 = GroupDTO(id = "group2", name = "Group Two", description = "Desc 2")
            whenever(userManagementResolver.resolveUserGroupsBatch(any<List<String>>()))
                .thenReturn(
                    mapOf(
                        userId1 to listOf(group1),
                        userId2 to listOf(group2),
                    ),
                )

            // Act
            val future1 = dataLoader.load(userId1)
            val future2 = dataLoader.load(userId2)
            dataLoader.dispatch()

            // Assert
            val result1 = future1.get()
            val result2 = future2.get()
            assertThat(result1).hasSize(1)
            assertThat(result1[0].name).isEqualTo("Group One")
            assertThat(result2).hasSize(1)
            assertThat(result2[0].name).isEqualTo("Group Two")
            verify(userManagementResolver, times(1)).resolveUserGroupsBatch(any<List<String>>())
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCasesTests {
        @Test
        fun `should return empty list for user with no groups`() {
            // Arrange
            val userId = "user1"
            whenever(userManagementResolver.resolveUserGroupsBatch(any<List<String>>()))
                .thenReturn(mapOf(userId to emptyList()))

            // Act
            val future = dataLoader.load(userId)
            dataLoader.dispatch()

            // Assert
            assertThat(future.get()).isEmpty()
        }
    }
}
