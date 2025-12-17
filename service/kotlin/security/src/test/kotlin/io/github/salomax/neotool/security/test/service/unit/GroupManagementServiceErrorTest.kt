package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.security.domain.GroupManagement
import io.github.salomax.neotool.security.repo.GroupMembershipRepository
import io.github.salomax.neotool.security.repo.GroupRepository
import io.github.salomax.neotool.security.repo.GroupRepositoryCustom
import io.github.salomax.neotool.security.repo.GroupRoleAssignmentRepository
import io.github.salomax.neotool.security.repo.RoleRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.GroupManagementService
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

@DisplayName("GroupManagementService Error Handling Tests")
class GroupManagementServiceErrorTest {
    private lateinit var groupRepository: GroupRepository
    private lateinit var groupSearchRepository: GroupRepositoryCustom
    private lateinit var groupMembershipRepository: GroupMembershipRepository
    private lateinit var groupRoleAssignmentRepository: GroupRoleAssignmentRepository
    private lateinit var roleRepository: RoleRepository
    private lateinit var userRepository: UserRepository
    private lateinit var groupManagementService: GroupManagementService

    @BeforeEach
    fun setUp() {
        groupRepository = mock()
        groupSearchRepository = mock()
        groupMembershipRepository = mock()
        groupRoleAssignmentRepository = mock()
        roleRepository = mock()
        userRepository = mock()
        groupManagementService =
            GroupManagementService(
                groupRepository,
                groupSearchRepository,
                groupMembershipRepository,
                groupRoleAssignmentRepository,
                roleRepository,
                userRepository,
            )
    }

    @Nested
    @DisplayName("Update Group Error Handling")
    inner class UpdateGroupErrorTests {
        @Test
        fun `should throw exception when updating non-existent group`() {
            // Arrange
            val nonExistentGroupId = UUID.randomUUID()
            val command =
                GroupManagement.UpdateGroupCommand(
                    groupId = nonExistentGroupId,
                    name = "Updated Group",
                )
            whenever(groupRepository.findById(nonExistentGroupId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                groupManagementService.updateGroup(command)
            }.also { exception ->
                assertThat(exception.message).contains("Group not found with ID: $nonExistentGroupId")
            }

            verify(groupRepository).findById(nonExistentGroupId)
            verify(groupRepository, never()).update(any())
        }
    }

    @Nested
    @DisplayName("Delete Group Error Handling")
    inner class DeleteGroupErrorTests {
        @Test
        fun `should handle delete operation gracefully when group doesn't exist`() {
            // Arrange
            val nonExistentGroupId = UUID.randomUUID()

            // Act - delete should not throw even if group doesn't exist
            groupManagementService.deleteGroup(nonExistentGroupId)

            // Assert
            verify(groupRepository).deleteById(nonExistentGroupId)
        }
    }

    @Nested
    @DisplayName("Assign Role To Group Error Handling")
    inner class AssignRoleToGroupErrorTests {
        @Test
        fun `should throw exception when assigning role to non-existent group`() {
            // Arrange
            val nonExistentGroupId = UUID.randomUUID()
            val roleId = UUID.randomUUID()
            val command = GroupManagement.AssignRoleToGroupCommand(nonExistentGroupId, roleId)
            whenever(groupRepository.findById(nonExistentGroupId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                groupManagementService.assignRoleToGroup(command)
            }.also { exception ->
                assertThat(exception.message).contains("Group not found with ID: $nonExistentGroupId")
            }

            verify(groupRepository).findById(nonExistentGroupId)
            verify(roleRepository, never()).findById(any())
        }

        @Test
        fun `should throw exception when assigning non-existent role to group`() {
            // Arrange
            val groupId = UUID.randomUUID()
            val nonExistentRoleId = UUID.randomUUID()
            val command = GroupManagement.AssignRoleToGroupCommand(groupId, nonExistentRoleId)
            val groupEntity = io.github.salomax.neotool.security.test.SecurityTestDataBuilders.group(id = groupId)
            whenever(groupRepository.findById(groupId)).thenReturn(Optional.of(groupEntity))
            whenever(roleRepository.findById(nonExistentRoleId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                groupManagementService.assignRoleToGroup(command)
            }.also { exception ->
                assertThat(exception.message).contains("Role not found with ID: $nonExistentRoleId")
            }

            verify(groupRepository).findById(groupId)
            verify(roleRepository).findById(nonExistentRoleId)
        }
    }

    @Nested
    @DisplayName("Remove Role From Group Error Handling")
    inner class RemoveRoleFromGroupErrorTests {
        @Test
        fun `should throw exception when removing role from non-existent group`() {
            // Arrange
            val nonExistentGroupId = UUID.randomUUID()
            val roleId = UUID.randomUUID()
            val command = GroupManagement.RemoveRoleFromGroupCommand(nonExistentGroupId, roleId)
            whenever(groupRepository.findById(nonExistentGroupId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                groupManagementService.removeRoleFromGroup(command)
            }.also { exception ->
                assertThat(exception.message).contains("Group not found with ID: $nonExistentGroupId")
            }

            verify(groupRepository).findById(nonExistentGroupId)
            verify(roleRepository, never()).findById(any())
        }

        @Test
        fun `should throw exception when removing non-existent role from group`() {
            // Arrange
            val groupId = UUID.randomUUID()
            val nonExistentRoleId = UUID.randomUUID()
            val command = GroupManagement.RemoveRoleFromGroupCommand(groupId, nonExistentRoleId)
            val groupEntity = io.github.salomax.neotool.security.test.SecurityTestDataBuilders.group(id = groupId)
            whenever(groupRepository.findById(groupId)).thenReturn(Optional.of(groupEntity))
            whenever(roleRepository.findById(nonExistentRoleId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                groupManagementService.removeRoleFromGroup(command)
            }.also { exception ->
                assertThat(exception.message).contains("Role not found with ID: $nonExistentRoleId")
            }

            verify(groupRepository).findById(groupId)
            verify(roleRepository).findById(nonExistentRoleId)
        }
    }

    @Nested
    @DisplayName("Create Group Error Handling")
    inner class CreateGroupErrorTests {
        @Test
        fun `should throw exception when creating group with non-existent user IDs`() {
            // Arrange
            val nonExistentUserId1 = UUID.randomUUID()
            val nonExistentUserId2 = UUID.randomUUID()
            val command =
                GroupManagement.CreateGroupCommand(
                    name = "Test Group",
                    userIds = listOf(nonExistentUserId1, nonExistentUserId2),
                )
            val groupEntity =
                io.github.salomax.neotool.security.test.SecurityTestDataBuilders.group(
                    name = "Test Group",
                )
            whenever(groupRepository.save(any())).thenReturn(groupEntity)
            whenever(userRepository.findByIdIn(listOf(nonExistentUserId1, nonExistentUserId2))).thenReturn(emptyList())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                groupManagementService.createGroup(command)
            }.also { exception ->
                assertThat(exception.message).contains("One or more users not found")
                assertThat(exception.message).contains(nonExistentUserId1.toString())
                assertThat(exception.message).contains(nonExistentUserId2.toString())
            }

            verify(groupRepository).save(any())
            verify(userRepository).findByIdIn(listOf(nonExistentUserId1, nonExistentUserId2))
        }
    }
}
