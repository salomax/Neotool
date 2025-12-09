package io.github.salomax.neotool.security.test.model

import io.github.salomax.neotool.security.domain.abac.PolicyEffect
import io.github.salomax.neotool.security.model.abac.AbacPolicyVersionEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@DisplayName("AbacPolicyVersionEntity Tests")
class AbacPolicyVersionEntityTest {
    private val policyId = UUID.randomUUID()
    private val createdBy = UUID.randomUUID()

    @Nested
    @DisplayName("toDomain()")
    inner class ToDomainTests {
        @Test
        fun `should convert entity to domain with all fields`() {
            // Arrange
            val id = UUID.randomUUID()
            val createdAt = Instant.now().minusSeconds(3600)
            val entity =
                AbacPolicyVersionEntity(
                    id = id,
                    policyId = policyId,
                    version = 2,
                    effect = PolicyEffect.ALLOW,
                    condition = "user.id == resource.ownerId",
                    isActive = true,
                    createdAt = createdAt,
                    createdBy = createdBy,
                )

            // Act
            val domain = entity.toDomain()

            // Assert
            assertThat(domain.id).isEqualTo(id)
            assertThat(domain.policyId).isEqualTo(policyId)
            assertThat(domain.version).isEqualTo(2)
            assertThat(domain.effect).isEqualTo(PolicyEffect.ALLOW)
            assertThat(domain.condition).isEqualTo("user.id == resource.ownerId")
            assertThat(domain.isActive).isTrue()
            assertThat(domain.createdAt).isEqualTo(createdAt)
            assertThat(domain.createdBy).isEqualTo(createdBy)
        }

        @Test
        fun `should convert entity with DENY effect`() {
            // Arrange
            val entity =
                AbacPolicyVersionEntity(
                    policyId = policyId,
                    version = 1,
                    effect = PolicyEffect.DENY,
                    condition = "user.role != 'admin'",
                    isActive = false,
                )

            // Act
            val domain = entity.toDomain()

            // Assert
            assertThat(domain.effect).isEqualTo(PolicyEffect.DENY)
            assertThat(domain.condition).isEqualTo("user.role != 'admin'")
            assertThat(domain.isActive).isFalse()
        }

        @Test
        fun `should convert entity with null createdBy`() {
            // Arrange
            val entity =
                AbacPolicyVersionEntity(
                    policyId = policyId,
                    version = 1,
                    effect = PolicyEffect.ALLOW,
                    condition = "true",
                    createdBy = null,
                )

            // Act
            val domain = entity.toDomain()

            // Assert
            assertThat(domain.id).isNotNull()
            assertThat(domain.createdBy).isNull()
        }

        @Test
        fun `should preserve all entity fields in domain`() {
            // Arrange
            val id = UUID.randomUUID()
            val createdAt = Instant.now()
            val entity =
                AbacPolicyVersionEntity(
                    id = id,
                    policyId = policyId,
                    version = 5,
                    effect = PolicyEffect.ALLOW,
                    condition = "user.department == 'engineering'",
                    isActive = true,
                    createdAt = createdAt,
                    createdBy = createdBy,
                )

            // Act
            val domain = entity.toDomain()

            // Assert
            assertThat(domain.id).isEqualTo(id)
            assertThat(domain.policyId).isEqualTo(policyId)
            assertThat(domain.version).isEqualTo(5)
            assertThat(domain.effect).isEqualTo(PolicyEffect.ALLOW)
            assertThat(domain.condition).isEqualTo("user.department == 'engineering'")
            assertThat(domain.isActive).isTrue()
            assertThat(domain.createdAt).isEqualTo(createdAt)
            assertThat(domain.createdBy).isEqualTo(createdBy)
        }
    }
}
