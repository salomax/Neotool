package io.github.salomax.neotool.banking.domain.transaction

import java.util.UUID

data class RuleSuggestion(
    val ruleId: UUID,
    val categoryId: UUID?,
    val merchantId: UUID?,
    val typeOverride: CategoryApplicableTo? = null,
    val descriptionOverride: String? = null,
    val tagsOverride: List<String>? = null,
    /** All rules that contributed to this suggestion. */
    val appliedRuleIds: List<UUID> = emptyList(),
)
