package io.github.salomax.neotool.security.test.domain

import io.github.salomax.neotool.security.domain.rbac.GroupMembership
import io.github.salomax.neotool.security.domain.rbac.MembershipType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@DisplayName("GroupMembership Domain Tests")
class GroupMembershipTest {
    private val userId = UUID.randomUUID()
    private val groupId = UUID.randomUUID()
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
            val membership =
                GroupMembership(
                    id = id,
                    userId = userId,
                    groupId = groupId,
                    membershipType = MembershipType.ADMIN,
                )

            // Act
            val entity = membership.toEntity()

            // Assert
            assertThat(entity.id).isEqualTo(id)
            assertThat(entity.userId).isEqualTo(userId)
            assertThat(entity.groupId).isEqualTo(groupId)
            assertThat(entity.membershipType).isEqualTo(MembershipType.ADMIN)
        }

        @Test
        fun `should generate id when id is null`() {
            // Arrange
            val membership =
                GroupMembership(
                    id = null,
                    userId = userId,
                    groupId = groupId,
                )

            // Act
            val entity = membership.toEntity()

            // Assert
            assertThat(entity.id).isNotNull()
            assertThat(entity.userId).isEqualTo(userId)
            assertThat(entity.groupId).isEqualTo(groupId)
        }

        @Test
        fun `should preserve all fields when converting to entity`() {
            // Arrange
            val id = UUID.randomUUID()
            val createdAt = Instant.now().minusSeconds(3600)
            val updatedAt = Instant.now()
            val version = 3L
            val membership =
                GroupMembership(
                    id = id,
                    userId = userId,
                    groupId = groupId,
                    membershipType = MembershipType.OWNER,
                    validUntil = future,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    version = version,
                )

            // Act
            val entity = membership.toEntity()

            // Assert
            assertThat(entity.id).isEqualTo(id)
            assertThat(entity.userId).isEqualTo(userId)
            assertThat(entity.groupId).isEqualTo(groupId)
            assertThat(entity.membershipType).isEqualTo(MembershipType.OWNER)
            assertThat(entity.validUntil).isEqualTo(future)
            assertThat(entity.createdAt).isEqualTo(createdAt)
            assertThat(entity.updatedAt).isEqualTo(updatedAt)
            assertThat(entity.version).isEqualTo(version)
        }

        @Test
        fun `should default to MEMBER membership type`() {
            // Arrange
            val membership =
                GroupMembership(
                    userId = userId,
                    groupId = groupId,
                )

            // Act
            val entity = membership.toEntity()

            // Assert
            assertThat(entity.membershipType).isEqualTo(MembershipType.MEMBER)
        }
    }

    @Nested
    @DisplayName("isActive()")
    inner class IsActiveTests {
        @Test
        fun `should return true when validUntil is null`() {
            // Arrange
            val membership =
                GroupMembership(
                    userId = userId,
                    groupId = groupId,
                    validUntil = null,
                )

            // Act
            val result = membership.isActive(now)

            // Assert
            assertThat(result).isTrue()
        }

        @Test
        fun `should return true when now is before validUntil`() {
            // Arrange
            val membership =
                GroupMembership(
                    userId = userId,
                    groupId = groupId,
                    validUntil = future,
                )

            // Act
            val result = membership.isActive(now)

            // Assert
            assertThat(result).isTrue()
        }

        @Test
        fun `should return true when now equals validUntil`() {
            // Arrange
            val membership =
                GroupMembership(
                    userId = userId,
                    groupId = groupId,
                    validUntil = now,
                )

            // Act
            val result = membership.isActive(now)

            // Assert
            assertThat(result).isTrue()
        }

        @Test
        fun `should return false when now is after validUntil`() {
            // Arrange
            val membership =
                GroupMembership(
                    userId = userId,
                    groupId = groupId,
                    validUntil = past,
                )

            // Act
            val result = membership.isActive(now)

            // Assert
            assertThat(result).isFalse()
        }
    }

    @Nested
    @DisplayName("isExpired()")
    inner class IsExpiredTests {
        @Test
        fun `should return false when validUntil is null`() {
            // Arrange
            val membership =
                GroupMembership(
                    userId = userId,
                    groupId = groupId,
                    validUntil = null,
                )

            // Act
            val result = membership.isExpired(now)

            // Assert
            assertThat(result).isFalse()
        }

        @Test
        fun `should return false when now is before validUntil`() {
            // Arrange
            val membership =
                GroupMembership(
                    userId = userId,
                    groupId = groupId,
                    validUntil = future,
                )

            // Act
            val result = membership.isExpired(now)

            // Assert
            assertThat(result).isFalse()
        }

        @Test
        fun `should return false when now equals validUntil`() {
            // Arrange
            val membership =
                GroupMembership(
                    userId = userId,
                    groupId = groupId,
                    validUntil = now,
                )

            // Act
            val result = membership.isExpired(now)

            // Assert
            assertThat(result).isFalse()
        }

        @Test
        fun `should return true when now is after validUntil`() {
            // Arrange
            val membership =
                GroupMembership(
                    userId = userId,
                    groupId = groupId,
                    validUntil = past,
                )

            // Act
            val result = membership.isExpired(now)

            // Assert
            assertThat(result).isTrue()
        }
    }

    @Nested
    @DisplayName("isPermanent()")
    inner class IsPermanentTests {
        @Test
        fun `should return true when validUntil is null`() {
            // Arrange
            val membership =
                GroupMembership(
                    userId = userId,
                    groupId = groupId,
                    validUntil = null,
                )

            // Act
            val result = membership.isPermanent()

            // Assert
            assertThat(result).isTrue()
        }

        @Test
        fun `should return false when validUntil is set`() {
            // Arrange
            val membership =
                GroupMembership(
                    userId = userId,
                    groupId = groupId,
                    validUntil = future,
                )

            // Act
            val result = membership.isPermanent()

            // Assert
            assertThat(result).isFalse()
        }
    }
}
