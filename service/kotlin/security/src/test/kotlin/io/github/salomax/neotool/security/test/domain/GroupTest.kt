package io.github.salomax.neotool.security.test.domain

import io.github.salomax.neotool.security.domain.rbac.Group
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@DisplayName("Group Domain Tests")
class GroupTest {
    @Nested
    @DisplayName("toEntity()")
    inner class ToEntityTests {
        @Test
        fun `should convert to entity with provided id`() {
            // Arrange
            val id = UUID.randomUUID()
            val group =
                Group(
                    id = id,
                    name = "Test Group",
                    description = "Test Description",
                )

            // Act
            val entity = group.toEntity()

            // Assert
            assertThat(entity.id).isEqualTo(id)
            assertThat(entity.name).isEqualTo("Test Group")
            assertThat(entity.description).isEqualTo("Test Description")
        }

        @Test
        fun `should generate id when id is null`() {
            // Arrange
            val group =
                Group(
                    id = null,
                    name = "Test Group",
                )

            // Act
            val entity = group.toEntity()

            // Assert
            assertThat(entity.id).isNotNull()
            assertThat(entity.name).isEqualTo("Test Group")
        }

        @Test
        fun `should preserve all fields when converting to entity`() {
            // Arrange
            val id = UUID.randomUUID()
            val createdAt = Instant.now().minusSeconds(3600)
            val updatedAt = Instant.now()
            val version = 5L
            val group =
                Group(
                    id = id,
                    name = "Test Group",
                    description = "Test Description",
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    version = version,
                )

            // Act
            val entity = group.toEntity()

            // Assert
            assertThat(entity.id).isEqualTo(id)
            assertThat(entity.name).isEqualTo("Test Group")
            assertThat(entity.description).isEqualTo("Test Description")
            assertThat(entity.createdAt).isEqualTo(createdAt)
            assertThat(entity.updatedAt).isEqualTo(updatedAt)
            assertThat(entity.version).isEqualTo(version)
        }

        @Test
        fun `should handle null description`() {
            // Arrange
            val group =
                Group(
                    name = "Test Group",
                    description = null,
                )

            // Act
            val entity = group.toEntity()

            // Assert
            assertThat(entity.id).isNotNull()
            assertThat(entity.name).isEqualTo("Test Group")
            assertThat(entity.description).isNull()
        }
    }
}
