package io.github.salomax.neotool.security.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.security.domain.abac.AbacPolicy
import io.github.salomax.neotool.security.domain.abac.PolicyEffect
import io.github.salomax.neotool.security.repo.AbacPolicyRepository
import jakarta.inject.Singleton
import mu.KotlinLogging

/**
 * Service for evaluating ABAC policies.
 * Supports JSON-based condition evaluation with AND/OR logical operators.
 */
@Singleton
class AbacEvaluationService(
    private val abacPolicyRepository: AbacPolicyRepository,
    private val objectMapper: ObjectMapper,
) {
    private val logger = KotlinLogging.logger {}

    companion object {
        private const val MAX_CONDITION_SIZE = 10 * 1024 // 10KB
        private const val MAX_RECURSION_DEPTH = 10
    }

    /**
     * Evaluate all active ABAC policies for a given context.
     *
     * @param subjectAttributes Attributes of the subject (user, groups, roles, etc.)
     * @param resourceAttributes Attributes of the resource (type, id, owner, etc.)
     * @param contextAttributes Additional context (time, IP, etc.)
     * @return AbacEvaluationResult with decision and matched policies
     */
    fun evaluatePolicies(
        subjectAttributes: Map<String, Any>,
        resourceAttributes: Map<String, Any>? = null,
        contextAttributes: Map<String, Any>? = null,
    ): AbacEvaluationResult {
        val activePolicies = abacPolicyRepository.findActivePolicies().map { it.toDomain() }
        val matchedPolicies = mutableListOf<AbacPolicy>()
        var hasDeny = false
        var hasAllow = false

        // Combine all attributes for evaluation
        val allAttributes = mutableMapOf<String, Any>()
        allAttributes["subject"] = subjectAttributes
        resourceAttributes?.let { allAttributes["resource"] = it }
        contextAttributes?.let { allAttributes["context"] = it }

        for (policy in activePolicies) {
            try {
                val matches = evaluateCondition(policy.condition, allAttributes)
                if (matches) {
                    matchedPolicies.add(policy)
                    when (policy.effect) {
                        PolicyEffect.ALLOW -> hasAllow = true
                        PolicyEffect.DENY -> hasDeny = true
                    }
                }
            } catch (e: Exception) {
                logger.warn { "Error evaluating policy ${policy.name}: ${e.javaClass.simpleName}" }
                // Continue evaluating other policies even if one fails
            }
        }

        // Explicit deny overrides allow
        val decision =
            if (hasDeny) {
                PolicyEffect.DENY
            } else if (hasAllow) {
                PolicyEffect.ALLOW
            } else {
                null // No matching policies
            }

        return AbacEvaluationResult(
            decision = decision,
            matchedPolicies = matchedPolicies,
            reason =
                when {
                    hasDeny -> "Access denied by ABAC policy"
                    hasAllow -> "Access allowed by ABAC policy"
                    else -> "No matching ABAC policies"
                },
        )
    }

    /**
     * Evaluate a single policy condition.
     * Supports JSON format with AND/OR operators and attribute comparisons.
     *
     * Example condition format:
     * {
     *   "and": [
     *     {"eq": {"subject.userId": "123"}},
     *     {"or": [
     *       {"eq": {"resource.ownerId": "123"}},
     *       {"in": {"subject.groups": ["group1", "group2"]}}
     *     ]}
     *   ]
     * }
     */
    private fun evaluateCondition(
        condition: String,
        attributes: Map<String, Any>,
    ): Boolean {
        return try {
            // Validate condition size to prevent DoS attacks
            if (condition.length > MAX_CONDITION_SIZE) {
                logger.warn { "Policy condition exceeds maximum size of $MAX_CONDITION_SIZE bytes" }
                return false
            }

            // Use JsonParser to validate entire input is consumed
            return objectMapper.factory.createParser(condition).use { parser ->
                val conditionNode: JsonNode = objectMapper.readTree(parser)
                // Check if there's any remaining content after parsing
                if (parser.nextToken() != null) {
                    logger.debug { "Malformed JSON: extra content after root object" }
                    false
                } else {
                    evaluateConditionNode(conditionNode, attributes, depth = 0)
                }
            }
        } catch (e: Exception) {
            logger.debug(e) { "Failed to parse condition" }
            false
        }
    }

    /**
     * Recursively evaluate a condition node.
     *
     * @param node The JSON node to evaluate
     * @param attributes The attribute map for evaluation
     * @param depth Current recursion depth (to prevent stack overflow)
     * @return True if condition matches, false otherwise
     */
    private fun evaluateConditionNode(
        node: JsonNode,
        attributes: Map<String, Any>,
        depth: Int,
    ): Boolean {
        // Prevent stack overflow from deeply nested conditions
        if (depth > MAX_RECURSION_DEPTH) {
            logger.warn { "Condition recursion depth exceeded maximum of $MAX_RECURSION_DEPTH" }
            return false
        }

        return when {
            node.has("and") -> {
                val conditions = node.get("and")
                conditions.all { evaluateConditionNode(it, attributes, depth + 1) }
            }
            node.has("or") -> {
                val conditions = node.get("or")
                conditions.any { evaluateConditionNode(it, attributes, depth + 1) }
            }
            node.has("not") -> {
                !evaluateConditionNode(node.get("not"), attributes, depth + 1)
            }
            node.has("eq") -> {
                evaluateComparison(node.get("eq"), attributes) { a, b -> a == b }
            }
            node.has("ne") -> {
                evaluateComparison(node.get("ne"), attributes) { a, b -> a != b }
            }
            node.has("in") -> {
                evaluateIn(node.get("in"), attributes)
            }
            node.has("gt") -> {
                evaluateComparison(
                    node.get("gt"),
                    attributes,
                ) { a, b -> (a as? Number)?.toDouble() ?: 0.0 > (b as? Number)?.toDouble() ?: 0.0 }
            }
            node.has("gte") -> {
                evaluateComparison(
                    node.get("gte"),
                    attributes,
                ) { a, b -> (a as? Number)?.toDouble() ?: 0.0 >= (b as? Number)?.toDouble() ?: 0.0 }
            }
            node.has("lt") -> {
                evaluateComparison(
                    node.get("lt"),
                    attributes,
                ) { a, b -> (a as? Number)?.toDouble() ?: 0.0 < (b as? Number)?.toDouble() ?: 0.0 }
            }
            node.has("lte") -> {
                evaluateComparison(
                    node.get("lte"),
                    attributes,
                ) { a, b -> (a as? Number)?.toDouble() ?: 0.0 <= (b as? Number)?.toDouble() ?: 0.0 }
            }
            else -> {
                logger.warn { "Unknown condition operator: ${node.fieldNames().asSequence().firstOrNull()}" }
                false
            }
        }
    }

    /**
     * Evaluate a comparison operation (eq, ne, gt, etc.).
     */
    private fun evaluateComparison(
        comparisonNode: JsonNode,
        attributes: Map<String, Any>,
        comparator: (Any?, Any?) -> Boolean,
    ): Boolean {
        val fieldNames = comparisonNode.fieldNames().asSequence().toList()
        if (fieldNames.size != 1) {
            logger.warn { "Comparison should have exactly one field, got: $fieldNames" }
            return false
        }

        val attributePath = fieldNames[0]
        val expectedValue = comparisonNode.get(attributePath)

        val actualValue = getAttributeValue(attributePath, attributes)
        val expected =
            when {
                expectedValue.isTextual -> expectedValue.asText()
                expectedValue.isNumber -> expectedValue.asLong()
                expectedValue.isBoolean -> expectedValue.asBoolean()
                else -> expectedValue.toString()
            }

        return comparator(actualValue, expected)
    }

    /**
     * Evaluate an "in" operation (check if value is in array).
     * Supports checking if a single value or any element of a collection is in the array.
     */
    private fun evaluateIn(
        inNode: JsonNode,
        attributes: Map<String, Any>,
    ): Boolean {
        val fieldNames = inNode.fieldNames().asSequence().toList()
        if (fieldNames.size != 1) {
            logger.warn { "In operation should have exactly one field, got: $fieldNames" }
            return false
        }

        val attributePath = fieldNames[0]
        val arrayNode = inNode.get(attributePath)

        if (!arrayNode.isArray) {
            logger.warn { "In operation expects an array value" }
            return false
        }

        val actualValue = getAttributeValue(attributePath, attributes)
        val arrayValues = arrayNode.map { it.asText() }

        // Handle collections: check if any element in the collection is in the array
        return when (actualValue) {
            is Collection<*> -> {
                actualValue.any { element ->
                    arrayValues.contains(element?.toString())
                }
            }
            else -> {
                arrayValues.contains(actualValue?.toString())
            }
        }
    }

    /**
     * Get attribute value by path (e.g., "subject.userId" or "resource.ownerId").
     */
    private fun getAttributeValue(
        path: String,
        attributes: Map<String, Any>,
    ): Any? {
        val parts = path.split(".")
        if (parts.isEmpty()) return null

        var current: Any? = attributes[parts[0]]
        for (i in 1 until parts.size) {
            current =
                when (current) {
                    is Map<*, *> ->
                        @Suppress("UNCHECKED_CAST")
                        (current as Map<String, Any>)[parts[i]]
                    else -> null
                }
            if (current == null) return null
        }

        return current
    }
}

/**
 * Result of ABAC policy evaluation.
 */
data class AbacEvaluationResult(
    val decision: PolicyEffect?,
    val matchedPolicies: List<AbacPolicy>,
    val reason: String,
)
