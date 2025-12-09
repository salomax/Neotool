package io.github.salomax.neotool.security.test.model

import io.github.salomax.neotool.security.model.rbac.GroupEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@DisplayName("GroupEntity Tests")
class GroupEntityTest {
    @Nested
    @DisplayName("toDomain()")
    inner class ToDomainTests {
        @Test
        fun `should convert entity to domain with all fields`() {
            // Arrange
            val id = UUID.randomUUID()
            val createdAt = Instant.now().minusSeconds(3600)
            val updatedAt = Instant.now()

            val entity =
                GroupEntity(
                    id = id,
                    name = "Engineering Team",
                    description = "Software engineering team",
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    version = 3L,
                )

            // Act
            val domain = entity.toDomain()

            // Assert
            assertThat(domain.id).isEqualTo(id)
            assertThat(domain.name).isEqualTo("Engineering Team")
            assertThat(domain.description).isEqualTo("Software engineering team")
            assertThat(domain.createdAt).isEqualTo(createdAt)
            assertThat(domain.updatedAt).isEqualTo(updatedAt)
            assertThat(domain.version).isEqualTo(3L)
        }

        @Test
        fun `should convert entity with null description`() {
            // Arrange
            val entity =
                GroupEntity(
                    name = "Test Group",
                    description = null,
                )

            // Act
            val domain = entity.toDomain()

            // Assert
            assertThat(domain.id).isNotNull()
            assertThat(domain.name).isEqualTo("Test Group")
            assertThat(domain.description).isNull()
        }

        @Test
        fun `should preserve version field`() {
            // Arrange
            val entity =
                GroupEntity(
                    name = "Test Group",
                    version = 10L,
                )

            // Act
            val domain = entity.toDomain()

            // Assert
            assertThat(domain.version).isEqualTo(10L)
        }

        @Test
        fun `should preserve timestamps`() {
            // Arrange
            val createdAt = Instant.now().minusSeconds(7200)
            val updatedAt = Instant.now().minusSeconds(1800)
            val entity =
                GroupEntity(
                    name = "Test Group",
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                )

            // Act
            val domain = entity.toDomain()

            // Assert
            assertThat(domain.createdAt).isEqualTo(createdAt)
            assertThat(domain.updatedAt).isEqualTo(updatedAt)
        }
    }
}
