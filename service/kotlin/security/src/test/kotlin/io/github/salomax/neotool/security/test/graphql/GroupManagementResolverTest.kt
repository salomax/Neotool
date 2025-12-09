package io.github.salomax.neotool.security.test.graphql

import io.github.salomax.neotool.security.domain.rbac.Role
import io.github.salomax.neotool.security.graphql.mapper.GroupManagementMapper
import io.github.salomax.neotool.security.graphql.mapper.UserManagementMapper
import io.github.salomax.neotool.security.graphql.resolver.GroupManagementResolver
import io.github.salomax.neotool.security.service.GroupManagementService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

@DisplayName("GroupManagementResolver Batch Methods Tests")
class GroupManagementResolverTest {
    private lateinit var groupManagementService: GroupManagementService
    private lateinit var mapper: GroupManagementMapper
    private lateinit var userManagementMapper: UserManagementMapper
    private lateinit var groupManagementResolver: GroupManagementResolver

    @BeforeEach
    fun setUp() {
        groupManagementService = mock()
        mapper = mock()
        userManagementMapper = mock()
        groupManagementResolver =
            GroupManagementResolver(
                groupManagementService,
                mapper,
                userManagementMapper,
            )
    }

    @Nested
    @DisplayName("resolveGroupRolesBatch")
    inner class ResolveGroupRolesBatchTests {
        @Test
        fun `should return roles for multiple groups`() {
            // Arrange
            val groupId1 = UUID.randomUUID()
            val groupId2 = UUID.randomUUID()
            val role1 = Role(id = 1, name = "admin", createdAt = Instant.now(), updatedAt = Instant.now())
            val role2 = Role(id = 2, name = "editor", createdAt = Instant.now(), updatedAt = Instant.now())
            whenever(groupManagementService.getGroupRolesBatch(any(), anyOrNull()))
                .thenReturn(
                    mapOf(
                        groupId1 to listOf(role1),
                        groupId2 to listOf(role2),
                    ),
                )

            // Act
            val result =
                groupManagementResolver.resolveGroupRolesBatch(
                    listOf(
                        groupId1.toString(),
                        groupId2.toString(),
                    ),
                )

            // Assert
            assertThat(result).hasSize(2)
            assertThat(result[groupId1.toString()]).hasSize(1)
            assertThat(result[groupId1.toString()]!![0].name).isEqualTo("admin")
            assertThat(result[groupId2.toString()]).hasSize(1)
            assertThat(result[groupId2.toString()]!![0].name).isEqualTo("editor")
            verify(groupManagementService).getGroupRolesBatch(any(), anyOrNull())
        }

        @Test
        fun `should return empty map for empty group list`() {
            // Act
            val result = groupManagementResolver.resolveGroupRolesBatch(emptyList())

            // Assert
            assertThat(result).isEmpty()
        }

        @Test
        fun `should filter out invalid group IDs`() {
            // Arrange
            val groupId1 = UUID.randomUUID()
            val invalidId = "not-a-uuid"
            val role1 = Role(id = 1, name = "admin", createdAt = Instant.now(), updatedAt = Instant.now())
            whenever(groupManagementService.getGroupRolesBatch(any(), anyOrNull()))
                .thenReturn(mapOf(groupId1 to listOf(role1)))

            // Act
            val result = groupManagementResolver.resolveGroupRolesBatch(listOf(groupId1.toString(), invalidId))

            // Assert
            assertThat(result).hasSize(1)
            assertThat(result[groupId1.toString()]).hasSize(1)
            assertThat(result).doesNotContainKey(invalidId)
        }

        @Test
        fun `should preserve order of requested group IDs`() {
            // Arrange
            val groupId1 = UUID.randomUUID()
            val groupId2 = UUID.randomUUID()
            val groupId3 = UUID.randomUUID()
            val role1 = Role(id = 1, name = "admin", createdAt = Instant.now(), updatedAt = Instant.now())
            whenever(groupManagementService.getGroupRolesBatch(any(), anyOrNull()))
                .thenReturn(
                    mapOf(
                        groupId1 to listOf(role1),
                        groupId2 to emptyList(),
                        groupId3 to listOf(role1),
                    ),
                )

            // Act
            val result =
                groupManagementResolver.resolveGroupRolesBatch(
                    listOf(groupId1.toString(), groupId2.toString(), groupId3.toString()),
                )

            // Assert
            assertThat(result.keys.toList())
                .containsExactly(
                    groupId1.toString(),
                    groupId2.toString(),
                    groupId3.toString(),
                )
        }
    }
}
