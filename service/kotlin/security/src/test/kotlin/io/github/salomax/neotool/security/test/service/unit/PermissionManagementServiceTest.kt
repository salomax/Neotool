package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.common.graphql.pagination.CursorEncoder
import io.github.salomax.neotool.common.graphql.pagination.PaginationConstants
import io.github.salomax.neotool.security.repo.PermissionRepository
import io.github.salomax.neotool.security.service.PermissionManagementService
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

@DisplayName("PermissionManagementService Unit Tests")
class PermissionManagementServiceTest {
    private lateinit var permissionRepository: PermissionRepository
    private lateinit var permissionManagementService: PermissionManagementService

    @BeforeEach
    fun setUp() {
        permissionRepository = mock()
        permissionManagementService = PermissionManagementService(permissionRepository)
    }

    @Nested
    @DisplayName("List Permissions")
    inner class ListPermissionsTests {
        @Test
        fun `should list permissions with default page size`() {
            // Arrange
            val permission1 = SecurityTestDataBuilders.permission(id = 1, name = "permission:read")
            val permission2 = SecurityTestDataBuilders.permission(id = 2, name = "permission:write")
            whenever(permissionRepository.findAll(PaginationConstants.DEFAULT_PAGE_SIZE + 1, null))
                .thenReturn(listOf(permission1, permission2))

            // Act
            val result = permissionManagementService.listPermissions(after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(2)
            assertThat(result.pageInfo.hasNextPage).isFalse()
            verify(permissionRepository).findAll(PaginationConstants.DEFAULT_PAGE_SIZE + 1, null)
        }

        @Test
        fun `should list permissions with custom page size`() {
            // Arrange
            val first = 10
            val permission1 = SecurityTestDataBuilders.permission(id = 1, name = "permission:read")
            whenever(permissionRepository.findAll(first + 1, null))
                .thenReturn(listOf(permission1))

            // Act
            val result = permissionManagementService.listPermissions(first = first, after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(1)
            verify(permissionRepository).findAll(first + 1, null)
        }

        @Test
        fun `should enforce max page size`() {
            // Arrange
            val first = 200 // Exceeds MAX_PAGE_SIZE
            whenever(permissionRepository.findAll(PaginationConstants.MAX_PAGE_SIZE + 1, null))
                .thenReturn(emptyList())

            // Act
            val result = permissionManagementService.listPermissions(first = first, after = null)

            // Assert
            assertThat(result).isNotNull()
            verify(permissionRepository).findAll(PaginationConstants.MAX_PAGE_SIZE + 1, null)
        }

        @Test
        fun `should list permissions with cursor pagination`() {
            // Arrange
            val afterId = 5
            val afterCursor = CursorEncoder.encodeCursor(afterId)
            val permission1 = SecurityTestDataBuilders.permission(id = 6, name = "permission:read")
            whenever(permissionRepository.findAll(PaginationConstants.DEFAULT_PAGE_SIZE + 1, afterId))
                .thenReturn(listOf(permission1))

            // Act
            val result = permissionManagementService.listPermissions(after = afterCursor)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(1)
            verify(permissionRepository).findAll(PaginationConstants.DEFAULT_PAGE_SIZE + 1, afterId)
        }

        @Test
        fun `should indicate has more when results exceed page size`() {
            // Arrange
            val permissions =
                (1..21).map {
                    SecurityTestDataBuilders.permission(
                        id = it,
                        name = "permission:$it",
                    )
                }
            whenever(permissionRepository.findAll(PaginationConstants.DEFAULT_PAGE_SIZE + 1, null))
                .thenReturn(permissions)

            // Act
            val result = permissionManagementService.listPermissions(after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(PaginationConstants.DEFAULT_PAGE_SIZE)
            assertThat(result.pageInfo.hasNextPage).isTrue()
        }

        @Test
        fun `should return empty connection when no permissions exist`() {
            // Arrange
            whenever(permissionRepository.findAll(PaginationConstants.DEFAULT_PAGE_SIZE + 1, null))
                .thenReturn(emptyList())

            // Act
            val result = permissionManagementService.listPermissions(after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges).isEmpty()
            assertThat(result.pageInfo.hasNextPage).isFalse()
        }

        @Test
        fun `should throw exception for invalid cursor`() {
            // Arrange
            val invalidCursor = "invalid-cursor"

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                permissionManagementService.listPermissions(after = invalidCursor)
            }
            verify(permissionRepository, never()).findAll(any(), any())
        }
    }

    @Nested
    @DisplayName("Search Permissions")
    inner class SearchPermissionsTests {
        @Test
        fun `should search permissions by name`() {
            // Arrange
            val query = "read"
            val permission1 = SecurityTestDataBuilders.permission(id = 1, name = "permission:read")
            whenever(permissionRepository.searchByName(query, PaginationConstants.DEFAULT_PAGE_SIZE + 1, null))
                .thenReturn(listOf(permission1))

            // Act
            val result = permissionManagementService.searchPermissions(query, after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(1)
            assertThat(result.edges.first().node.name).isEqualTo("permission:read")
            verify(permissionRepository).searchByName(query, PaginationConstants.DEFAULT_PAGE_SIZE + 1, null)
        }

        @Test
        fun `should search permissions with pagination`() {
            // Arrange
            val query = "write"
            val afterId = 5
            val afterCursor = CursorEncoder.encodeCursor(afterId)
            val permission1 = SecurityTestDataBuilders.permission(id = 6, name = "permission:write")
            whenever(permissionRepository.searchByName(query, PaginationConstants.DEFAULT_PAGE_SIZE + 1, afterId))
                .thenReturn(listOf(permission1))

            // Act
            val result = permissionManagementService.searchPermissions(query, after = afterCursor)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges.map { it.node }).hasSize(1)
            verify(permissionRepository).searchByName(query, PaginationConstants.DEFAULT_PAGE_SIZE + 1, afterId)
        }

        @Test
        fun `should return empty connection when no permissions match search`() {
            // Arrange
            val query = "nonexistent"
            whenever(permissionRepository.searchByName(query, PaginationConstants.DEFAULT_PAGE_SIZE + 1, null))
                .thenReturn(emptyList())

            // Act
            val result = permissionManagementService.searchPermissions(query, after = null)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.edges).isEmpty()
            assertThat(result.pageInfo.hasNextPage).isFalse()
        }

        @Test
        fun `should throw exception for invalid cursor in search`() {
            // Arrange
            val query = "test"
            val invalidCursor = "invalid-cursor"

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                permissionManagementService.searchPermissions(query, after = invalidCursor)
            }
            verify(permissionRepository, never()).searchByName(any(), any(), any())
        }
    }
}
