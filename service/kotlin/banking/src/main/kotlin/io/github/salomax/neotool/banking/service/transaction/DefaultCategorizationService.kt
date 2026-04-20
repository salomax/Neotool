package io.github.salomax.neotool.banking.service.transaction

import io.github.salomax.neotool.banking.domain.transaction.*
import io.github.salomax.neotool.banking.model.domain.transaction.TransactionDescriptionNormalizer
import io.github.salomax.neotool.banking.repo.transaction.TransactionCategoryRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRuleRepository
import jakarta.inject.Singleton
import java.util.UUID


@Singleton
class DefaultTransactionRuleEngine(
    private val ruleRepository: TransactionRuleRepository,
    private val categoryRepository: TransactionCategoryRepository,
) : TransactionRuleEngine {
    /**
     * Matching algorithm:
     * 1) Rules are pre-ordered by priority ASC (lower = higher precedence)
     * 2) Tie-breaker: matchCount DESC
     * 3) Tie-breaker: createdAt DESC
     * 4) First matching rule that contributes each field wins that field
     * 5) Multiple rules may contribute different fields in the same suggestion
     */
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

        var ruleId: UUID? = null
        var categoryId: UUID? = null
        var merchantId: UUID? = null
        var descriptionOverride: String? = null
        var tagsOverride: List<String>? = null
        val matchedRuleIds = linkedSetOf<UUID>()

        for (rule in rules) {
            if (!isApplicable(rule.applicableTo, side)) continue
            if (!matches(normalized, rule.pattern, rule.patternType)) continue

            val resolvedCategoryId =
                if (rule.categoryId != null) {
                    val categoryExists = categoryRepository.findById(rule.categoryId) != null
                    if (categoryExists) rule.categoryId else null
                } else {
                    null
                }

            val contributed =
                (categoryId == null && resolvedCategoryId != null) ||
                    (merchantId == null && rule.merchantId != null) ||
                    (descriptionOverride == null && rule.descriptionOverride != null) ||
                    (tagsOverride == null && rule.tagsOverride != null)
            if (!contributed) continue

            if (ruleId == null) {
                ruleId = rule.id
            }
            if (resolvedCategoryId != null && categoryId == null) {
                categoryId = resolvedCategoryId
            }
            if (rule.merchantId != null && merchantId == null) {
                merchantId = rule.merchantId
            }
            if (rule.descriptionOverride != null && descriptionOverride == null) {
                descriptionOverride = rule.descriptionOverride
            }
            if (rule.tagsOverride != null && tagsOverride == null) {
                tagsOverride = rule.tagsOverride
            }
            rule.id?.let(matchedRuleIds::add)

            if (categoryId != null && merchantId != null && descriptionOverride != null && tagsOverride != null) {
                break
            }
        }

        if (matchedRuleIds.isEmpty() || ruleId == null) return null
        if (incrementMatchCount) {
            matchedRuleIds.forEach(ruleRepository::incrementMatchCount)
        }
        return RuleSuggestion(
            ruleId = ruleId,
            categoryId = categoryId,
            merchantId = merchantId,
            descriptionOverride = descriptionOverride,
            tagsOverride = tagsOverride,
            appliedRuleIds = matchedRuleIds.toList(),
        )
    }

    override fun learnFromManualAssignment(
        accountId: UUID,
        description: String?,
        categoryId: UUID?,
        merchantId: UUID?,
        descriptionOverride: String?,
        tagsOverride: List<String>?,
        tagsOverrideProvided: Boolean,
    ): UUID? {
        if (description.isNullOrBlank()) return null
        if (categoryId == null && merchantId == null && descriptionOverride == null && !tagsOverrideProvided) return null
        val pattern = TransactionDescriptionNormalizer.normalize(description)
        val existing = ruleRepository.findImplicitByPattern(accountId, pattern)
        val now = java.time.Instant.now()
        return if (existing == null) {
            ruleRepository.save(
                TransactionRule(
                    accountId = accountId,
                    categoryId = categoryId,
                    merchantId = merchantId,
                    descriptionOverride = descriptionOverride,
                    tagsOverride = if (tagsOverrideProvided) tagsOverride.orEmpty() else null,
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
            ).id
        } else {
            val newCategoryId = if (categoryId != null) categoryId else existing.categoryId
            val newMerchantId = if (merchantId != null) merchantId else existing.merchantId
            val newDescriptionOverride = descriptionOverride ?: existing.descriptionOverride
            val newTagsOverride = if (tagsOverrideProvided) tagsOverride.orEmpty() else existing.tagsOverride
            val changed =
                newCategoryId != existing.categoryId ||
                    newMerchantId != existing.merchantId ||
                    newDescriptionOverride != existing.descriptionOverride ||
                    newTagsOverride != existing.tagsOverride
            if (changed) {
                ruleRepository.save(
                    existing.copy(
                        categoryId = newCategoryId,
                        merchantId = newMerchantId,
                        descriptionOverride = newDescriptionOverride,
                        tagsOverride = newTagsOverride,
                        updatedAt = now,
                    ),
                )
            }
            existing.id
        }
    }

    internal fun normalize(s: String): String =
        TransactionDescriptionNormalizer.normalize(s)

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
