package io.github.salomax.neotool.banking.domain.transaction

import jakarta.inject.Singleton
import java.text.Normalizer
import java.util.UUID


@Singleton
class DefaultTransactionRuleEngine(
    private val ruleRepository: TransactionRuleRepository,
    private val categoryRepository: TransactionCategoryRepository,
) : TransactionRuleEngine {
    override fun preloadRules(accountId: UUID): List<TransactionRule> = ruleRepository.findRulesForAccount(accountId)

    override fun suggest(
        accountId: UUID,
        description: String?,
        creditDebitType: CreditDebitType,
    ): RuleSuggestion? {
        if (description.isNullOrBlank()) return null
        val rules = ruleRepository.findRulesForAccount(accountId)
        return suggestInternal(
            rules = rules,
            description = description,
            creditDebitType = creditDebitType,
            incrementMatchCount = true,
        )
    }

    override fun suggestFromRules(
        rules: List<TransactionRule>,
        description: String?,
        creditDebitType: CreditDebitType,
    ): RuleSuggestion? {
        if (description.isNullOrBlank()) return null
        return suggestInternal(
            rules = rules,
            description = description,
            creditDebitType = creditDebitType,
            incrementMatchCount = false,
        )
    }

    override fun suggestFromRulesRecordingMatch(
        rules: List<TransactionRule>,
        description: String?,
        creditDebitType: CreditDebitType,
    ): RuleSuggestion? {
        if (description.isNullOrBlank()) return null
        return suggestInternal(
            rules = rules,
            description = description,
            creditDebitType = creditDebitType,
            incrementMatchCount = true,
        )
    }

    private fun suggestInternal(
        rules: List<TransactionRule>,
        description: String,
        creditDebitType: CreditDebitType,
        incrementMatchCount: Boolean,
    ): RuleSuggestion? {
        val normalized = normalize(description)
        val side =
            if (creditDebitType == CreditDebitType.CREDIT) {
                CategoryApplicableTo.INCOME
            } else {
                CategoryApplicableTo.EXPENSE
            }

        for (rule in rules) {
            if (!isApplicable(rule.applicableTo, side)) continue
            if (matches(normalized, rule.pattern, rule.patternType)) {
                // If the rule targets a category that was soft-deleted, skip it silently (CAT-12 edge case).
                if (rule.categoryId != null) {
                    val categoryExists = categoryRepository.findById(rule.categoryId) != null
                    if (!categoryExists) continue
                }

                if (incrementMatchCount) {
                    ruleRepository.incrementMatchCount(rule.id!!)
                }
                return RuleSuggestion(
                    ruleId = rule.id!!,
                    categoryId = rule.categoryId,
                    merchantId = rule.merchantId,
                )
            }
        }
        return null
    }

    override fun learnFromManualAssignment(
        accountId: UUID,
        description: String?,
        categoryId: UUID?,
        merchantId: UUID?,
    ) {
        if (description.isNullOrBlank()) return
        if (categoryId == null && merchantId == null) return
        val pattern = normalize(description)
        val existing = ruleRepository.findImplicitByPattern(accountId, pattern)
        val now = java.time.Instant.now()
        if (existing == null) {
            ruleRepository.save(
                TransactionRule(
                    accountId = accountId,
                    categoryId = categoryId,
                    merchantId = merchantId,
                    pattern = pattern,
                    patternType = PatternType.CONTAINS,
                    applicableTo = CategoryApplicableTo.ANY,
                    priority = 60,
                    isSystem = false,
                    isImplicit = true,
                    matchCount = 0,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } else {
            val newCategoryId = if (categoryId != null) categoryId else existing.categoryId
            val newMerchantId = if (merchantId != null) merchantId else existing.merchantId
            val changed = newCategoryId != existing.categoryId || newMerchantId != existing.merchantId
            if (changed) {
                ruleRepository.save(
                    existing.copy(
                        categoryId = newCategoryId,
                        merchantId = newMerchantId,
                        updatedAt = now,
                    ),
                )
            }
        }
    }

    internal fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("[^\\p{ASCII}]"), "")
            // Keep '*' as it can be present in provider-generated descriptors (e.g. "NETFLIX.COM*BR").
            // Strip other punctuation to keep CONTAINS matching robust.
            .replace(Regex("[^a-zA-Z0-9\\s*]"), " ")
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
