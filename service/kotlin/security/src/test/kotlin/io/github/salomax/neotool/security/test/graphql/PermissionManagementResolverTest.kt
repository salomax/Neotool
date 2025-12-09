package io.github.salomax.neotool.security.test.graphql

import io.github.salomax.neotool.security.domain.rbac.Role
import io.github.salomax.neotool.security.graphql.mapper.PermissionManagementMapper
import io.github.salomax.neotool.security.graphql.resolver.PermissionManagementResolver
import io.github.salomax.neotool.security.service.PermissionManagementService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

@DisplayName("PermissionManagementResolver Batch Methods Tests")
class PermissionManagementResolverTest {
    private lateinit var permissionManagementService: PermissionManagementService
    private lateinit var mapper: PermissionManagementMapper
    private lateinit var permissionManagementResolver: PermissionManagementResolver

    @BeforeEach
    fun setUp() {
        permissionManagementService = mock()
        mapper = mock()
        permissionManagementResolver =
            PermissionManagementResolver(permissionManagementService, mapper)
    }

    @Nested
    @DisplayName("resolvePermissionRolesBatch")
    inner class ResolvePermissionRolesBatchTests {
        @Test
        fun `should return roles for multiple permissions`() {
            // Arrange
            val permissionId1 = 10
            val permissionId2 = 20
            val role1 = Role(id = 1, name = "admin", createdAt = Instant.now(), updatedAt = Instant.now())
            val role2 = Role(id = 2, name = "editor", createdAt = Instant.now(), updatedAt = Instant.now())
            whenever(mapper.toPermissionId(permissionId1.toString())).thenReturn(permissionId1)
            whenever(mapper.toPermissionId(permissionId2.toString())).thenReturn(permissionId2)
            whenever(permissionManagementService.getPermissionRolesBatch(any<List<Int>>()))
                .thenReturn(
                    mapOf(
                        permissionId1 to listOf(role1),
                        permissionId2 to listOf(role2),
                    ),
                )

            // Act
            val result =
                permissionManagementResolver.resolvePermissionRolesBatch(
                    listOf(permissionId1.toString(), permissionId2.toString()),
                )

            // Assert
            assertThat(result).hasSize(2)
            assertThat(result[permissionId1.toString()]).hasSize(1)
            assertThat(result[permissionId1.toString()]!![0].name).isEqualTo("admin")
            assertThat(result[permissionId2.toString()]).hasSize(1)
            assertThat(result[permissionId2.toString()]!![0].name).isEqualTo("editor")
            verify(permissionManagementService).getPermissionRolesBatch(any<List<Int>>())
        }

        @Test
        fun `should return empty map for empty permission list`() {
            // Act
            val result = permissionManagementResolver.resolvePermissionRolesBatch(emptyList())

            // Assert
            assertThat(result).isEmpty()
        }

        @Test
        fun `should filter out invalid permission IDs`() {
            // Arrange
            val permissionId1 = 10
            val invalidId = "not-a-number"
            val role1 = Role(id = 1, name = "admin", createdAt = Instant.now(), updatedAt = Instant.now())
            whenever(mapper.toPermissionId(permissionId1.toString())).thenReturn(permissionId1)
            whenever(mapper.toPermissionId(invalidId)).thenThrow(IllegalArgumentException("Invalid permission ID"))
            whenever(permissionManagementService.getPermissionRolesBatch(any<List<Int>>()))
                .thenReturn(mapOf(permissionId1 to listOf(role1)))

            // Act
            val result =
                permissionManagementResolver.resolvePermissionRolesBatch(
                    listOf(
                        permissionId1.toString(),
                        invalidId,
                    ),
                )

            // Assert
            assertThat(result).hasSize(1)
            assertThat(result[permissionId1.toString()]).hasSize(1)
            assertThat(result).doesNotContainKey(invalidId)
        }

        @Test
        fun `should preserve order of requested permission IDs`() {
            // Arrange
            val permissionId1 = 10
            val permissionId2 = 20
            val permissionId3 = 30
            val role1 = Role(id = 1, name = "admin", createdAt = Instant.now(), updatedAt = Instant.now())
            whenever(mapper.toPermissionId(permissionId1.toString())).thenReturn(permissionId1)
            whenever(mapper.toPermissionId(permissionId2.toString())).thenReturn(permissionId2)
            whenever(mapper.toPermissionId(permissionId3.toString())).thenReturn(permissionId3)
            whenever(permissionManagementService.getPermissionRolesBatch(any<List<Int>>()))
                .thenReturn(
                    mapOf(
                        permissionId1 to listOf(role1),
                        permissionId2 to emptyList(),
                        permissionId3 to listOf(role1),
                    ),
                )

            // Act
            val result =
                permissionManagementResolver.resolvePermissionRolesBatch(
                    listOf(permissionId1.toString(), permissionId2.toString(), permissionId3.toString()),
                )

            // Assert
            assertThat(result.keys.toList())
                .containsExactly(
                    permissionId1.toString(),
                    permissionId2.toString(),
                    permissionId3.toString(),
                )
        }
    }
}
