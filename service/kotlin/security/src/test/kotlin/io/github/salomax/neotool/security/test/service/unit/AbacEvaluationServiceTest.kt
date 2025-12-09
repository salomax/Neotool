package io.github.salomax.neotool.security.test.service.unit

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.security.domain.abac.PolicyEffect
import io.github.salomax.neotool.security.repo.AbacPolicyRepository
import io.github.salomax.neotool.security.service.AbacEvaluationService
import io.github.salomax.neotool.security.test.SecurityTestDataBuilders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@DisplayName("AbacEvaluationService Unit Tests")
class AbacEvaluationServiceTest {
    private lateinit var abacPolicyRepository: AbacPolicyRepository
    private lateinit var objectMapper: ObjectMapper
    private lateinit var abacEvaluationService: AbacEvaluationService

    @BeforeEach
    fun setUp() {
        abacPolicyRepository = mock()
        objectMapper = ObjectMapper()
        abacEvaluationService = AbacEvaluationService(abacPolicyRepository, objectMapper)
    }

    @Nested
    @DisplayName("Policy Evaluation - Basic Operators")
    inner class BasicOperatorsTests {
        @Test
        fun `should evaluate eq operator correctly`() {
            // Arrange
            val condition = """{"eq": {"subject.userId": "user-123"}}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = condition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes = mapOf("userId" to "user-123")
            val resourceAttributes = null
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isEqualTo(PolicyEffect.ALLOW)
            assertThat(result.matchedPolicies).hasSize(1)
            assertThat(result.matchedPolicies[0].name).isEqualTo("test-policy")
        }

        @Test
        fun `should evaluate ne operator correctly`() {
            // Arrange
            val condition = """{"ne": {"subject.userId": "user-123"}}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = condition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes = mapOf("userId" to "user-456") // Different user
            val resourceAttributes = null
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isEqualTo(PolicyEffect.ALLOW)
            assertThat(result.matchedPolicies).hasSize(1)
        }

        @Test
        fun `should evaluate in operator correctly`() {
            // Arrange
            val condition = """{"in": {"subject.roles": ["admin", "editor"]}}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = condition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes = mapOf("roles" to listOf("admin", "viewer"))
            val resourceAttributes = null
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isEqualTo(PolicyEffect.ALLOW)
            assertThat(result.matchedPolicies).hasSize(1)
        }

        @Test
        fun `should evaluate gt operator correctly`() {
            // Arrange
            val condition = """{"gt": {"resource.amount": 1000}}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = condition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes = mapOf<String, Any>()
            val resourceAttributes = mapOf("amount" to 1500)
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isEqualTo(PolicyEffect.ALLOW)
        }

        @Test
        fun `should evaluate gte operator correctly`() {
            // Arrange
            val condition = """{"gte": {"resource.amount": 1000}}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = condition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes = mapOf<String, Any>()
            val resourceAttributes = mapOf("amount" to 1000) // Equal to threshold
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isEqualTo(PolicyEffect.ALLOW)
        }

        @Test
        fun `should evaluate lt operator correctly`() {
            // Arrange
            val condition = """{"lt": {"resource.amount": 1000}}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = condition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes = mapOf<String, Any>()
            val resourceAttributes = mapOf("amount" to 500)
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isEqualTo(PolicyEffect.ALLOW)
        }

        @Test
        fun `should evaluate lte operator correctly`() {
            // Arrange
            val condition = """{"lte": {"resource.amount": 1000}}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = condition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes = mapOf<String, Any>()
            val resourceAttributes = mapOf("amount" to 1000) // Equal to threshold
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isEqualTo(PolicyEffect.ALLOW)
        }
    }

    @Nested
    @DisplayName("Policy Evaluation - Logical Operators")
    inner class LogicalOperatorsTests {
        @Test
        fun `should evaluate AND operator correctly`() {
            // Arrange
            val condition =
                """{"and": [{"eq": {"subject.userId": "user-123"}}, {"eq": {"resource.type": "transaction"}}]}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = condition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes = mapOf("userId" to "user-123")
            val resourceAttributes = mapOf("type" to "transaction")
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isEqualTo(PolicyEffect.ALLOW)
            assertThat(result.matchedPolicies).hasSize(1)
        }

        @Test
        fun `should deny when AND condition fails`() {
            // Arrange
            val condition =
                """{"and": [{"eq": {"subject.userId": "user-123"}}, {"eq": {"resource.type": "transaction"}}]}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = condition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes = mapOf("userId" to "user-123")
            val resourceAttributes = mapOf("type" to "account") // Different type
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isNull() // No matching policies
            assertThat(result.matchedPolicies).isEmpty()
        }

        @Test
        fun `should evaluate OR operator correctly`() {
            // Arrange
            val condition =
                """{"or": [{"eq": {"subject.userId": "user-123"}}, {"eq": {"subject.userId": "user-456"}}]}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = condition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes = mapOf("userId" to "user-456") // Matches second condition
            val resourceAttributes = null
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isEqualTo(PolicyEffect.ALLOW)
            assertThat(result.matchedPolicies).hasSize(1)
        }

        @Test
        fun `should evaluate NOT operator correctly`() {
            // Arrange
            val condition = """{"not": {"eq": {"subject.userId": "user-123"}}}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = condition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes = mapOf("userId" to "user-456") // Different user
            val resourceAttributes = null
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isEqualTo(PolicyEffect.ALLOW)
            assertThat(result.matchedPolicies).hasSize(1)
        }

        @Test
        fun `should evaluate nested logical operators`() {
            // Arrange
            val condition =
                """{"and": [{"eq": {"subject.userId": "user-123"}}, """ +
                    """{"or": [{"eq": {"resource.ownerId": "user-123"}}, """ +
                    """{"in": {"subject.groups": ["group1"]}}]}]}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = condition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes =
                mapOf(
                    "userId" to "user-123",
                    "groups" to listOf("group1", "group2"),
                )
            val resourceAttributes = mapOf("ownerId" to "user-999") // Doesn't match, but group matches
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isEqualTo(PolicyEffect.ALLOW)
            assertThat(result.matchedPolicies).hasSize(1)
        }
    }

    @Nested
    @DisplayName("Policy Evaluation - Attribute Path Resolution")
    inner class AttributePathResolutionTests {
        @Test
        fun `should resolve nested attribute paths`() {
            // Arrange
            val condition = """{"eq": {"subject.userId": "user-123"}}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = condition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes = mapOf("userId" to "user-123")
            val resourceAttributes = null
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isEqualTo(PolicyEffect.ALLOW)
        }

        @Test
        fun `should resolve resource attribute paths`() {
            // Arrange
            val condition = """{"eq": {"resource.ownerId": "user-123"}}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = condition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes = mapOf<String, Any>()
            val resourceAttributes = mapOf("ownerId" to "user-123")
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isEqualTo(PolicyEffect.ALLOW)
        }

        @Test
        fun `should resolve context attribute paths`() {
            // Arrange
            val condition = """{"eq": {"context.ipAddress": "192.168.1.1"}}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = condition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes = mapOf<String, Any>()
            val resourceAttributes = null
            val contextAttributes = mapOf("ipAddress" to "192.168.1.1")

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isEqualTo(PolicyEffect.ALLOW)
        }
    }

    @Nested
    @DisplayName("Policy Evaluation - Explicit Deny Override")
    inner class ExplicitDenyOverrideTests {
        @Test
        fun `should deny when any policy has DENY effect`() {
            // Arrange
            val allowPolicy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "allow-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = """{"eq": {"subject.userId": "user-123"}}""",
                )
            val denyPolicy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "deny-policy",
                    effect = PolicyEffect.DENY,
                    condition = """{"eq": {"subject.userId": "user-123"}}""",
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(allowPolicy, denyPolicy))

            val subjectAttributes = mapOf("userId" to "user-123")
            val resourceAttributes = null
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isEqualTo(PolicyEffect.DENY)
            assertThat(result.matchedPolicies).hasSize(2)
            assertThat(result.reason).contains("Access denied by ABAC policy")
        }

        @Test
        fun `should allow when only ALLOW policies match`() {
            // Arrange
            val allowPolicy1 =
                SecurityTestDataBuilders.abacPolicy(
                    name = "allow-policy-1",
                    effect = PolicyEffect.ALLOW,
                    condition = """{"eq": {"subject.userId": "user-123"}}""",
                )
            val allowPolicy2 =
                SecurityTestDataBuilders.abacPolicy(
                    name = "allow-policy-2",
                    effect = PolicyEffect.ALLOW,
                    condition = """{"eq": {"resource.type": "transaction"}}""",
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(allowPolicy1, allowPolicy2))

            val subjectAttributes = mapOf("userId" to "user-123")
            val resourceAttributes = mapOf("type" to "transaction")
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isEqualTo(PolicyEffect.ALLOW)
            assertThat(result.matchedPolicies).hasSize(2)
        }
    }

    @Nested
    @DisplayName("Policy Evaluation - Edge Cases")
    inner class EdgeCasesTests {
        @Test
        fun `should return null when no policies match`() {
            // Arrange
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = """{"eq": {"subject.userId": "user-123"}}""",
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes = mapOf("userId" to "user-456") // Doesn't match
            val resourceAttributes = null
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isNull()
            assertThat(result.matchedPolicies).isEmpty()
            assertThat(result.reason).contains("No matching ABAC policies")
        }

        @Test
        fun `should handle invalid policy syntax gracefully`() {
            // Arrange
            val invalidPolicy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "invalid-policy",
                    effect = PolicyEffect.ALLOW,
                    // Invalid condition
                    condition = """{"invalid": "syntax"}""",
                )
            val validPolicy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "valid-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = """{"eq": {"subject.userId": "user-123"}}""",
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(invalidPolicy, validPolicy))

            val subjectAttributes = mapOf("userId" to "user-123")
            val resourceAttributes = null
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            // Should still evaluate valid policy even if invalid one fails
            assertThat(result.decision).isEqualTo(PolicyEffect.ALLOW)
            assertThat(result.matchedPolicies).hasSize(1)
            assertThat(result.matchedPolicies[0].name).isEqualTo("valid-policy")
        }

        @Test
        fun `should handle missing attributes gracefully`() {
            // Arrange
            val condition = """{"eq": {"subject.nonexistent": "value"}}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = condition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes = mapOf("userId" to "user-123") // Missing nonexistent attribute
            val resourceAttributes = null
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isNull()
            assertThat(result.matchedPolicies).isEmpty()
        }

        @Test
        fun `should return null when no active policies exist`() {
            // Arrange
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(emptyList())

            val subjectAttributes = mapOf("userId" to "user-123")
            val resourceAttributes = null
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isNull()
            assertThat(result.matchedPolicies).isEmpty()
            assertThat(result.reason).contains("No matching ABAC policies")
        }

        @Test
        fun `should only evaluate active policies`() {
            // Arrange
            val activePolicy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "active-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = """{"eq": {"subject.userId": "user-123"}}""",
                    isActive = true,
                )
            val inactivePolicy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "inactive-policy",
                    effect = PolicyEffect.DENY,
                    condition = """{"eq": {"subject.userId": "user-123"}}""",
                    // Inactive
                    isActive = false,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(activePolicy))

            val subjectAttributes = mapOf("userId" to "user-123")
            val resourceAttributes = null
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isEqualTo(PolicyEffect.ALLOW)
            assertThat(result.matchedPolicies).hasSize(1)
            assertThat(result.matchedPolicies[0].name).isEqualTo("active-policy")
        }
    }

    @Nested
    @DisplayName("Security - Condition Size Limits")
    inner class ConditionSizeLimitTests {
        @Test
        fun `should reject condition exceeding maximum size`() {
            // Arrange
            val oversizedCondition = "a".repeat(10 * 1024 + 1) // Exceeds 10KB
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "oversized-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = """{"eq": {"subject.userId": "$oversizedCondition"}}""",
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes = mapOf("userId" to "user-123")
            val resourceAttributes = null
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isNull()
            assertThat(result.matchedPolicies).isEmpty()
        }

        @Test
        fun `should accept condition within maximum size`() {
            // Arrange
            val validCondition = """{"eq": {"subject.userId": "user-123"}}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "valid-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = validCondition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes = mapOf("userId" to "user-123")
            val resourceAttributes = null
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isEqualTo(PolicyEffect.ALLOW)
            assertThat(result.matchedPolicies).hasSize(1)
        }
    }

    @Nested
    @DisplayName("Security - Recursion Depth Limits")
    inner class RecursionDepthLimitTests {
        @Test
        fun `should reject condition exceeding maximum recursion depth`() {
            // Arrange
            // Create a deeply nested condition (11 levels deep, exceeding max of 10)
            var condition = """{"eq": {"subject.userId": "user-123"}}"""
            for (i in 1..11) {
                condition = """{"and": [$condition]}"""
            }
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "deep-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = condition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes = mapOf("userId" to "user-123")
            val resourceAttributes = null
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isNull()
            assertThat(result.matchedPolicies).isEmpty()
        }

        @Test
        fun `should accept condition within maximum recursion depth`() {
            // Arrange
            // Create a nested condition (10 levels deep, at max)
            var condition = """{"eq": {"subject.userId": "user-123"}}"""
            for (i in 1..10) {
                condition = """{"and": [$condition]}"""
            }
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "deep-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = condition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes = mapOf("userId" to "user-123")
            val resourceAttributes = null
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isEqualTo(PolicyEffect.ALLOW)
            assertThat(result.matchedPolicies).hasSize(1)
        }
    }

    @Nested
    @DisplayName("Security - Error Logging")
    inner class ErrorLoggingTests {
        @Test
        fun `should not log full condition in error messages`() {
            // Arrange
            val sensitiveCondition = """{"eq": {"subject.secret": "sensitive-data-12345"}}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = sensitiveCondition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes = mapOf("userId" to "user-123")
            val resourceAttributes = null
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            // The result should not contain the sensitive condition
            assertThat(result.reason).doesNotContain("sensitive-data-12345")
            assertThat(result.reason).doesNotContain(sensitiveCondition)
        }
    }

    @Nested
    @DisplayName("Comparison Operators - Edge Cases")
    inner class ComparisonOperatorEdgeCasesTests {
        @Test
        fun `should return false when comparison node has multiple fields`() {
            // Arrange
            val condition = """{"eq": {"subject.userId": "user-123", "subject.email": "test@example.com"}}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = condition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes = mapOf("userId" to "user-123")
            val resourceAttributes = null
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isNull()
            assertThat(result.matchedPolicies).isEmpty()
        }

        @Test
        fun `should handle Float numbers in gt comparison`() {
            // Arrange
            val condition = """{"gt": {"resource.amount": 1000.5}}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = condition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes = mapOf<String, Any>()
            val resourceAttributes = mapOf("amount" to 1500.75f)
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isEqualTo(PolicyEffect.ALLOW)
        }

        @Test
        fun `should handle Double numbers in gte comparison`() {
            // Arrange
            val condition = """{"gte": {"resource.amount": 1000.5}}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = condition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes = mapOf<String, Any>()
            val resourceAttributes = mapOf("amount" to 1000.5)
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isEqualTo(PolicyEffect.ALLOW)
        }

        @Test
        fun `should handle Int numbers in lt comparison`() {
            // Arrange
            val condition = """{"lt": {"resource.amount": 1000}}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = condition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes = mapOf<String, Any>()
            val resourceAttributes = mapOf("amount" to 500)
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isEqualTo(PolicyEffect.ALLOW)
        }

        @Test
        fun `should handle Long numbers in lte comparison`() {
            // Arrange
            val condition = """{"lte": {"resource.amount": 1000}}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = condition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes = mapOf<String, Any>()
            val resourceAttributes = mapOf("amount" to 1000L)
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isEqualTo(PolicyEffect.ALLOW)
        }

        @Test
        fun `should handle null values in comparison`() {
            // Arrange
            val condition = """{"eq": {"subject.userId": "user-123"}}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = condition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            @Suppress("UNCHECKED_CAST")
            val subjectAttributes = mapOf("userId" to null) as Map<String, Any>
            val resourceAttributes = null
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isNull()
            assertThat(result.matchedPolicies).isEmpty()
        }
    }

    @Nested
    @DisplayName("In Operator - Edge Cases")
    inner class InOperatorEdgeCasesTests {
        @Test
        fun `should return false when in operation has non-array value`() {
            // Arrange
            val condition = """{"in": {"subject.roles": "admin"}}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = condition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes = mapOf("roles" to listOf("admin"))
            val resourceAttributes = null
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isNull()
            assertThat(result.matchedPolicies).isEmpty()
        }

        @Test
        fun `should handle empty collections in in operation`() {
            // Arrange
            val condition = """{"in": {"subject.roles": ["admin", "editor"]}}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = condition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes = mapOf("roles" to emptyList<String>())
            val resourceAttributes = null
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isNull()
            assertThat(result.matchedPolicies).isEmpty()
        }

        @Test
        fun `should handle null values in collections for in operation`() {
            // Arrange
            val condition = """{"in": {"subject.roles": ["admin", "editor"]}}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = condition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes = mapOf("roles" to listOf("admin", null))
            val resourceAttributes = null
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isEqualTo(PolicyEffect.ALLOW)
        }

        @Test
        fun `should return false when in operation has multiple fields`() {
            // Arrange
            val condition = """{"in": {"subject.roles": ["admin"], "subject.groups": ["group1"]}}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = condition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes = mapOf("roles" to listOf("admin"))
            val resourceAttributes = null
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isNull()
            assertThat(result.matchedPolicies).isEmpty()
        }
    }

    @Nested
    @DisplayName("Attribute Path - Edge Cases")
    inner class AttributePathEdgeCasesTests {
        @Test
        fun `should handle empty path string`() {
            // Arrange
            val condition = """{"eq": {"": "value"}}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = condition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes = mapOf("userId" to "user-123")
            val resourceAttributes = null
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isNull()
            assertThat(result.matchedPolicies).isEmpty()
        }

        @Test
        fun `should handle deeply nested paths with 3 levels`() {
            // Arrange
            val condition = """{"eq": {"subject.user.profile.email": "test@example.com"}}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = condition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes =
                mapOf(
                    "user" to
                        mapOf(
                            "profile" to
                                mapOf("email" to "test@example.com"),
                        ),
                )
            val resourceAttributes = null
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isEqualTo(PolicyEffect.ALLOW)
        }

        @Test
        fun `should handle deeply nested paths with 4 levels`() {
            // Arrange
            val condition = """{"eq": {"subject.user.profile.settings.theme": "dark"}}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = condition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes =
                mapOf(
                    "user" to
                        mapOf(
                            "profile" to
                                mapOf(
                                    "settings" to
                                        mapOf("theme" to "dark"),
                                ),
                        ),
                )
            val resourceAttributes = null
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isEqualTo(PolicyEffect.ALLOW)
        }

        @Test
        fun `should handle missing intermediate path in nested structure`() {
            // Arrange
            val condition = """{"eq": {"subject.user.profile.email": "test@example.com"}}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = condition,
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy))

            val subjectAttributes = mapOf("user" to mapOf("name" to "Test User"))
            val resourceAttributes = null
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            assertThat(result.decision).isNull()
            assertThat(result.matchedPolicies).isEmpty()
        }
    }

    @Nested
    @DisplayName("Error Handling - Edge Cases")
    inner class ErrorHandlingEdgeCasesTests {
        @Test
        fun `should handle unknown operator gracefully`() {
            // Arrange
            val condition = """{"unknown": {"subject.userId": "user-123"}}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "test-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = condition,
                )
            val validPolicy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "valid-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = """{"eq": {"subject.userId": "user-123"}}""",
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy, validPolicy))

            val subjectAttributes = mapOf("userId" to "user-123")
            val resourceAttributes = null
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            // Should still evaluate valid policy even if unknown operator fails
            assertThat(result.decision).isEqualTo(PolicyEffect.ALLOW)
            assertThat(result.matchedPolicies).hasSize(1)
            assertThat(result.matchedPolicies[0].name).isEqualTo("valid-policy")
        }

        @Test
        fun `should handle malformed JSON condition gracefully`() {
            // Arrange
            val malformedCondition = """{"eq": {"subject.userId": "user-123"}}}"""
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "malformed-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = malformedCondition,
                )
            val validPolicy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "valid-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = """{"eq": {"subject.userId": "user-123"}}""",
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy, validPolicy))

            val subjectAttributes = mapOf("userId" to "user-123")
            val resourceAttributes = null
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            // Should still evaluate valid policy even if malformed one fails
            assertThat(result.decision).isEqualTo(PolicyEffect.ALLOW)
            assertThat(result.matchedPolicies).hasSize(1)
            assertThat(result.matchedPolicies[0].name).isEqualTo("valid-policy")
        }

        @Test
        fun `should handle completely invalid JSON condition`() {
            // Arrange
            val invalidCondition = "not json at all"
            val policy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "invalid-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = invalidCondition,
                )
            val validPolicy =
                SecurityTestDataBuilders.abacPolicy(
                    name = "valid-policy",
                    effect = PolicyEffect.ALLOW,
                    condition = """{"eq": {"subject.userId": "user-123"}}""",
                )
            whenever(abacPolicyRepository.findActivePolicies()).thenReturn(listOf(policy, validPolicy))

            val subjectAttributes = mapOf("userId" to "user-123")
            val resourceAttributes = null
            val contextAttributes = null

            // Act
            val result =
                abacEvaluationService.evaluatePolicies(
                    subjectAttributes,
                    resourceAttributes,
                    contextAttributes,
                )

            // Assert
            // Should still evaluate valid policy even if invalid one fails
            assertThat(result.decision).isEqualTo(PolicyEffect.ALLOW)
            assertThat(result.matchedPolicies).hasSize(1)
            assertThat(result.matchedPolicies[0].name).isEqualTo("valid-policy")
        }
    }
}
