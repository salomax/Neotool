package io.github.salomax.neotool.banking.domain.transaction

import jakarta.inject.Singleton
import java.text.Normalizer
import java.util.UUID

@Singleton
class DefaultCategorizationService(
    private val ruleRepository: CategoryRuleRepository,
) : CategorizationService {
    override fun suggest(
        accountId: UUID,
        description: String?,
        creditDebitType: CreditDebitType,
    ): CategorySuggestion? {
        if (description.isNullOrBlank()) return null
        val normalized = normalize(description)
        val rules = ruleRepository.findRulesForAccount(accountId)
        val side =
            if (creditDebitType == CreditDebitType.CREDIT) {
                CategoryApplicableTo.INCOME
            } else {
                CategoryApplicableTo.EXPENSE
            }

        for (rule in rules) {
            if (!isApplicable(rule.applicableTo, side)) continue
            if (matches(normalized, rule.pattern, rule.patternType)) {
                ruleRepository.incrementMatchCount(rule.id!!)
                return CategorySuggestion(rule.categoryId, rule.id!!)
            }
        }
        return null
    }

    override fun learnFromManualCategorization(
        accountId: UUID,
        description: String?,
        categoryId: UUID,
    ) {
        if (description.isNullOrBlank()) return
        val pattern = normalize(description)
        val existing = ruleRepository.findImplicitByPattern(accountId, pattern)
        val now = java.time.Instant.now()
        if (existing == null) {
            ruleRepository.save(
                CategoryRule(
                    accountId = accountId,
                    categoryId = categoryId,
                    pattern = pattern,
                    patternType = PatternType.CONTAINS,
                    applicableTo = CategoryApplicableTo.ANY,
                    priority = 50,
                    isSystem = false,
                    isImplicit = true,
                    matchCount = 0,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } else if (existing.categoryId != categoryId) {
            ruleRepository.save(existing.copy(categoryId = categoryId, updatedAt = now))
        }
    }

    private fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("[^\\p{ASCII}]"), "")
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .lowercase()
            .trim()
            .replace(Regex("\\s+"), " ")

    private fun matches(
        normalized: String,
        pattern: String,
        type: PatternType,
    ): Boolean =
        when (type) {
            PatternType.CONTAINS -> normalized.contains(pattern)
            PatternType.STARTS_WITH -> normalized.startsWith(pattern)
            PatternType.EXACT -> normalized == pattern
            PatternType.REGEX ->
                try {
                    normalized.matches(Regex(pattern, RegexOption.IGNORE_CASE))
                } catch (_: Exception) {
                    false
                }
        }

    private fun isApplicable(
        ruleApplicableTo: CategoryApplicableTo,
        txSide: CategoryApplicableTo,
    ): Boolean = ruleApplicableTo == CategoryApplicableTo.ANY || ruleApplicableTo == txSide
}
