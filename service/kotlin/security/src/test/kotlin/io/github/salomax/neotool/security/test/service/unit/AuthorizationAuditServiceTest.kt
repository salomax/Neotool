package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.security.domain.audit.AuthorizationResult
import io.github.salomax.neotool.security.model.audit.AuthorizationAuditLogEntity
import io.github.salomax.neotool.security.repo.AuthorizationAuditLogRepository
import io.github.salomax.neotool.security.service.authorization.AuthorizationAuditService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

@DisplayName("AuthorizationAuditService Unit Tests")
class AuthorizationAuditServiceTest {
    private lateinit var auditLogRepository: AuthorizationAuditLogRepository
    private lateinit var auditService: AuthorizationAuditService

    @BeforeEach
    fun setUp() {
        auditLogRepository = mock()
        auditService = AuthorizationAuditService(auditLogRepository)
    }

    @Nested
    @DisplayName("logAuthorizationDecision()")
    inner class LogAuthorizationDecisionTests {
        @Test
        fun `should log authorization decision with all fields`() {
            // Arrange
            val userId = UUID.randomUUID()
            val groupId1 = UUID.randomUUID()
            val groupId2 = UUID.randomUUID()
            val resourceId = UUID.randomUUID()
            val groups = listOf(groupId1, groupId2)
            val roles = listOf(UUID.randomUUID(), UUID.randomUUID())
            val metadata = mapOf("ip" to "127.0.0.1", "userAgent" to "Mozilla/5.0")
            val savedEntity =
                AuthorizationAuditLogEntity(
                    userId = userId,
                    groups = groups,
                    roles = roles,
                    requestedAction = "transaction:read",
                    resourceType = "Transaction",
                    resourceId = resourceId,
                    rbacResult = AuthorizationResult.ALLOWED,
                    abacResult = AuthorizationResult.ALLOWED,
                    finalDecision = AuthorizationResult.ALLOWED,
                    metadata = metadata,
                )

            whenever(auditLogRepository.save(any())).thenReturn(savedEntity)

            // Act
            auditService.logAuthorizationDecision(
                userId = userId,
                groups = groups,
                roles = roles,
                requestedAction = "transaction:read",
                resourceType = "Transaction",
                resourceId = resourceId,
                rbacResult = AuthorizationResult.ALLOWED,
                abacResult = AuthorizationResult.ALLOWED,
                finalDecision = AuthorizationResult.ALLOWED,
                metadata = metadata,
            )

            // Assert
            verify(auditLogRepository).save(any())
        }

        @Test
        fun `should log authorization decision with minimal fields`() {
            // Arrange
            val userId = UUID.randomUUID()
            val savedEntity =
                AuthorizationAuditLogEntity(
                    userId = userId,
                    requestedAction = "transaction:read",
                    rbacResult = AuthorizationResult.DENIED,
                    finalDecision = AuthorizationResult.DENIED,
                )

            whenever(auditLogRepository.save(any())).thenReturn(savedEntity)

            // Act
            auditService.logAuthorizationDecision(
                userId = userId,
                requestedAction = "transaction:read",
                rbacResult = AuthorizationResult.DENIED,
                finalDecision = AuthorizationResult.DENIED,
            )

            // Assert
            verify(auditLogRepository).save(any())
        }

        @Test
        fun `should log authorization decision with null optional fields`() {
            // Arrange
            val userId = UUID.randomUUID()
            val savedEntity =
                AuthorizationAuditLogEntity(
                    userId = userId,
                    groups = null,
                    roles = null,
                    requestedAction = "transaction:read",
                    resourceType = null,
                    resourceId = null,
                    rbacResult = AuthorizationResult.ALLOWED,
                    abacResult = null,
                    finalDecision = AuthorizationResult.ALLOWED,
                    metadata = null,
                )

            whenever(auditLogRepository.save(any())).thenReturn(savedEntity)

            // Act
            auditService.logAuthorizationDecision(
                userId = userId,
                groups = null,
                roles = null,
                requestedAction = "transaction:read",
                resourceType = null,
                resourceId = null,
                rbacResult = AuthorizationResult.ALLOWED,
                abacResult = null,
                finalDecision = AuthorizationResult.ALLOWED,
                metadata = null,
            )

            // Assert
            verify(auditLogRepository).save(any())
        }

        @Test
        fun `should log NOT_EVALUATED result`() {
            // Arrange
            val userId = UUID.randomUUID()
            val savedEntity =
                AuthorizationAuditLogEntity(
                    userId = userId,
                    requestedAction = "transaction:read",
                    rbacResult = AuthorizationResult.NOT_EVALUATED,
                    abacResult = AuthorizationResult.NOT_EVALUATED,
                    finalDecision = AuthorizationResult.NOT_EVALUATED,
                )

            whenever(auditLogRepository.save(any())).thenReturn(savedEntity)

            // Act
            auditService.logAuthorizationDecision(
                userId = userId,
                requestedAction = "transaction:read",
                rbacResult = AuthorizationResult.NOT_EVALUATED,
                abacResult = AuthorizationResult.NOT_EVALUATED,
                finalDecision = AuthorizationResult.NOT_EVALUATED,
            )

            // Assert
            verify(auditLogRepository).save(any())
        }

        @Test
        fun `should log with empty groups and roles lists`() {
            // Arrange
            val userId = UUID.randomUUID()
            val savedEntity =
                AuthorizationAuditLogEntity(
                    userId = userId,
                    groups = emptyList(),
                    roles = emptyList(),
                    requestedAction = "transaction:read",
                    rbacResult = AuthorizationResult.ALLOWED,
                    finalDecision = AuthorizationResult.ALLOWED,
                )

            whenever(auditLogRepository.save(any())).thenReturn(savedEntity)

            // Act
            auditService.logAuthorizationDecision(
                userId = userId,
                groups = emptyList(),
                roles = emptyList(),
                requestedAction = "transaction:read",
                rbacResult = AuthorizationResult.ALLOWED,
                finalDecision = AuthorizationResult.ALLOWED,
            )

            // Assert
            verify(auditLogRepository).save(any())
        }
    }

    @Nested
    @DisplayName("findByUserId()")
    inner class FindByUserIdTests {
        @Test
        fun `should find audit logs for user`() {
            // Arrange
            val userId = UUID.randomUUID()
            val entity1 =
                AuthorizationAuditLogEntity(
                    userId = userId,
                    requestedAction = "transaction:read",
                    rbacResult = AuthorizationResult.ALLOWED,
                    finalDecision = AuthorizationResult.ALLOWED,
                )
            val entity2 =
                AuthorizationAuditLogEntity(
                    userId = userId,
                    requestedAction = "transaction:write",
                    rbacResult = AuthorizationResult.DENIED,
                    finalDecision = AuthorizationResult.DENIED,
                )

            whenever(auditLogRepository.findByUserId(userId)).thenReturn(listOf(entity1, entity2))

            // Act
            val result = auditService.findByUserId(userId)

            // Assert
            assertThat(result).hasSize(2)
            assertThat(result[0].requestedAction).isEqualTo("transaction:read")
            assertThat(result[1].requestedAction).isEqualTo("transaction:write")
            verify(auditLogRepository).findByUserId(userId)
        }

        @Test
        fun `should return empty list when no audit logs found`() {
            // Arrange
            val userId = UUID.randomUUID()
            whenever(auditLogRepository.findByUserId(userId)).thenReturn(emptyList())

            // Act
            val result = auditService.findByUserId(userId)

            // Assert
            assertThat(result).isEmpty()
            verify(auditLogRepository).findByUserId(userId)
        }
    }

    @Nested
    @DisplayName("findByUserIdAndTimestampBetween()")
    inner class FindByUserIdAndTimestampBetweenTests {
        @Test
        fun `should find audit logs within time range`() {
            // Arrange
            val userId = UUID.randomUUID()
            val startTime = Instant.now().minusSeconds(3600)
            val endTime = Instant.now()
            val entity =
                AuthorizationAuditLogEntity(
                    userId = userId,
                    requestedAction = "transaction:read",
                    rbacResult = AuthorizationResult.ALLOWED,
                    finalDecision = AuthorizationResult.ALLOWED,
                    timestamp = startTime.plusSeconds(1800),
                )

            whenever(
                auditLogRepository.findByUserIdAndTimestampBetween(
                    userId,
                    startTime,
                    endTime,
                ),
            ).thenReturn(listOf(entity))

            // Act
            val result = auditService.findByUserIdAndTimestampBetween(userId, startTime, endTime)

            // Assert
            assertThat(result).hasSize(1)
            assertThat(result[0].requestedAction).isEqualTo("transaction:read")
            verify(auditLogRepository).findByUserIdAndTimestampBetween(userId, startTime, endTime)
        }

        @Test
        fun `should return empty list when no logs in time range`() {
            // Arrange
            val userId = UUID.randomUUID()
            val startTime = Instant.now().minusSeconds(3600)
            val endTime = Instant.now()

            whenever(
                auditLogRepository.findByUserIdAndTimestampBetween(userId, startTime, endTime),
            ).thenReturn(emptyList())

            // Act
            val result = auditService.findByUserIdAndTimestampBetween(userId, startTime, endTime)

            // Assert
            assertThat(result).isEmpty()
        }
    }

    @Nested
    @DisplayName("findByResource()")
    inner class FindByResourceTests {
        @Test
        fun `should find audit logs for resource`() {
            // Arrange
            val resourceId = UUID.randomUUID()
            val entity =
                AuthorizationAuditLogEntity(
                    userId = UUID.randomUUID(),
                    requestedAction = "transaction:read",
                    resourceType = "Transaction",
                    resourceId = resourceId,
                    rbacResult = AuthorizationResult.ALLOWED,
                    finalDecision = AuthorizationResult.ALLOWED,
                )

            whenever(
                auditLogRepository.findByResourceTypeAndResourceId("Transaction", resourceId),
            ).thenReturn(listOf(entity))

            // Act
            val result = auditService.findByResource("Transaction", resourceId)

            // Assert
            assertThat(result).hasSize(1)
            assertThat(result[0].resourceType).isEqualTo("Transaction")
            assertThat(result[0].resourceId).isEqualTo(resourceId)
            verify(auditLogRepository).findByResourceTypeAndResourceId("Transaction", resourceId)
        }

        @Test
        fun `should return empty list when no logs found for resource`() {
            // Arrange
            val resourceId = UUID.randomUUID()

            whenever(
                auditLogRepository.findByResourceTypeAndResourceId("Transaction", resourceId),
            ).thenReturn(emptyList())

            // Act
            val result = auditService.findByResource("Transaction", resourceId)

            // Assert
            assertThat(result).isEmpty()
        }
    }
}
