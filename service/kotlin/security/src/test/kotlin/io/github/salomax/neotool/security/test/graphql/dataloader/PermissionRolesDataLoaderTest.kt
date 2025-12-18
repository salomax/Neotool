package io.github.salomax.neotool.security.test.graphql.dataloader

import io.github.salomax.neotool.security.graphql.dataloader.PermissionRolesDataLoader
import io.github.salomax.neotool.security.graphql.dto.RoleDTO
import io.github.salomax.neotool.security.graphql.resolver.PermissionManagementResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

@DisplayName("PermissionRolesDataLoader Tests")
class PermissionRolesDataLoaderTest {
    private lateinit var permissionManagementResolver: PermissionManagementResolver
    private lateinit var dataLoader: org.dataloader.DataLoader<String, List<RoleDTO>>

    @BeforeEach
    fun setUp() {
        permissionManagementResolver = mock()
        dataLoader = PermissionRolesDataLoader.create(permissionManagementResolver)
    }

    @Nested
    @DisplayName("Batching")
    inner class BatchingTests {
        @Test
        fun `should batch multiple load calls into single resolver call`() {
            // Arrange
            val permissionId1 = "10"
            val permissionId2 = "20"
            val role1 = RoleDTO(id = UUID.randomUUID().toString(), name = "admin")
            val role2 = RoleDTO(id = UUID.randomUUID().toString(), name = "editor")
            whenever(permissionManagementResolver.resolvePermissionRolesBatch(any<List<String>>()))
                .thenReturn(
                    mapOf(
                        permissionId1 to listOf(role1),
                        permissionId2 to listOf(role2),
                    ),
                )

            // Act
            val future1 = dataLoader.load(permissionId1)
            val future2 = dataLoader.load(permissionId2)
            dataLoader.dispatch()

            // Assert
            val result1 = future1.get()
            val result2 = future2.get()
            assertThat(result1).hasSize(1)
            assertThat(result1[0].name).isEqualTo("admin")
            assertThat(result2).hasSize(1)
            assertThat(result2[0].name).isEqualTo("editor")
            verify(permissionManagementResolver, times(1)).resolvePermissionRolesBatch(any<List<String>>())
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCasesTests {
        @Test
        fun `should return empty list for permission with no roles`() {
            // Arrange
            val permissionId = "10"
            whenever(permissionManagementResolver.resolvePermissionRolesBatch(any<List<String>>()))
                .thenReturn(mapOf(permissionId to emptyList()))

            // Act
            val future = dataLoader.load(permissionId)
            dataLoader.dispatch()

            // Assert
            assertThat(future.get()).isEmpty()
        }
    }
}
