package io.github.salomax.neotool.banking.domain.transaction

import java.util.UUID

/**
 * Suggests a category for a transaction based on rules, and learns from manual categorization.
 */
interface CategorizationService {
    /**
     * Returns the first matching rule's category for the given description and side (INCOME/EXPENSE).
     * Returns null if no rule matches or description is blank.
     */
    fun suggest(
        accountId: UUID,
        description: String?,
        creditDebitType: CreditDebitType,
    ): CategorySuggestion?

    /**
     * Creates or updates an implicit rule from a user's manual category assignment.
     * No-op if description is blank.
     */
    fun learnFromManualCategorization(
        accountId: UUID,
        description: String?,
        categoryId: UUID,
    )
}

data class CategorySuggestion(val categoryId: UUID, val ruleId: UUID)
