package io.github.salomax.neotool.assistant.domain.enums

/**
 * Usage budget scope target.
 * Matches assistant.usage_budgets.scope CHECK constraint.
 */
enum class BudgetScope {
    PROVIDER,
    ACCOUNT,
    SERVICE,
    FEATURE,
}
