package io.github.salomax.neotool.security.test.model

import io.github.salomax.neotool.security.model.rbac.RoleAssignmentEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@DisplayName("RoleAssignmentEntity Tests")
class RoleAssignmentEntityTest {
    private val userId = UUID.randomUUID()

    @Nested
    @DisplayName("toDomain()")
    inner class ToDomainTests {
        @Test
        fun `should convert entity to domain with all fields`() {
            // Arrange
            val id = UUID.randomUUID()
            val roleId = UUID.randomUUID()
            val createdAt = Instant.now().minusSeconds(3600)
            val updatedAt = Instant.now()
            val validFrom = Instant.now().minusSeconds(1800)
            val validUntil = Instant.now().plusSeconds(1800)

            val entity =
                RoleAssignmentEntity(
                    id = id,
                    userId = userId,
                    roleId = roleId,
                    validFrom = validFrom,
                    validUntil = validUntil,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    version = 2L,
                )

            // Act
            val domain = entity.toDomain()

            // Assert
            assertThat(domain.id).isEqualTo(id)
            assertThat(domain.userId).isEqualTo(userId)
            assertThat(domain.roleId).isEqualTo(roleId)
            assertThat(domain.validFrom).isEqualTo(validFrom)
            assertThat(domain.validUntil).isEqualTo(validUntil)
            assertThat(domain.createdAt).isEqualTo(createdAt)
            assertThat(domain.updatedAt).isEqualTo(updatedAt)
            assertThat(domain.version).isEqualTo(2L)
        }

        @Test
        fun `should convert entity with null optional fields`() {
            // Arrange
            val entity =
                RoleAssignmentEntity(
                    userId = userId,
                    roleId = UUID.randomUUID(),
                    validFrom = null,
                    validUntil = null,
                )

            // Act
            val domain = entity.toDomain()

            // Assert
            assertThat(domain.id).isNotNull()
            assertThat(domain.validFrom).isNull()
            assertThat(domain.validUntil).isNull()
        }

        @Test
        fun `should preserve version field`() {
            // Arrange
            val entity =
                RoleAssignmentEntity(
                    userId = userId,
                    roleId = UUID.randomUUID(),
                    version = 10L,
                )

            // Act
            val domain = entity.toDomain()

            // Assert
            assertThat(domain.version).isEqualTo(10L)
        }
    }
}
