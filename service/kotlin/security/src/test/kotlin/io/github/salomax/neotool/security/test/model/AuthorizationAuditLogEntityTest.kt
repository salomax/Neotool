package io.github.salomax.neotool.security.test.model

import io.github.salomax.neotool.security.domain.audit.AuthorizationResult
import io.github.salomax.neotool.security.model.audit.AuthorizationAuditLogEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@DisplayName("AuthorizationAuditLogEntity Tests")
class AuthorizationAuditLogEntityTest {
    private val userId = UUID.randomUUID()
    private val resourceId = UUID.randomUUID()
    private val groupId1 = UUID.randomUUID()
    private val groupId2 = UUID.randomUUID()

    @Nested
    @DisplayName("toDomain()")
    inner class ToDomainTests {
        @Test
        fun `should convert entity to domain with all fields`() {
            // Arrange
            val id = UUID.randomUUID()
            val groups = listOf(groupId1, groupId2)
            val roles = listOf(1, 2, 3)
            val metadata = mapOf("ip" to "127.0.0.1", "userAgent" to "Mozilla/5.0")
            val timestamp = Instant.now()
            val createdAt = Instant.now().minusSeconds(3600)

            val entity =
                AuthorizationAuditLogEntity(
                    id = id,
                    userId = userId,
                    groups = groups,
                    roles = roles,
                    requestedAction = "transaction:read",
                    resourceType = "Transaction",
                    resourceId = resourceId,
                    rbacResult = AuthorizationResult.ALLOWED,
                    abacResult = AuthorizationResult.ALLOWED,
                    finalDecision = AuthorizationResult.ALLOWED,
                    timestamp = timestamp,
                    metadata = metadata,
                    createdAt = createdAt,
                )

            // Act
            val domain = entity.toDomain()

            // Assert
            assertThat(domain.id).isEqualTo(id)
            assertThat(domain.userId).isEqualTo(userId)
            assertThat(domain.groups).isEqualTo(groups)
            assertThat(domain.roles).isEqualTo(roles)
            assertThat(domain.requestedAction).isEqualTo("transaction:read")
            assertThat(domain.resourceType).isEqualTo("Transaction")
            assertThat(domain.resourceId).isEqualTo(resourceId)
            assertThat(domain.rbacResult).isEqualTo(AuthorizationResult.ALLOWED)
            assertThat(domain.abacResult).isEqualTo(AuthorizationResult.ALLOWED)
            assertThat(domain.finalDecision).isEqualTo(AuthorizationResult.ALLOWED)
            assertThat(domain.timestamp).isEqualTo(timestamp)
            assertThat(domain.metadata).isEqualTo(metadata)
            assertThat(domain.createdAt).isEqualTo(createdAt)
        }

        @Test
        fun `should convert entity with null optional fields`() {
            // Arrange
            val entity =
                AuthorizationAuditLogEntity(
                    userId = userId,
                    groups = null,
                    roles = null,
                    requestedAction = "transaction:read",
                    resourceType = null,
                    resourceId = null,
                    rbacResult = AuthorizationResult.DENIED,
                    abacResult = null,
                    finalDecision = AuthorizationResult.DENIED,
                    metadata = null,
                )

            // Act
            val domain = entity.toDomain()

            // Assert
            assertThat(domain.id).isNotNull()
            assertThat(domain.userId).isEqualTo(userId)
            assertThat(domain.groups).isNull()
            assertThat(domain.roles).isNull()
            assertThat(domain.resourceType).isNull()
            assertThat(domain.resourceId).isNull()
            assertThat(domain.abacResult).isNull()
            assertThat(domain.metadata).isNull()
        }

        @Test
        fun `should convert entity with NOT_EVALUATED result`() {
            // Arrange
            val entity =
                AuthorizationAuditLogEntity(
                    userId = userId,
                    requestedAction = "transaction:read",
                    rbacResult = AuthorizationResult.NOT_EVALUATED,
                    abacResult = AuthorizationResult.NOT_EVALUATED,
                    finalDecision = AuthorizationResult.NOT_EVALUATED,
                )

            // Act
            val domain = entity.toDomain()

            // Assert
            assertThat(domain.rbacResult).isEqualTo(AuthorizationResult.NOT_EVALUATED)
            assertThat(domain.abacResult).isEqualTo(AuthorizationResult.NOT_EVALUATED)
            assertThat(domain.finalDecision).isEqualTo(AuthorizationResult.NOT_EVALUATED)
        }

        @Test
        fun `should preserve empty lists`() {
            // Arrange
            val entity =
                AuthorizationAuditLogEntity(
                    userId = userId,
                    groups = emptyList(),
                    roles = emptyList(),
                    requestedAction = "transaction:read",
                    rbacResult = AuthorizationResult.ALLOWED,
                    finalDecision = AuthorizationResult.ALLOWED,
                )

            // Act
            val domain = entity.toDomain()

            // Assert
            assertThat(domain.groups).isEmpty()
            assertThat(domain.roles).isEmpty()
        }
    }

    @Nested
    @DisplayName("Getters")
    inner class GetterTests {
        @Test
        fun `should return correct values from getters`() {
            // Arrange
            val id = UUID.randomUUID()
            val entity =
                AuthorizationAuditLogEntity(
                    id = id,
                    userId = userId,
                    requestedAction = "transaction:read",
                    rbacResult = AuthorizationResult.ALLOWED,
                    finalDecision = AuthorizationResult.ALLOWED,
                )

            // Act & Assert
            assertThat(entity.id).isEqualTo(id)
            assertThat(entity.userId).isEqualTo(userId)
            assertThat(entity.requestedAction).isEqualTo("transaction:read")
            assertThat(entity.rbacResult).isEqualTo(AuthorizationResult.ALLOWED)
            assertThat(entity.finalDecision).isEqualTo(AuthorizationResult.ALLOWED)
        }
    }
}
