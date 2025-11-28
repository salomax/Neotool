package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.security.domain.abac.AbacPolicy
import io.github.salomax.neotool.security.domain.abac.PolicyEffect
import io.github.salomax.neotool.security.repo.AbacPolicyRepository
import io.github.salomax.neotool.security.service.AbacPolicyService
import io.github.salomax.neotool.security.test.SecurityTestDataBuilders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.Optional
import java.util.UUID

@DisplayName("AbacPolicyService Unit Tests")
class AbacPolicyServiceTest {
    private lateinit var abacPolicyRepository: AbacPolicyRepository
    private lateinit var abacPolicyService: AbacPolicyService

    @BeforeEach
    fun setUp() {
        abacPolicyRepository = mock()
        abacPolicyService = AbacPolicyService(abacPolicyRepository)
    }

    @Nested
    @DisplayName("Find Operations")
    inner class FindOperationsTests {
        @Test
        fun `should find policy by id`() {
            // Arrange
            val policyId = UUID.randomUUID()
            val policyEntity =
                SecurityTestDataBuilders.abacPolicy(
                    id = policyId,
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                )
            whenever(abacPolicyRepository.findById(policyId)).thenReturn(Optional.of(policyEntity))

            // Act
            val result = abacPolicyService.findById(policyId)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result?.id).isEqualTo(policyId)
            assertThat(result?.name).isEqualTo("test-policy")
            assertThat(result?.effect).isEqualTo(PolicyEffect.ALLOW)
            verify(abacPolicyRepository).findById(policyId)
        }

        @Test
        fun `should return null when policy not found by id`() {
            // Arrange
            val policyId = UUID.randomUUID()
            whenever(abacPolicyRepository.findById(policyId)).thenReturn(Optional.empty())

            // Act
            val result = abacPolicyService.findById(policyId)

            // Assert
            assertThat(result).isNull()
            verify(abacPolicyRepository).findById(policyId)
        }

        @Test
        fun `should find policy by name`() {
            // Arrange
            val policyName = "test-policy"
            val policyEntity = SecurityTestDataBuilders.abacPolicy(name = policyName)
            whenever(abacPolicyRepository.findByName(policyName)).thenReturn(Optional.of(policyEntity))

            // Act
            val result = abacPolicyService.findByName(policyName)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result?.name).isEqualTo(policyName)
            verify(abacPolicyRepository).findByName(policyName)
        }

        @Test
        fun `should return null when policy not found by name`() {
            // Arrange
            val policyName = "nonexistent-policy"
            whenever(abacPolicyRepository.findByName(policyName)).thenReturn(Optional.empty())

            // Act
            val result = abacPolicyService.findByName(policyName)

            // Assert
            assertThat(result).isNull()
            verify(abacPolicyRepository).findByName(policyName)
        }

        @Test
        fun `should find all policies`() {
            // Arrange
            val policy1 = SecurityTestDataBuilders.abacPolicy(name = "policy-1")
            val policy2 = SecurityTestDataBuilders.abacPolicy(name = "policy-2")
            whenever(abacPolicyRepository.findAll()).thenReturn(listOf(policy1, policy2))

            // Act
            val result = abacPolicyService.findAll()

            // Assert
            assertThat(result).hasSize(2)
            assertThat(result.map { it.name }).containsExactlyInAnyOrder("policy-1", "policy-2")
            verify(abacPolicyRepository).findAll()
        }

        @Test
        fun `should return empty list when no policies exist`() {
            // Arrange
            whenever(abacPolicyRepository.findAll()).thenReturn(emptyList())

            // Act
            val result = abacPolicyService.findAll()

            // Assert
            assertThat(result).isEmpty()
            verify(abacPolicyRepository).findAll()
        }

        @Test
        fun `should find only active policies`() {
            // Arrange
            val activePolicy1 = SecurityTestDataBuilders.abacPolicy(name = "active-1", isActive = true)
            val activePolicy2 = SecurityTestDataBuilders.abacPolicy(name = "active-2", isActive = true)
            val inactivePolicy = SecurityTestDataBuilders.abacPolicy(name = "inactive", isActive = false)

            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(activePolicy1, activePolicy2))

            // Act
            val result = abacPolicyService.findActivePolicies()

            // Assert
            assertThat(result).hasSize(2)
            assertThat(result.map { it.name }).containsExactlyInAnyOrder("active-1", "active-2")
            assertThat(result.all { it.isActive }).isTrue()
            verify(abacPolicyRepository).findActivePolicies()
        }
    }

    @Nested
    @DisplayName("Create Operations")
    inner class CreateOperationsTests {
        @Test
        fun `should create policy successfully`() {
            // Arrange
            val policy =
                AbacPolicy(
                    name = "test-policy",
                    description = "Test policy description",
                    effect = PolicyEffect.ALLOW,
                    condition = """{"eq": {"subject.userId": "test"}}""",
                    version = 1,
                    isActive = true,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            val savedEntity = SecurityTestDataBuilders.abacPolicy(name = "test-policy")
            whenever(abacPolicyRepository.save(any())).thenReturn(savedEntity)

            // Act
            val result = abacPolicyService.create(policy)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.name).isEqualTo("test-policy")
            verify(abacPolicyRepository).save(any())
        }
    }

    @Nested
    @DisplayName("Update Operations")
    inner class UpdateOperationsTests {
        @Test
        fun `should update policy successfully`() {
            // Arrange
            val policyId = UUID.randomUUID()
            val existingEntity =
                SecurityTestDataBuilders.abacPolicy(
                    id = policyId,
                    name = "old-policy",
                    effect = PolicyEffect.ALLOW,
                )
            val updatedPolicy =
                AbacPolicy(
                    id = policyId,
                    name = "new-policy",
                    description = "Updated description",
                    effect = PolicyEffect.DENY,
                    condition = """{"eq": {"subject.userId": "updated"}}""",
                    version = 2,
                    isActive = false,
                    updatedAt = Instant.now(),
                )
            val savedEntity =
                SecurityTestDataBuilders.abacPolicy(
                    id = policyId,
                    name = "new-policy",
                    effect = PolicyEffect.DENY,
                )

            whenever(abacPolicyRepository.findById(policyId)).thenReturn(Optional.of(existingEntity))
            whenever(abacPolicyRepository.update(any())).thenReturn(savedEntity)

            // Act
            val result = abacPolicyService.update(updatedPolicy)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.id).isEqualTo(policyId)
            assertThat(result.name).isEqualTo("new-policy")
            verify(abacPolicyRepository).findById(policyId)
            verify(abacPolicyRepository).update(any())
        }

        @Test
        fun `should throw exception when updating policy without id`() {
            // Arrange
            val policy =
                AbacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = """{"eq": {"subject.userId": "test"}}""",
                    updatedAt = Instant.now(),
                )

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                abacPolicyService.update(policy)
            }.also { exception ->
                assertThat(exception.message).contains("Policy ID is required")
            }

            verify(abacPolicyRepository, never()).findById(any())
            verify(abacPolicyRepository, never()).update(any())
        }

        @Test
        fun `should throw exception when updating non-existent policy`() {
            // Arrange
            val policyId = UUID.randomUUID()
            val policy =
                AbacPolicy(
                    id = policyId,
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = """{"eq": {"subject.userId": "test"}}""",
                    updatedAt = Instant.now(),
                )
            whenever(abacPolicyRepository.findById(policyId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                abacPolicyService.update(policy)
            }.also { exception ->
                assertThat(exception.message).contains("Policy not found")
            }

            verify(abacPolicyRepository).findById(policyId)
            verify(abacPolicyRepository, never()).update(any())
        }
    }

    @Nested
    @DisplayName("Delete Operations")
    inner class DeleteOperationsTests {
        @Test
        fun `should delete policy successfully`() {
            // Arrange
            val policyId = UUID.randomUUID()

            // Act
            abacPolicyService.delete(policyId)

            // Assert
            verify(abacPolicyRepository).deleteById(policyId)
        }
    }
}
