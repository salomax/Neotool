package io.github.salomax.neotool.security.test.model

import io.github.salomax.neotool.security.domain.rbac.ScopeType
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
    private val scopeId = UUID.randomUUID()

    @Nested
    @DisplayName("toDomain()")
    inner class ToDomainTests {
        @Test
        fun `should convert entity to domain with all fields`() {
            // Arrange
            val id = UUID.randomUUID()
            val createdAt = Instant.now().minusSeconds(3600)
            val updatedAt = Instant.now()
            val validFrom = Instant.now().minusSeconds(1800)
            val validUntil = Instant.now().plusSeconds(1800)

            val entity =
                RoleAssignmentEntity(
                    id = id,
                    userId = userId,
                    roleId = 1,
                    scopeType = ScopeType.PROJECT,
                    scopeId = scopeId,
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
            assertThat(domain.roleId).isEqualTo(1)
            assertThat(domain.scopeType).isEqualTo(ScopeType.PROJECT)
            assertThat(domain.scopeId).isEqualTo(scopeId)
            assertThat(domain.validFrom).isEqualTo(validFrom)
            assertThat(domain.validUntil).isEqualTo(validUntil)
            assertThat(domain.createdAt).isEqualTo(createdAt)
            assertThat(domain.updatedAt).isEqualTo(updatedAt)
            assertThat(domain.version).isEqualTo(2L)
        }

        @Test
        fun `should convert entity with PROFILE scope type`() {
            // Arrange
            val entity =
                RoleAssignmentEntity(
                    userId = userId,
                    roleId = 2,
                    scopeType = ScopeType.PROFILE,
                    scopeId = null,
                )

            // Act
            val domain = entity.toDomain()

            // Assert
            assertThat(domain.scopeType).isEqualTo(ScopeType.PROFILE)
            assertThat(domain.scopeId).isNull()
        }

        @Test
        fun `should convert entity with null optional fields`() {
            // Arrange
            val entity =
                RoleAssignmentEntity(
                    userId = userId,
                    roleId = 3,
                    scopeType = ScopeType.PROFILE,
                    scopeId = null,
                    validFrom = null,
                    validUntil = null,
                )

            // Act
            val domain = entity.toDomain()

            // Assert
            assertThat(domain.id).isNotNull()
            assertThat(domain.scopeId).isNull()
            assertThat(domain.validFrom).isNull()
            assertThat(domain.validUntil).isNull()
        }

        @Test
        fun `should preserve version field`() {
            // Arrange
            val entity =
                RoleAssignmentEntity(
                    userId = userId,
                    roleId = 1,
                    scopeType = ScopeType.PROJECT,
                    version = 10L,
                )

            // Act
            val domain = entity.toDomain()

            // Assert
            assertThat(domain.version).isEqualTo(10L)
        }
    }
}
