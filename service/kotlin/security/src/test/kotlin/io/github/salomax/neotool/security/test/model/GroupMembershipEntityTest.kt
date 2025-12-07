package io.github.salomax.neotool.security.test.model

import io.github.salomax.neotool.security.domain.rbac.MembershipType
import io.github.salomax.neotool.security.model.rbac.GroupMembershipEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@DisplayName("GroupMembershipEntity Tests")
class GroupMembershipEntityTest {
    private val userId = UUID.randomUUID()
    private val groupId = UUID.randomUUID()

    @Nested
    @DisplayName("toDomain()")
    inner class ToDomainTests {
        @Test
        fun `should convert entity to domain with all fields`() {
            // Arrange
            val id = UUID.randomUUID()
            val createdAt = Instant.now().minusSeconds(3600)
            val updatedAt = Instant.now()
            val validUntil = Instant.now().plusSeconds(3600)

            val entity =
                GroupMembershipEntity(
                    id = id,
                    userId = userId,
                    groupId = groupId,
                    membershipType = MembershipType.ADMIN,
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
            assertThat(domain.groupId).isEqualTo(groupId)
            assertThat(domain.membershipType).isEqualTo(MembershipType.ADMIN)
            assertThat(domain.validUntil).isEqualTo(validUntil)
            assertThat(domain.createdAt).isEqualTo(createdAt)
            assertThat(domain.updatedAt).isEqualTo(updatedAt)
            assertThat(domain.version).isEqualTo(2L)
        }

        @Test
        fun `should convert entity with MEMBER membership type`() {
            // Arrange
            val id = UUID.randomUUID()
            val entity =
                GroupMembershipEntity(
                    id = id,
                    userId = userId,
                    groupId = groupId,
                    membershipType = MembershipType.MEMBER,
                )

            // Act
            val domain = entity.toDomain()

            // Assert
            assertThat(domain.membershipType).isEqualTo(MembershipType.MEMBER)
        }

        @Test
        fun `should convert entity with OWNER membership type`() {
            // Arrange
            val id = UUID.randomUUID()
            val entity =
                GroupMembershipEntity(
                    id = id,
                    userId = userId,
                    groupId = groupId,
                    membershipType = MembershipType.OWNER,
                )

            // Act
            val domain = entity.toDomain()

            // Assert
            assertThat(domain.membershipType).isEqualTo(MembershipType.OWNER)
        }

        @Test
        fun `should convert entity with null validUntil`() {
            // Arrange
            val id = UUID.randomUUID()
            val entity =
                GroupMembershipEntity(
                    id = id,
                    userId = userId,
                    groupId = groupId,
                    validUntil = null,
                )

            // Act
            val domain = entity.toDomain()

            // Assert
            assertThat(domain.id).isNotNull()
            assertThat(domain.validUntil).isNull()
        }

        @Test
        fun `should preserve version field`() {
            // Arrange
            val id = UUID.randomUUID()
            val entity =
                GroupMembershipEntity(
                    id = id,
                    userId = userId,
                    groupId = groupId,
                    version = 5L,
                )

            // Act
            val domain = entity.toDomain()

            // Assert
            assertThat(domain.version).isEqualTo(5L)
        }
    }
}
