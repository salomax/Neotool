package io.github.salomax.neotool.security.test.graphql.dataloader

import io.github.salomax.neotool.security.graphql.dataloader.UserPermissionsDataLoader
import io.github.salomax.neotool.security.graphql.dto.PermissionDTO
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

@DisplayName("UserPermissionsDataLoader Tests")
class UserPermissionsDataLoaderTest {
    private lateinit var userManagementResolver: UserManagementResolver
    private lateinit var dataLoader: org.dataloader.DataLoader<String, List<PermissionDTO>>

    @BeforeEach
    fun setUp() {
        userManagementResolver = mock()
        dataLoader = UserPermissionsDataLoader.create(userManagementResolver)
    }

    @Nested
    @DisplayName("Batching")
    inner class BatchingTests {
        @Test
        fun `should batch multiple load calls into single resolver call`() {
            // Arrange
            val userId1 = "user1"
            val userId2 = "user2"
            val permission1 = PermissionDTO(id = "10", name = "transaction:read")
            val permission2 = PermissionDTO(id = "20", name = "transaction:write")
            whenever(userManagementResolver.resolveUserPermissionsBatch(any<List<String>>()))
                .thenReturn(
                    mapOf(
                        userId1 to listOf(permission1),
                        userId2 to listOf(permission2),
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
            assertThat(result1[0].name).isEqualTo("transaction:read")
            assertThat(result2).hasSize(1)
            assertThat(result2[0].name).isEqualTo("transaction:write")
            verify(userManagementResolver, times(1)).resolveUserPermissionsBatch(any<List<String>>())
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCasesTests {
        @Test
        fun `should return empty list for user with no permissions`() {
            // Arrange
            val userId = "user1"
            whenever(userManagementResolver.resolveUserPermissionsBatch(any<List<String>>()))
                .thenReturn(mapOf(userId to emptyList()))

            // Act
            val future = dataLoader.load(userId)
            dataLoader.dispatch()

            // Assert
            assertThat(future.get()).isEmpty()
        }
    }
}
