package io.github.salomax.neotool.security.test.graphql.dataloader

import io.github.salomax.neotool.security.graphql.dataloader.RolePermissionsDataLoader
import io.github.salomax.neotool.security.graphql.dto.PermissionDTO
import io.github.salomax.neotool.security.graphql.resolver.RoleManagementResolver
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

@DisplayName("RolePermissionsDataLoader Tests")
class RolePermissionsDataLoaderTest {
    private lateinit var roleManagementResolver: RoleManagementResolver
    private lateinit var dataLoader: org.dataloader.DataLoader<String, List<PermissionDTO>>

    @BeforeEach
    fun setUp() {
        roleManagementResolver = mock()
        dataLoader = RolePermissionsDataLoader.create(roleManagementResolver)
    }

    @Nested
    @DisplayName("Batching")
    inner class BatchingTests {
        @Test
        fun `should batch multiple load calls into single resolver call`() {
            // Arrange
            val roleId1 = "1"
            val roleId2 = "2"
            val permission1 = PermissionDTO(id = "10", name = "transaction:read")
            val permission2 = PermissionDTO(id = "20", name = "transaction:write")
            whenever(roleManagementResolver.resolveRolePermissionsBatch(any<List<String>>()))
                .thenReturn(
                    mapOf(
                        roleId1 to listOf(permission1),
                        roleId2 to listOf(permission2),
                    ),
                )

            // Act
            val future1 = dataLoader.load(roleId1)
            val future2 = dataLoader.load(roleId2)
            dataLoader.dispatch()

            // Assert
            val result1 = future1.get()
            val result2 = future2.get()
            assertThat(result1).hasSize(1)
            assertThat(result1[0].name).isEqualTo("transaction:read")
            assertThat(result2).hasSize(1)
            assertThat(result2[0].name).isEqualTo("transaction:write")
            verify(roleManagementResolver, times(1)).resolveRolePermissionsBatch(any<List<String>>())
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCasesTests {
        @Test
        fun `should return empty list for role with no permissions`() {
            // Arrange
            val roleId = "1"
            whenever(roleManagementResolver.resolveRolePermissionsBatch(any<List<String>>()))
                .thenReturn(mapOf(roleId to emptyList()))

            // Act
            val future = dataLoader.load(roleId)
            dataLoader.dispatch()

            // Assert
            assertThat(future.get()).isEmpty()
        }
    }
}
