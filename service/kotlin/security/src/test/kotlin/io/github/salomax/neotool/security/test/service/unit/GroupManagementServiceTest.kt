package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.common.graphql.pagination.CursorEncoder
import io.github.salomax.neotool.common.graphql.pagination.PaginationConstants
import io.github.salomax.neotool.security.domain.GroupManagement
import io.github.salomax.neotool.security.repo.GroupRepository
import io.github.salomax.neotool.security.service.GroupManagementService
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

@DisplayName("GroupManagementService Unit Tests")
class GroupManagementServiceTest {
    private lateinit var groupRepository: GroupRepository
    private lateinit var groupManagementService: GroupManagementService

    @BeforeEach
    fun setUp() {
        groupRepository = mock()
        groupManagementService = GroupManagementService(groupRepository)
    }

    @Nested
    @DisplayName("List Groups")
    inner class ListGroupsTests {
        @Test
        fun `should list groups with default page size`() {
            // Arrange
            val group1 = SecurityTestDataBuilders.group(id = UUID.randomUUID(), name = "Group One")
            val group2 = SecurityTestDataBuilders.group(id = UUID.randomUUID(), name = "Group Two")
            whenever(groupRepository.findAll(PaginationConstants.DEFAULT_PAGE_SIZE + 1, null))
                .thenReturn(listOf(group1, group2))

            // Act
            val result = groupManagementService.listGroups(after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.nodes).hasSize(2)
            assertThat(result.pageInfo.hasNextPage).isFalse()
            verify(groupRepository).findAll(PaginationConstants.DEFAULT_PAGE_SIZE + 1, null)
        }

        @Test
        fun `should list groups with custom page size`() {
            // Arrange
            val first = 10
            val group1 = SecurityTestDataBuilders.group(id = UUID.randomUUID(), name = "Group One")
            whenever(groupRepository.findAll(first + 1, null))
                .thenReturn(listOf(group1))

            // Act
            val result = groupManagementService.listGroups(first = first, after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.nodes).hasSize(1)
            verify(groupRepository).findAll(first + 1, null)
        }

        @Test
        fun `should enforce max page size`() {
            // Arrange
            val first = 200 // Exceeds MAX_PAGE_SIZE
            whenever(groupRepository.findAll(PaginationConstants.MAX_PAGE_SIZE + 1, null))
                .thenReturn(emptyList())

            // Act
            val result = groupManagementService.listGroups(first = first, after = null)

            // Assert
            assertThat(result).isNotNull()
            verify(groupRepository).findAll(PaginationConstants.MAX_PAGE_SIZE + 1, null)
        }

        @Test
        fun `should list groups with cursor pagination`() {
            // Arrange
            val afterId = UUID.randomUUID()
            val afterCursor = CursorEncoder.encodeCursor(afterId as UUID)
            val group1 = SecurityTestDataBuilders.group(id = UUID.randomUUID(), name = "Group One")
            whenever(groupRepository.findAll(PaginationConstants.DEFAULT_PAGE_SIZE + 1, afterId))
                .thenReturn(listOf(group1))

            // Act
            val result = groupManagementService.listGroups(after = afterCursor)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.nodes).hasSize(1)
            verify(groupRepository).findAll(PaginationConstants.DEFAULT_PAGE_SIZE + 1, afterId)
        }

        @Test
        fun `should indicate has more when results exceed page size`() {
            // Arrange
            val groups =
                (1..21).map {
                    SecurityTestDataBuilders.group(
                        id = UUID.randomUUID(),
                        name = "Group $it",
                    )
                }
            whenever(groupRepository.findAll(PaginationConstants.DEFAULT_PAGE_SIZE + 1, null))
                .thenReturn(groups)

            // Act
            val result = groupManagementService.listGroups(after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.nodes).hasSize(PaginationConstants.DEFAULT_PAGE_SIZE)
            assertThat(result.pageInfo.hasNextPage).isTrue()
        }

        @Test
        fun `should return empty connection when no groups exist`() {
            // Arrange
            whenever(groupRepository.findAll(PaginationConstants.DEFAULT_PAGE_SIZE + 1, null))
                .thenReturn(emptyList())

            // Act
            val result = groupManagementService.listGroups(after = null)

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
                groupManagementService.listGroups(after = invalidCursor)
            }
            verify(groupRepository, never()).findAll(any(), any())
        }
    }

    @Nested
    @DisplayName("Search Groups")
    inner class SearchGroupsTests {
        @Test
        fun `should search groups by name`() {
            // Arrange
            val query = "admin"
            val group1 = SecurityTestDataBuilders.group(id = UUID.randomUUID(), name = "Admin Group")
            whenever(groupRepository.searchByName(query, PaginationConstants.DEFAULT_PAGE_SIZE + 1, null))
                .thenReturn(listOf(group1))

            // Act
            val result = groupManagementService.searchGroups(query, after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.nodes).hasSize(1)
            assertThat(result.nodes.first().name).isEqualTo("Admin Group")
            verify(groupRepository).searchByName(query, PaginationConstants.DEFAULT_PAGE_SIZE + 1, null)
        }

        @Test
        fun `should search groups with pagination`() {
            // Arrange
            val query = "test"
            val afterId = UUID.randomUUID()
            val afterCursor = CursorEncoder.encodeCursor(afterId as UUID)
            val group1 = SecurityTestDataBuilders.group(id = UUID.randomUUID(), name = "Test Group")
            whenever(groupRepository.searchByName(query, PaginationConstants.DEFAULT_PAGE_SIZE + 1, afterId))
                .thenReturn(listOf(group1))

            // Act
            val result = groupManagementService.searchGroups(query, after = afterCursor)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.nodes).hasSize(1)
            verify(groupRepository).searchByName(query, PaginationConstants.DEFAULT_PAGE_SIZE + 1, afterId)
        }

        @Test
        fun `should return empty connection when no groups match search`() {
            // Arrange
            val query = "nonexistent"
            whenever(groupRepository.searchByName(query, PaginationConstants.DEFAULT_PAGE_SIZE + 1, null))
                .thenReturn(emptyList())

            // Act
            val result = groupManagementService.searchGroups(query, after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.nodes).isEmpty()
            assertThat(result.pageInfo.hasNextPage).isFalse()
        }

        @Test
        fun `should throw exception for invalid cursor in search`() {
            // Arrange
            val query = "test"
            val invalidCursor = "invalid-cursor"

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                groupManagementService.searchGroups(query, after = invalidCursor)
            }
            verify(groupRepository, never()).searchByName(any(), any(), any())
        }
    }

    @Nested
    @DisplayName("Create Group")
    inner class CreateGroupTests {
        @Test
        fun `should create group successfully`() {
            // Arrange
            val command =
                GroupManagement.CreateGroupCommand(
                    name = "New Group",
                    description = "Group description",
                )
            val savedEntity =
                SecurityTestDataBuilders.group(
                    id = UUID.randomUUID(),
                    name = "New Group",
                    description = "Group description",
                )
            whenever(groupRepository.save(any())).thenReturn(savedEntity)

            // Act
            val result = groupManagementService.createGroup(command)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.name).isEqualTo("New Group")
            assertThat(result.description).isEqualTo("Group description")
            verify(groupRepository).save(any())
        }

        @Test
        fun `should create group without description`() {
            // Arrange
            val command = GroupManagement.CreateGroupCommand(name = "New Group")
            val savedEntity =
                SecurityTestDataBuilders.group(
                    id = UUID.randomUUID(),
                    name = "New Group",
                    description = null,
                )
            whenever(groupRepository.save(any())).thenReturn(savedEntity)

            // Act
            val result = groupManagementService.createGroup(command)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.name).isEqualTo("New Group")
            assertThat(result.description).isNull()
            verify(groupRepository).save(any())
        }
    }

    @Nested
    @DisplayName("Update Group")
    inner class UpdateGroupTests {
        @Test
        fun `should update group successfully`() {
            // Arrange
            val groupId = UUID.randomUUID()
            val existingEntity =
                SecurityTestDataBuilders.group(
                    id = groupId,
                    name = "Old Name",
                    description = "Old Description",
                )
            val command =
                GroupManagement.UpdateGroupCommand(
                    groupId = groupId,
                    name = "New Name",
                    description = "New Description",
                )
            val updatedEntity =
                SecurityTestDataBuilders.group(
                    id = groupId,
                    name = "New Name",
                    description = "New Description",
                )
            whenever(groupRepository.findById(groupId)).thenReturn(Optional.of(existingEntity))
            whenever(groupRepository.update(any())).thenReturn(updatedEntity)

            // Act
            val result = groupManagementService.updateGroup(command)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.id).isEqualTo(groupId)
            assertThat(result.name).isEqualTo("New Name")
            assertThat(result.description).isEqualTo("New Description")
            verify(groupRepository).findById(groupId)
            verify(groupRepository).update(any())
        }

        @Test
        fun `should throw exception when group not found`() {
            // Arrange
            val groupId = UUID.randomUUID()
            val command =
                GroupManagement.UpdateGroupCommand(
                    groupId = groupId,
                    name = "New Name",
                )
            whenever(groupRepository.findById(groupId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                groupManagementService.updateGroup(command)
            }.also { exception ->
                assertThat(exception.message).contains("Group not found")
            }
            verify(groupRepository).findById(groupId)
            verify(groupRepository, never()).update(any())
        }
    }

    @Nested
    @DisplayName("Delete Group")
    inner class DeleteGroupTests {
        @Test
        fun `should delete group successfully`() {
            // Arrange
            val groupId = UUID.randomUUID()

            // Act
            groupManagementService.deleteGroup(groupId)

            // Assert
            verify(groupRepository).deleteById(groupId)
        }

        @Test
        fun `should delete group even if not found`() {
            // Arrange
            val groupId = UUID.randomUUID()
            // Note: deleteById doesn't throw exception if not found, it just does nothing

            // Act
            groupManagementService.deleteGroup(groupId)

            // Assert
            verify(groupRepository).deleteById(groupId)
        }
    }
}
