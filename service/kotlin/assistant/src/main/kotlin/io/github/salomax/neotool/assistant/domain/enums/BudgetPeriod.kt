package io.github.salomax.neotool.assistant.domain.enums

/**
 * Usage budget accounting interval.
 * Matches assistant.usage_budgets.period CHECK constraint.
 */
enum class BudgetPeriod {
    DAILY,
    WEEKLY,
    MONTHLY,
}
