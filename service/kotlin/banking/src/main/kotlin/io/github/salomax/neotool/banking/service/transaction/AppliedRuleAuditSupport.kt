package io.github.salomax.neotool.banking.service.transaction

import io.github.salomax.neotool.banking.domain.transaction.AppliedRuleAuditEntry
import io.github.salomax.neotool.banking.domain.transaction.RuleSuggestion
import io.github.salomax.neotool.banking.domain.transaction.TransactionRule
import java.time.Instant
import java.util.UUID

internal object AppliedRuleAuditSupport {
    fun resolveAppliedRuleIds(
        suggestion: RuleSuggestion?,
        rules: List<TransactionRule>,
        appliedCategory: Boolean,
        appliedMerchant: Boolean,
        appliedType: Boolean,
        appliedDescription: Boolean,
        appliedTags: Boolean,
    ): List<UUID> {
        if (suggestion == null || suggestion.appliedRuleIds.isEmpty()) return emptyList()
        if (!appliedCategory && !appliedMerchant && !appliedType && !appliedDescription && !appliedTags) return emptyList()

        val rulesById = rules.filter { it.id != null }.associateBy { requireNotNull(it.id) }
        if (rulesById.isEmpty()) {
            return suggestion.appliedRuleIds.distinct()
        }

        val appliedIds = linkedSetOf<UUID>()
        var categoryResolved = !appliedCategory
        var merchantResolved = !appliedMerchant
        var typeResolved = !appliedType
        var descriptionResolved = !appliedDescription
        var tagsResolved = !appliedTags

        for (ruleId in suggestion.appliedRuleIds) {
            val rule = rulesById[ruleId] ?: continue

            if (!categoryResolved && rule.categoryId != null) {
                appliedIds += ruleId
                categoryResolved = true
            }
            if (!merchantResolved && rule.merchantId != null) {
                appliedIds += ruleId
                merchantResolved = true
            }
            if (!typeResolved && rule.typeOverride != null) {
                appliedIds += ruleId
                typeResolved = true
            }
            if (!descriptionResolved && rule.descriptionOverride != null) {
                appliedIds += ruleId
                descriptionResolved = true
            }
            if (!tagsResolved && !rule.tagsOverride.isNullOrEmpty()) {
                appliedIds += ruleId
                tagsResolved = true
            }

            if (categoryResolved && merchantResolved && typeResolved && descriptionResolved && tagsResolved) {
                break
            }
        }

        return appliedIds.toList()
    }

    fun buildEntries(
        ruleIds: List<UUID>,
        appliedAt: Instant,
    ): List<AppliedRuleAuditEntry> =
        ruleIds.distinct().map {
            AppliedRuleAuditEntry(
                ruleId = it,
                appliedAt = appliedAt,
            )
        }
}
