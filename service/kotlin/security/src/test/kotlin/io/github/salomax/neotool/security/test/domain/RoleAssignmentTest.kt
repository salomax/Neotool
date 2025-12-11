package io.github.salomax.neotool.security.test.domain

import io.github.salomax.neotool.security.domain.rbac.RoleAssignment
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@DisplayName("RoleAssignment Domain Tests")
class RoleAssignmentTest {
    private val userId = UUID.randomUUID()
    private val roleId = UUID.randomUUID()
    private val now = Instant.now()
    private val past = now.minusSeconds(3600)
    private val future = now.plusSeconds(3600)

    @Nested
    @DisplayName("toEntity()")
    inner class ToEntityTests {
        @Test
        fun `should convert to entity with provided id`() {
            // Arrange
            val id = UUID.randomUUID()
            val assignment =
                RoleAssignment(
                    id = id,
                    userId = userId,
                    roleId = roleId,
                )

            // Act
            val entity = assignment.toEntity()

            // Assert
            assertThat(entity.id).isEqualTo(id)
            assertThat(entity.userId).isEqualTo(userId)
            assertThat(entity.roleId).isEqualTo(roleId)
        }

        @Test
        fun `should generate id when id is null`() {
            // Arrange
            val assignment =
                RoleAssignment(
                    id = null,
                    userId = userId,
                    roleId = roleId,
                )

            // Act
            val entity = assignment.toEntity()

            // Assert
            assertThat(entity.id).isNotNull()
            assertThat(entity.userId).isEqualTo(userId)
            assertThat(entity.roleId).isEqualTo(roleId)
        }
    }

    @Nested
    @DisplayName("isActive()")
    inner class IsActiveTests {
        @Test
        fun `should return true when validFrom and validUntil are null`() {
            // Arrange
            val assignment =
                RoleAssignment(
                    userId = userId,
                    roleId = roleId,
                    validFrom = null,
                    validUntil = null,
                )

            // Act
            val result = assignment.isActive(now)

            // Assert
            assertThat(result).isTrue()
        }

        @Test
        fun `should return true when now is after validFrom and before validUntil`() {
            // Arrange
            val assignment =
                RoleAssignment(
                    userId = userId,
                    roleId = roleId,
                    validFrom = past,
                    validUntil = future,
                )

            // Act
            val result = assignment.isActive(now)

            // Assert
            assertThat(result).isTrue()
        }

        @Test
        fun `should return true when now equals validFrom`() {
            // Arrange
            val assignment =
                RoleAssignment(
                    userId = userId,
                    roleId = roleId,
                    validFrom = now,
                    validUntil = future,
                )

            // Act
            val result = assignment.isActive(now)

            // Assert
            assertThat(result).isTrue()
        }

        @Test
        fun `should return true when now equals validUntil`() {
            // Arrange
            val assignment =
                RoleAssignment(
                    userId = userId,
                    roleId = roleId,
                    validFrom = past,
                    validUntil = now,
                )

            // Act
            val result = assignment.isActive(now)

            // Assert
            assertThat(result).isTrue()
        }

        @Test
        fun `should return false when now is before validFrom`() {
            // Arrange
            val assignment =
                RoleAssignment(
                    userId = userId,
                    roleId = roleId,
                    validFrom = future,
                    validUntil = future.plusSeconds(3600),
                )

            // Act
            val result = assignment.isActive(now)

            // Assert
            assertThat(result).isFalse()
        }

        @Test
        fun `should return false when now is after validUntil`() {
            // Arrange
            val assignment =
                RoleAssignment(
                    userId = userId,
                    roleId = roleId,
                    validFrom = past,
                    validUntil = past,
                )

            // Act
            val result = assignment.isActive(now)

            // Assert
            assertThat(result).isFalse()
        }

        @Test
        fun `should return true when validFrom is null and now is before validUntil`() {
            // Arrange
            val assignment =
                RoleAssignment(
                    userId = userId,
                    roleId = roleId,
                    validFrom = null,
                    validUntil = future,
                )

            // Act
            val result = assignment.isActive(now)

            // Assert
            assertThat(result).isTrue()
        }

        @Test
        fun `should return true when validUntil is null and now is after validFrom`() {
            // Arrange
            val assignment =
                RoleAssignment(
                    userId = userId,
                    roleId = roleId,
                    validFrom = past,
                    validUntil = null,
                )

            // Act
            val result = assignment.isActive(now)

            // Assert
            assertThat(result).isTrue()
        }
    }

    @Nested
    @DisplayName("isExpired()")
    inner class IsExpiredTests {
        @Test
        fun `should return false when assignment is active`() {
            // Arrange
            val assignment =
                RoleAssignment(
                    userId = userId,
                    roleId = roleId,
                    validFrom = null,
                    validUntil = null,
                )

            // Act
            val result = assignment.isExpired(now)

            // Assert
            assertThat(result).isFalse()
        }

        @Test
        fun `should return true when now is after validUntil`() {
            // Arrange
            val assignment =
                RoleAssignment(
                    userId = userId,
                    roleId = roleId,
                    validFrom = past,
                    validUntil = past,
                )

            // Act
            val result = assignment.isExpired(now)

            // Assert
            assertThat(result).isTrue()
        }

        @Test
        fun `should return true when now is before validFrom`() {
            // Arrange
            val assignment =
                RoleAssignment(
                    userId = userId,
                    roleId = roleId,
                    validFrom = future,
                    validUntil = future.plusSeconds(3600),
                )

            // Act
            val result = assignment.isExpired(now)

            // Assert
            assertThat(result).isTrue()
        }
    }

    @Nested
    @DisplayName("isTemporary()")
    inner class IsTemporaryTests {
        @Test
        fun `should return false when validFrom and validUntil are null`() {
            // Arrange
            val assignment =
                RoleAssignment(
                    userId = userId,
                    roleId = roleId,
                    validFrom = null,
                    validUntil = null,
                )

            // Act
            val result = assignment.isTemporary()

            // Assert
            assertThat(result).isFalse()
        }

        @Test
        fun `should return true when validFrom is set`() {
            // Arrange
            val assignment =
                RoleAssignment(
                    userId = userId,
                    roleId = roleId,
                    validFrom = now,
                    validUntil = null,
                )

            // Act
            val result = assignment.isTemporary()

            // Assert
            assertThat(result).isTrue()
        }

        @Test
        fun `should return true when validUntil is set`() {
            // Arrange
            val assignment =
                RoleAssignment(
                    userId = userId,
                    roleId = roleId,
                    validFrom = null,
                    validUntil = future,
                )

            // Act
            val result = assignment.isTemporary()

            // Assert
            assertThat(result).isTrue()
        }

        @Test
        fun `should return true when both validFrom and validUntil are set`() {
            // Arrange
            val assignment =
                RoleAssignment(
                    userId = userId,
                    roleId = roleId,
                    validFrom = past,
                    validUntil = future,
                )

            // Act
            val result = assignment.isTemporary()

            // Assert
            assertThat(result).isTrue()
        }
    }

    @Nested
    @DisplayName("isPermanent()")
    inner class IsPermanentTests {
        @Test
        fun `should return true when validFrom and validUntil are null`() {
            // Arrange
            val assignment =
                RoleAssignment(
                    userId = userId,
                    roleId = roleId,
                    validFrom = null,
                    validUntil = null,
                )

            // Act
            val result = assignment.isPermanent()

            // Assert
            assertThat(result).isTrue()
        }

        @Test
        fun `should return false when validFrom is set`() {
            // Arrange
            val assignment =
                RoleAssignment(
                    userId = userId,
                    roleId = roleId,
                    validFrom = now,
                    validUntil = null,
                )

            // Act
            val result = assignment.isPermanent()

            // Assert
            assertThat(result).isFalse()
        }

        @Test
        fun `should return false when validUntil is set`() {
            // Arrange
            val assignment =
                RoleAssignment(
                    userId = userId,
                    roleId = roleId,
                    validFrom = null,
                    validUntil = future,
                )

            // Act
            val result = assignment.isPermanent()

            // Assert
            assertThat(result).isFalse()
        }

        @Test
        fun `should return false when both validFrom and validUntil are set`() {
            // Arrange
            val assignment =
                RoleAssignment(
                    userId = userId,
                    roleId = roleId,
                    validFrom = past,
                    validUntil = future,
                )

            // Act
            val result = assignment.isPermanent()

            // Assert
            assertThat(result).isFalse()
        }
    }
}
