package io.github.salomax.neotool.security.test.graphql

import io.github.salomax.neotool.security.domain.rbac.Permission
import io.github.salomax.neotool.security.graphql.dto.PermissionDTO
import io.github.salomax.neotool.security.graphql.mapper.RoleManagementMapper
import io.github.salomax.neotool.security.graphql.resolver.RoleManagementResolver
import io.github.salomax.neotool.security.service.RoleManagementService
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

@DisplayName("RoleManagementResolver Batch Methods Tests")
class RoleManagementResolverTest {
    private lateinit var roleManagementService: RoleManagementService
    private lateinit var mapper: RoleManagementMapper
    private lateinit var roleManagementResolver: RoleManagementResolver

    @BeforeEach
    fun setUp() {
        roleManagementService = mock()
        mapper = mock()
        roleManagementResolver = RoleManagementResolver(roleManagementService, mapper)
    }

    @Nested
    @DisplayName("resolveRolePermissionsBatch")
    inner class ResolveRolePermissionsBatchTests {
        @Test
        fun `should return permissions for multiple roles`() {
            // Arrange
            val roleId1 = 1
            val roleId2 = 2
            val permission1 =
                Permission(
                    id = 10,
                    name = "transaction:read",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            val permission2 =
                Permission(
                    id = 20,
                    name = "transaction:write",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            whenever(mapper.toRoleId(roleId1.toString())).thenReturn(roleId1)
            whenever(mapper.toRoleId(roleId2.toString())).thenReturn(roleId2)
            whenever(roleManagementService.listRolePermissionsBatch(any<List<Int>>()))
                .thenReturn(
                    mapOf(
                        roleId1 to listOf(permission1),
                        roleId2 to listOf(permission2),
                    ),
                )
            whenever(mapper.toPermissionDTOList(any<List<Permission>>())).thenAnswer { invocation ->
                val permissions = invocation.getArgument<List<Permission>>(0)
                permissions.map { PermissionDTO(id = it.id!!.toString(), name = it.name) }
            }

            // Act
            val result =
                roleManagementResolver.resolveRolePermissionsBatch(
                    listOf(
                        roleId1.toString(),
                        roleId2.toString(),
                    ),
                )

            // Assert
            assertThat(result).hasSize(2)
            assertThat(result[roleId1.toString()]).hasSize(1)
            assertThat(result[roleId1.toString()]!![0].name).isEqualTo("transaction:read")
            assertThat(result[roleId2.toString()]).hasSize(1)
            assertThat(result[roleId2.toString()]!![0].name).isEqualTo("transaction:write")
            verify(roleManagementService).listRolePermissionsBatch(any<List<Int>>())
        }

        @Test
        fun `should return empty map for empty role list`() {
            // Act
            val result = roleManagementResolver.resolveRolePermissionsBatch(emptyList())

            // Assert
            assertThat(result).isEmpty()
        }

        @Test
        fun `should filter out invalid role IDs`() {
            // Arrange
            val roleId1 = 1
            val invalidId = "not-a-number"
            val permission1 =
                Permission(
                    id = 10,
                    name = "transaction:read",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            whenever(mapper.toRoleId(roleId1.toString())).thenReturn(roleId1)
            whenever(mapper.toRoleId(invalidId)).thenThrow(IllegalArgumentException("Invalid role ID"))
            whenever(roleManagementService.listRolePermissionsBatch(any<List<Int>>()))
                .thenReturn(mapOf(roleId1 to listOf(permission1)))
            whenever(mapper.toPermissionDTOList(any<List<Permission>>())).thenAnswer { invocation ->
                val permissions = invocation.getArgument<List<Permission>>(0)
                permissions.map { PermissionDTO(id = it.id!!.toString(), name = it.name) }
            }

            // Act
            val result = roleManagementResolver.resolveRolePermissionsBatch(listOf(roleId1.toString(), invalidId))

            // Assert
            assertThat(result).hasSize(1)
            assertThat(result[roleId1.toString()]).hasSize(1)
            assertThat(result).doesNotContainKey(invalidId)
        }

        @Test
        fun `should preserve order of requested role IDs`() {
            // Arrange
            val roleId1 = 1
            val roleId2 = 2
            val roleId3 = 3
            val permission1 =
                Permission(
                    id = 10,
                    name = "transaction:read",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            whenever(mapper.toRoleId(roleId1.toString())).thenReturn(roleId1)
            whenever(mapper.toRoleId(roleId2.toString())).thenReturn(roleId2)
            whenever(mapper.toRoleId(roleId3.toString())).thenReturn(roleId3)
            whenever(roleManagementService.listRolePermissionsBatch(any<List<Int>>()))
                .thenReturn(
                    mapOf(
                        roleId1 to listOf(permission1),
                        roleId2 to emptyList(),
                        roleId3 to listOf(permission1),
                    ),
                )
            whenever(mapper.toPermissionDTOList(any<List<Permission>>())).thenAnswer { invocation ->
                val permissions = invocation.getArgument<List<Permission>>(0)
                permissions.map { PermissionDTO(id = it.id!!.toString(), name = it.name) }
            }

            // Act
            val result =
                roleManagementResolver.resolveRolePermissionsBatch(
                    listOf(roleId1.toString(), roleId2.toString(), roleId3.toString()),
                )

            // Assert
            assertThat(result.keys.toList())
                .containsExactly(
                    roleId1.toString(),
                    roleId2.toString(),
                    roleId3.toString(),
                )
        }
    }
}
