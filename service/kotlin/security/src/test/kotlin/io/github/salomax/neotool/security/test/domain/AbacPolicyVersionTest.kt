package io.github.salomax.neotool.security.test.domain

import io.github.salomax.neotool.security.domain.abac.AbacPolicyVersion
import io.github.salomax.neotool.security.domain.abac.PolicyEffect
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@DisplayName("AbacPolicyVersion Domain Tests")
class AbacPolicyVersionTest {
    private val policyId = UUID.randomUUID()
    private val createdBy = UUID.randomUUID()

    @Nested
    @DisplayName("toEntity()")
    inner class ToEntityTests {
        @Test
        fun `should convert to entity with provided id`() {
            // Arrange
            val id = UUID.randomUUID()
            val policyVersion =
                AbacPolicyVersion(
                    id = id,
                    policyId = policyId,
                    version = 1,
                    effect = PolicyEffect.ALLOW,
                    condition = "user.id == resource.ownerId",
                )

            // Act
            val entity = policyVersion.toEntity()

            // Assert
            assertThat(entity.id).isEqualTo(id)
            assertThat(entity.policyId).isEqualTo(policyId)
            assertThat(entity.version).isEqualTo(1)
            assertThat(entity.effect).isEqualTo(PolicyEffect.ALLOW)
            assertThat(entity.condition).isEqualTo("user.id == resource.ownerId")
        }

        @Test
        fun `should generate id when id is null`() {
            // Arrange
            val policyVersion =
                AbacPolicyVersion(
                    id = null,
                    policyId = policyId,
                    version = 2,
                    effect = PolicyEffect.DENY,
                    condition = "user.role != 'admin'",
                )

            // Act
            val entity = policyVersion.toEntity()

            // Assert
            assertThat(entity.id).isNotNull()
            assertThat(entity.policyId).isEqualTo(policyId)
            assertThat(entity.version).isEqualTo(2)
            assertThat(entity.effect).isEqualTo(PolicyEffect.DENY)
        }

        @Test
        fun `should preserve all fields when converting to entity`() {
            // Arrange
            val id = UUID.randomUUID()
            val createdAt = Instant.now().minusSeconds(3600)
            val policyVersion =
                AbacPolicyVersion(
                    id = id,
                    policyId = policyId,
                    version = 3,
                    effect = PolicyEffect.ALLOW,
                    condition = "user.department == 'engineering'",
                    isActive = true,
                    createdAt = createdAt,
                    createdBy = createdBy,
                )

            // Act
            val entity = policyVersion.toEntity()

            // Assert
            assertThat(entity.id).isEqualTo(id)
            assertThat(entity.policyId).isEqualTo(policyId)
            assertThat(entity.version).isEqualTo(3)
            assertThat(entity.effect).isEqualTo(PolicyEffect.ALLOW)
            assertThat(entity.condition).isEqualTo("user.department == 'engineering'")
            assertThat(entity.isActive).isTrue()
            assertThat(entity.createdAt).isEqualTo(createdAt)
            assertThat(entity.createdBy).isEqualTo(createdBy)
        }

        @Test
        fun `should default isActive to false`() {
            // Arrange
            val policyVersion =
                AbacPolicyVersion(
                    policyId = policyId,
                    version = 1,
                    effect = PolicyEffect.ALLOW,
                    condition = "true",
                )

            // Act
            val entity = policyVersion.toEntity()

            // Assert
            assertThat(entity.isActive).isFalse()
        }

        @Test
        fun `should handle null createdBy`() {
            // Arrange
            val policyVersion =
                AbacPolicyVersion(
                    policyId = policyId,
                    version = 1,
                    effect = PolicyEffect.ALLOW,
                    condition = "true",
                    createdBy = null,
                )

            // Act
            val entity = policyVersion.toEntity()

            // Assert
            assertThat(entity.id).isNotNull()
            assertThat(entity.createdBy).isNull()
        }
    }
}
