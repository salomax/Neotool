package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.security.domain.rbac.Permission
import io.github.salomax.neotool.security.repo.PermissionRepository
import io.github.salomax.neotool.security.service.PermissionService
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

@DisplayName("PermissionService Unit Tests")
class PermissionServiceTest {
    private lateinit var permissionRepository: PermissionRepository
    private lateinit var permissionService: PermissionService

    @BeforeEach
    fun setUp() {
        permissionRepository = mock()
        permissionService = PermissionService(permissionRepository)
    }

    @Nested
    @DisplayName("Find Operations")
    inner class FindOperationsTests {
        @Test
        fun `should find permission by id`() {
            // Arrange
            val permissionId = 1
            val permissionEntity = SecurityTestDataBuilders.permission(id = permissionId, name = "transaction:read")
            whenever(permissionRepository.findById(permissionId)).thenReturn(Optional.of(permissionEntity))

            // Act
            val result = permissionService.findById(permissionId)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result?.id).isEqualTo(permissionId)
            assertThat(result?.name).isEqualTo("transaction:read")
            verify(permissionRepository).findById(permissionId)
        }

        @Test
        fun `should return null when permission not found by id`() {
            // Arrange
            val permissionId = 999
            whenever(permissionRepository.findById(permissionId)).thenReturn(Optional.empty())

            // Act
            val result = permissionService.findById(permissionId)

            // Assert
            assertThat(result).isNull()
            verify(permissionRepository).findById(permissionId)
        }

        @Test
        fun `should find permission by name`() {
            // Arrange
            val permissionName = "transaction:read"
            val permissionEntity = SecurityTestDataBuilders.permission(id = 1, name = permissionName)
            whenever(permissionRepository.findByName(permissionName)).thenReturn(Optional.of(permissionEntity))

            // Act
            val result = permissionService.findByName(permissionName)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result?.name).isEqualTo(permissionName)
            verify(permissionRepository).findByName(permissionName)
        }

        @Test
        fun `should return null when permission not found by name`() {
            // Arrange
            val permissionName = "nonexistent:action"
            whenever(permissionRepository.findByName(permissionName)).thenReturn(Optional.empty())

            // Act
            val result = permissionService.findByName(permissionName)

            // Assert
            assertThat(result).isNull()
            verify(permissionRepository).findByName(permissionName)
        }

        @Test
        fun `should find all permissions`() {
            // Arrange
            val permission1 = SecurityTestDataBuilders.permission(id = 1, name = "transaction:read")
            val permission2 = SecurityTestDataBuilders.permission(id = 2, name = "transaction:write")
            whenever(permissionRepository.findAll()).thenReturn(listOf(permission1, permission2))

            // Act
            val result = permissionService.findAll()

            // Assert
            assertThat(result).hasSize(2)
            assertThat(result.map { it.name }).containsExactlyInAnyOrder("transaction:read", "transaction:write")
            verify(permissionRepository).findAll()
        }

        @Test
        fun `should return empty list when no permissions exist`() {
            // Arrange
            whenever(permissionRepository.findAll()).thenReturn(emptyList())

            // Act
            val result = permissionService.findAll()

            // Assert
            assertThat(result).isEmpty()
            verify(permissionRepository).findAll()
        }
    }

    @Nested
    @DisplayName("Create Operations")
    inner class CreateOperationsTests {
        @Test
        fun `should create permission successfully`() {
            // Arrange
            val permission =
                Permission(
                    name = "transaction:read",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            val savedEntity = SecurityTestDataBuilders.permission(id = 1, name = "transaction:read")
            whenever(permissionRepository.save(any())).thenReturn(savedEntity)

            // Act
            val result = permissionService.create(permission)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.name).isEqualTo("transaction:read")
            verify(permissionRepository).save(any())
        }
    }

    @Nested
    @DisplayName("Update Operations")
    inner class UpdateOperationsTests {
        @Test
        fun `should update permission successfully`() {
            // Arrange
            val permissionId = 1
            val existingEntity = SecurityTestDataBuilders.permission(id = permissionId, name = "transaction:old")
            val updatedPermission =
                Permission(
                    id = permissionId,
                    name = "transaction:new",
                    updatedAt = Instant.now(),
                )
            val savedEntity = SecurityTestDataBuilders.permission(id = permissionId, name = "transaction:new")

            whenever(permissionRepository.findById(permissionId)).thenReturn(Optional.of(existingEntity))
            whenever(permissionRepository.update(any())).thenReturn(savedEntity)

            // Act
            val result = permissionService.update(updatedPermission)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.id).isEqualTo(permissionId)
            assertThat(result.name).isEqualTo("transaction:new")
            verify(permissionRepository).findById(permissionId)
            verify(permissionRepository).update(any())
        }

        @Test
        fun `should throw exception when updating permission without id`() {
            // Arrange
            val permission =
                Permission(
                    name = "transaction:read",
                    updatedAt = Instant.now(),
                )

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                permissionService.update(permission)
            }.also { exception ->
                assertThat(exception.message).contains("Permission ID is required")
            }

            verify(permissionRepository, never()).findById(any())
            verify(permissionRepository, never()).update(any())
        }

        @Test
        fun `should throw exception when updating non-existent permission`() {
            // Arrange
            val permissionId = 999
            val permission =
                Permission(
                    id = permissionId,
                    name = "transaction:read",
                    updatedAt = Instant.now(),
                )
            whenever(permissionRepository.findById(permissionId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                permissionService.update(permission)
            }.also { exception ->
                assertThat(exception.message).contains("Permission not found")
            }

            verify(permissionRepository).findById(permissionId)
            verify(permissionRepository, never()).update(any())
        }
    }

    @Nested
    @DisplayName("Delete Operations")
    inner class DeleteOperationsTests {
        @Test
        fun `should delete permission successfully`() {
            // Arrange
            val permissionId = 1

            // Act
            permissionService.delete(permissionId)

            // Assert
            verify(permissionRepository).deleteById(permissionId)
        }
    }
}
