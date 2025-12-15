package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.security.domain.RoleManagement
import io.github.salomax.neotool.security.repo.GroupRepository
import io.github.salomax.neotool.security.repo.PermissionRepository
import io.github.salomax.neotool.security.repo.RoleRepository
import io.github.salomax.neotool.security.repo.RoleRepositoryCustom
import io.github.salomax.neotool.security.service.RoleManagementService
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

@DisplayName("RoleManagementService Error Handling Tests")
class RoleManagementServiceErrorTest {
    private lateinit var roleRepository: RoleRepository
    private lateinit var roleSearchRepository: RoleRepositoryCustom
    private lateinit var permissionRepository: PermissionRepository
    private lateinit var groupRepository: GroupRepository
    private lateinit var roleManagementService: RoleManagementService

    @BeforeEach
    fun setUp() {
        roleRepository = mock()
        roleSearchRepository = mock()
        permissionRepository = mock()
        groupRepository = mock()
        roleManagementService =
            RoleManagementService(
                roleRepository,
                roleSearchRepository,
                permissionRepository,
                groupRepository,
            )
    }

    @Nested
    @DisplayName("Update Role Error Handling")
    inner class UpdateRoleErrorTests {
        @Test
        fun `should throw exception when updating non-existent role`() {
            // Arrange
            val nonExistentRoleId = UUID.randomUUID()
            val command =
                RoleManagement.UpdateRoleCommand(
                    roleId = nonExistentRoleId,
                    name = "Updated Role",
                )
            whenever(roleRepository.findById(nonExistentRoleId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                roleManagementService.updateRole(command)
            }.also { exception ->
                assertThat(exception.message).contains("Role not found with ID: $nonExistentRoleId")
            }

            verify(roleRepository).findById(nonExistentRoleId)
            verify(roleRepository, never()).update(any())
        }
    }

    @Nested
    @DisplayName("Delete Role Error Handling")
    inner class DeleteRoleErrorTests {
        @Test
        fun `should handle delete operation gracefully when role doesn't exist`() {
            // Arrange
            val nonExistentRoleId = UUID.randomUUID()

            // Act - delete should not throw even if role doesn't exist
            roleManagementService.deleteRole(nonExistentRoleId)

            // Assert
            verify(roleRepository).deleteById(nonExistentRoleId)
        }
    }

    @Nested
    @DisplayName("Assign Permission To Role Error Handling")
    inner class AssignPermissionToRoleErrorTests {
        @Test
        fun `should throw exception when assigning permission to non-existent role`() {
            // Arrange
            val nonExistentRoleId = UUID.randomUUID()
            val permissionId = UUID.randomUUID()
            val command = RoleManagement.AssignPermissionCommand(nonExistentRoleId, permissionId)
            whenever(roleRepository.findById(nonExistentRoleId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                roleManagementService.assignPermissionToRole(command)
            }.also { exception ->
                assertThat(exception.message).contains("Role not found with ID: $nonExistentRoleId")
            }

            verify(roleRepository).findById(nonExistentRoleId)
            verify(permissionRepository, never()).findById(any())
        }

        @Test
        fun `should throw exception when assigning non-existent permission to role`() {
            // Arrange
            val roleId = UUID.randomUUID()
            val nonExistentPermissionId = UUID.randomUUID()
            val command = RoleManagement.AssignPermissionCommand(roleId, nonExistentPermissionId)
            val roleEntity = io.github.salomax.neotool.security.test.SecurityTestDataBuilders.role(id = roleId)
            whenever(roleRepository.findById(roleId)).thenReturn(Optional.of(roleEntity))
            whenever(permissionRepository.findById(nonExistentPermissionId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                roleManagementService.assignPermissionToRole(command)
            }.also { exception ->
                assertThat(exception.message).contains("Permission not found with ID: $nonExistentPermissionId")
            }

            verify(roleRepository).findById(roleId)
            verify(permissionRepository).findById(nonExistentPermissionId)
        }
    }

    @Nested
    @DisplayName("Remove Permission From Role Error Handling")
    inner class RemovePermissionFromRoleErrorTests {
        @Test
        fun `should throw exception when removing permission from non-existent role`() {
            // Arrange
            val nonExistentRoleId = UUID.randomUUID()
            val permissionId = UUID.randomUUID()
            val command = RoleManagement.RemovePermissionCommand(nonExistentRoleId, permissionId)
            whenever(roleRepository.findById(nonExistentRoleId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                roleManagementService.removePermissionFromRole(command)
            }.also { exception ->
                assertThat(exception.message).contains("Role not found with ID: $nonExistentRoleId")
            }

            verify(roleRepository).findById(nonExistentRoleId)
            verify(permissionRepository, never()).findById(any())
        }

        @Test
        fun `should throw exception when removing non-existent permission from role`() {
            // Arrange
            val roleId = UUID.randomUUID()
            val nonExistentPermissionId = UUID.randomUUID()
            val command = RoleManagement.RemovePermissionCommand(roleId, nonExistentPermissionId)
            val roleEntity = io.github.salomax.neotool.security.test.SecurityTestDataBuilders.role(id = roleId)
            whenever(roleRepository.findById(roleId)).thenReturn(Optional.of(roleEntity))
            whenever(permissionRepository.findById(nonExistentPermissionId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                roleManagementService.removePermissionFromRole(command)
            }.also { exception ->
                assertThat(exception.message).contains("Permission not found with ID: $nonExistentPermissionId")
            }

            verify(roleRepository).findById(roleId)
            verify(permissionRepository).findById(nonExistentPermissionId)
        }
    }
}
