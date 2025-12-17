package io.github.salomax.neotool.security.test.graphql.dataloader

import io.github.salomax.neotool.security.graphql.dataloader.GroupRolesDataLoader
import io.github.salomax.neotool.security.graphql.dto.RoleDTO
import io.github.salomax.neotool.security.graphql.resolver.GroupManagementResolver
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
import java.util.UUID

@DisplayName("GroupRolesDataLoader Tests")
class GroupRolesDataLoaderTest {
    private lateinit var groupManagementResolver: GroupManagementResolver
    private lateinit var dataLoader: org.dataloader.DataLoader<String, List<RoleDTO>>

    @BeforeEach
    fun setUp() {
        groupManagementResolver = mock()
        dataLoader = GroupRolesDataLoader.create(groupManagementResolver)
    }

    @Nested
    @DisplayName("Batching")
    inner class BatchingTests {
        @Test
        fun `should batch multiple load calls into single resolver call`() {
            // Arrange
            val groupId1 = "group1"
            val groupId2 = "group2"
            val role1 = RoleDTO(id = UUID.randomUUID().toString(), name = "admin")
            val role2 = RoleDTO(id = UUID.randomUUID().toString(), name = "editor")
            whenever(groupManagementResolver.resolveGroupRolesBatch(any<List<String>>()))
                .thenReturn(
                    mapOf(
                        groupId1 to listOf(role1),
                        groupId2 to listOf(role2),
                    ),
                )

            // Act
            val future1 = dataLoader.load(groupId1)
            val future2 = dataLoader.load(groupId2)
            dataLoader.dispatch()

            // Assert
            val result1 = future1.get()
            val result2 = future2.get()
            assertThat(result1).hasSize(1)
            assertThat(result1[0].name).isEqualTo("admin")
            assertThat(result2).hasSize(1)
            assertThat(result2[0].name).isEqualTo("editor")
            verify(groupManagementResolver, times(1)).resolveGroupRolesBatch(any<List<String>>())
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCasesTests {
        @Test
        fun `should return empty list for group with no roles`() {
            // Arrange
            val groupId = "group1"
            whenever(groupManagementResolver.resolveGroupRolesBatch(any<List<String>>()))
                .thenReturn(mapOf(groupId to emptyList()))

            // Act
            val future = dataLoader.load(groupId)
            dataLoader.dispatch()

            // Assert
            assertThat(future.get()).isEmpty()
        }
    }
}
