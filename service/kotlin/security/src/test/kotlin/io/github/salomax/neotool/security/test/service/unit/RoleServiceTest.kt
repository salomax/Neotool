package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.security.domain.rbac.Role
import io.github.salomax.neotool.security.repo.RoleRepository
import io.github.salomax.neotool.security.service.management.RoleService
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
import java.time.Instant
import java.util.Optional
import java.util.UUID

@DisplayName("RoleService Unit Tests")
class RoleServiceTest {
    private lateinit var roleRepository: RoleRepository
    private lateinit var roleService: RoleService

    @BeforeEach
    fun setUp() {
        roleRepository = mock()
        roleService = RoleService(roleRepository)
    }

    @Nested
    @DisplayName("Find Operations")
    inner class FindOperationsTests {
        @Test
        fun `should find role by id`() {
            // Arrange
            val roleId = UUID.randomUUID()
            val roleEntity = SecurityTestDataBuilders.role(id = roleId, name = "admin")
            whenever(roleRepository.findById(roleId)).thenReturn(Optional.of(roleEntity))

            // Act
            val result = roleService.findById(roleId)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result?.id).isEqualTo(roleId)
            assertThat(result?.name).isEqualTo("admin")
            verify(roleRepository).findById(roleId)
        }

        @Test
        fun `should return null when role not found by id`() {
            // Arrange
            val roleId = UUID.randomUUID()
            whenever(roleRepository.findById(roleId)).thenReturn(Optional.empty())

            // Act
            val result = roleService.findById(roleId)

            // Assert
            assertThat(result).isNull()
            verify(roleRepository).findById(roleId)
        }

        @Test
        fun `should find role by name`() {
            // Arrange
            val roleName = "admin"
            val roleEntity = SecurityTestDataBuilders.role(id = UUID.randomUUID(), name = roleName)
            whenever(roleRepository.findByName(roleName)).thenReturn(Optional.of(roleEntity))

            // Act
            val result = roleService.findByName(roleName)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result?.name).isEqualTo(roleName)
            verify(roleRepository).findByName(roleName)
        }

        @Test
        fun `should return null when role not found by name`() {
            // Arrange
            val roleName = "nonexistent"
            whenever(roleRepository.findByName(roleName)).thenReturn(Optional.empty())

            // Act
            val result = roleService.findByName(roleName)

            // Assert
            assertThat(result).isNull()
            verify(roleRepository).findByName(roleName)
        }

        @Test
        fun `should find all roles`() {
            // Arrange
            val role1 = SecurityTestDataBuilders.role(id = UUID.randomUUID(), name = "admin")
            val role2 = SecurityTestDataBuilders.role(id = UUID.randomUUID(), name = "user")
            whenever(roleRepository.findAll()).thenReturn(listOf(role1, role2))

            // Act
            val result = roleService.findAll()

            // Assert
            assertThat(result).hasSize(2)
            assertThat(result.map { it.name }).containsExactlyInAnyOrder("admin", "user")
            verify(roleRepository).findAll()
        }

        @Test
        fun `should return empty list when no roles exist`() {
            // Arrange
            whenever(roleRepository.findAll()).thenReturn(emptyList())

            // Act
            val result = roleService.findAll()

            // Assert
            assertThat(result).isEmpty()
            verify(roleRepository).findAll()
        }
    }

    @Nested
    @DisplayName("Create Operations")
    inner class CreateOperationsTests {
        @Test
        fun `should create role successfully`() {
            // Arrange
            val role =
                Role(
                    name = "admin",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            val savedEntity = SecurityTestDataBuilders.role(id = UUID.randomUUID(), name = "admin")
            whenever(roleRepository.save(any())).thenReturn(savedEntity)

            // Act
            val result = roleService.create(role)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.name).isEqualTo("admin")
            verify(roleRepository).save(any())
        }
    }

    @Nested
    @DisplayName("Update Operations")
    inner class UpdateOperationsTests {
        @Test
        fun `should update role successfully`() {
            // Arrange
            val roleId = UUID.randomUUID()
            val existingEntity = SecurityTestDataBuilders.role(id = roleId, name = "old-name")
            val updatedRole =
                Role(
                    id = roleId,
                    name = "new-name",
                    updatedAt = Instant.now(),
                )
            val savedEntity = SecurityTestDataBuilders.role(id = roleId, name = "new-name")

            whenever(roleRepository.findById(roleId)).thenReturn(Optional.of(existingEntity))
            whenever(roleRepository.update(any())).thenReturn(savedEntity)

            // Act
            val result = roleService.update(updatedRole)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.id).isEqualTo(roleId)
            assertThat(result.name).isEqualTo("new-name")
            verify(roleRepository).findById(roleId)
            verify(roleRepository).update(any())
        }

        @Test
        fun `should throw exception when updating role without id`() {
            // Arrange
            val role =
                Role(
                    name = "test-role",
                    updatedAt = Instant.now(),
                )

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                roleService.update(role)
            }.also { exception ->
                assertThat(exception.message).contains("Role ID is required")
            }

            verify(roleRepository, never()).findById(any())
            verify(roleRepository, never()).update(any())
        }

        @Test
        fun `should throw exception when updating non-existent role`() {
            // Arrange
            val roleId = UUID.randomUUID()
            val role =
                Role(
                    id = roleId,
                    name = "test-role",
                    updatedAt = Instant.now(),
                )
            whenever(roleRepository.findById(roleId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                roleService.update(role)
            }.also { exception ->
                assertThat(exception.message).contains("Role not found")
            }

            verify(roleRepository).findById(roleId)
            verify(roleRepository, never()).update(any())
        }
    }

    @Nested
    @DisplayName("Delete Operations")
    inner class DeleteOperationsTests {
        @Test
        fun `should delete role successfully`() {
            // Arrange
            val roleId = UUID.randomUUID()

            // Act
            roleService.delete(roleId)

            // Assert
            verify(roleRepository).deleteById(roleId)
        }
    }
}
