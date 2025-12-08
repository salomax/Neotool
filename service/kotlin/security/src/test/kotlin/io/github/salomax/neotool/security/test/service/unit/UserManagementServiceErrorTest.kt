package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.security.domain.UserManagement
import io.github.salomax.neotool.security.repo.GroupMembershipRepository
import io.github.salomax.neotool.security.repo.GroupRepository
import io.github.salomax.neotool.security.repo.RoleAssignmentRepository
import io.github.salomax.neotool.security.repo.RoleRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.repo.UserRepositoryCustom
import io.github.salomax.neotool.security.service.UserManagementService
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

@DisplayName("UserManagementService Error Handling Tests")
class UserManagementServiceErrorTest {
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
    @DisplayName("Enable User Error Handling")
    inner class EnableUserErrorTests {
        @Test
        fun `should throw exception when enabling non-existent user`() {
            // Arrange
            val nonExistentUserId = UUID.randomUUID()
            whenever(userRepository.findById(nonExistentUserId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                userManagementService.enableUser(nonExistentUserId)
            }.also { exception ->
                assertThat(exception.message).contains("User not found with ID: $nonExistentUserId")
            }

            verify(userRepository).findById(nonExistentUserId)
        }
    }

    @Nested
    @DisplayName("Disable User Error Handling")
    inner class DisableUserErrorTests {
        @Test
        fun `should throw exception when disabling non-existent user`() {
            // Arrange
            val nonExistentUserId = UUID.randomUUID()
            whenever(userRepository.findById(nonExistentUserId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                userManagementService.disableUser(nonExistentUserId)
            }.also { exception ->
                assertThat(exception.message).contains("User not found with ID: $nonExistentUserId")
            }

            verify(userRepository).findById(nonExistentUserId)
        }
    }

    @Nested
    @DisplayName("Assign Role To User Error Handling")
    inner class AssignRoleToUserErrorTests {
        @Test
        fun `should throw exception when assigning role to non-existent user`() {
            // Arrange
            val nonExistentUserId = UUID.randomUUID()
            val roleId = 1
            val command = UserManagement.AssignRoleToUserCommand(nonExistentUserId, roleId)
            whenever(userRepository.findById(nonExistentUserId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                userManagementService.assignRoleToUser(command)
            }.also { exception ->
                assertThat(exception.message).contains("User not found with ID: $nonExistentUserId")
            }

            verify(userRepository).findById(nonExistentUserId)
            verify(roleRepository, never()).findById(any())
        }

        @Test
        fun `should throw exception when assigning role to user with non-existent role`() {
            // Arrange
            val userId = UUID.randomUUID()
            val nonExistentRoleId = 999
            val command = UserManagement.AssignRoleToUserCommand(userId, nonExistentRoleId)
            val userEntity = io.github.salomax.neotool.security.test.SecurityTestDataBuilders.user(id = userId)
            whenever(userRepository.findById(userId)).thenReturn(Optional.of(userEntity))
            whenever(roleRepository.findById(nonExistentRoleId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                userManagementService.assignRoleToUser(command)
            }.also { exception ->
                assertThat(exception.message).contains("Role not found with ID: $nonExistentRoleId")
            }

            verify(userRepository).findById(userId)
            verify(roleRepository).findById(nonExistentRoleId)
        }
    }

    @Nested
    @DisplayName("Remove Role From User Error Handling")
    inner class RemoveRoleFromUserErrorTests {
        @Test
        fun `should throw exception when removing role from non-existent user`() {
            // Arrange
            val nonExistentUserId = UUID.randomUUID()
            val roleId = 1
            val command = UserManagement.RemoveRoleFromUserCommand(nonExistentUserId, roleId)
            whenever(userRepository.findById(nonExistentUserId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                userManagementService.removeRoleFromUser(command)
            }.also { exception ->
                assertThat(exception.message).contains("User not found with ID: $nonExistentUserId")
            }

            verify(userRepository).findById(nonExistentUserId)
            verify(roleRepository, never()).findById(any())
        }

        @Test
        fun `should throw exception when removing non-existent role from user`() {
            // Arrange
            val userId = UUID.randomUUID()
            val nonExistentRoleId = 999
            val command = UserManagement.RemoveRoleFromUserCommand(userId, nonExistentRoleId)
            val userEntity = io.github.salomax.neotool.security.test.SecurityTestDataBuilders.user(id = userId)
            whenever(userRepository.findById(userId)).thenReturn(Optional.of(userEntity))
            whenever(roleRepository.findById(nonExistentRoleId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                userManagementService.removeRoleFromUser(command)
            }.also { exception ->
                assertThat(exception.message).contains("Role not found with ID: $nonExistentRoleId")
            }

            verify(userRepository).findById(userId)
            verify(roleRepository).findById(nonExistentRoleId)
        }
    }

    @Nested
    @DisplayName("Assign Group To User Error Handling")
    inner class AssignGroupToUserErrorTests {
        @Test
        fun `should throw exception when assigning group to non-existent user`() {
            // Arrange
            val nonExistentUserId = UUID.randomUUID()
            val groupId = UUID.randomUUID()
            val command = UserManagement.AssignGroupToUserCommand(nonExistentUserId, groupId)
            whenever(userRepository.findById(nonExistentUserId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                userManagementService.assignGroupToUser(command)
            }.also { exception ->
                assertThat(exception.message).contains("User not found with ID: $nonExistentUserId")
            }

            verify(userRepository).findById(nonExistentUserId)
            verify(groupRepository, never()).findById(any())
        }

        @Test
        fun `should throw exception when assigning non-existent group to user`() {
            // Arrange
            val userId = UUID.randomUUID()
            val nonExistentGroupId = UUID.randomUUID()
            val command = UserManagement.AssignGroupToUserCommand(userId, nonExistentGroupId)
            val userEntity = io.github.salomax.neotool.security.test.SecurityTestDataBuilders.user(id = userId)
            whenever(userRepository.findById(userId)).thenReturn(Optional.of(userEntity))
            whenever(groupRepository.findById(nonExistentGroupId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                userManagementService.assignGroupToUser(command)
            }.also { exception ->
                assertThat(exception.message).contains("Group not found with ID: $nonExistentGroupId")
            }

            verify(userRepository).findById(userId)
            verify(groupRepository).findById(nonExistentGroupId)
        }
    }

    @Nested
    @DisplayName("Remove Group From User Error Handling")
    inner class RemoveGroupFromUserErrorTests {
        @Test
        fun `should throw exception when removing group from non-existent user`() {
            // Arrange
            val nonExistentUserId = UUID.randomUUID()
            val groupId = UUID.randomUUID()
            val command = UserManagement.RemoveGroupFromUserCommand(nonExistentUserId, groupId)
            whenever(userRepository.findById(nonExistentUserId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                userManagementService.removeGroupFromUser(command)
            }.also { exception ->
                assertThat(exception.message).contains("User not found with ID: $nonExistentUserId")
            }

            verify(userRepository).findById(nonExistentUserId)
            verify(groupRepository, never()).findById(any())
        }

        @Test
        fun `should throw exception when removing non-existent group from user`() {
            // Arrange
            val userId = UUID.randomUUID()
            val nonExistentGroupId = UUID.randomUUID()
            val command = UserManagement.RemoveGroupFromUserCommand(userId, nonExistentGroupId)
            val userEntity = io.github.salomax.neotool.security.test.SecurityTestDataBuilders.user(id = userId)
            whenever(userRepository.findById(userId)).thenReturn(Optional.of(userEntity))
            whenever(groupRepository.findById(nonExistentGroupId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                userManagementService.removeGroupFromUser(command)
            }.also { exception ->
                assertThat(exception.message).contains("Group not found with ID: $nonExistentGroupId")
            }

            verify(userRepository).findById(userId)
            verify(groupRepository).findById(nonExistentGroupId)
        }
    }
}
