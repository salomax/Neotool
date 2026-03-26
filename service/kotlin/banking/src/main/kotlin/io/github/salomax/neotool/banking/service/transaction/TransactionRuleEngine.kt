package io.github.salomax.neotool.banking.service.transaction

import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.RuleSuggestion
import io.github.salomax.neotool.banking.domain.transaction.TransactionRule
import java.util.UUID

/**
 * Suggests category and/or merchant for a transaction based on rules, and learns from manual assignments.
 */
interface TransactionRuleEngine {
    /**
     * Loads all active rules for the account, including system rules, in the same order
     * expected by the rule engine.
     *
     * Used by preview flows that must avoid N+1 rule loading.
     */
    fun preloadRules(accountId: UUID): List<TransactionRule>

    /**
     * Returns the first matching rule's suggestion (categoryId and/or merchantId) for the given
     * description and side (INCOME/EXPENSE). Returns null if no rule matches or description is blank.
     */
    fun suggest(
        accountId: UUID,
        description: String?,
        creditDebitType: CreditDebitType,
    ): RuleSuggestion?

    /**
     * Suggests using preloaded [rules]. Side-effect free (does not increment match_count).
     */
    fun suggestFromRules(
        rules: List<TransactionRule>,
        description: String?,
        creditDebitType: CreditDebitType,
    ): RuleSuggestion?

    /**
     * Like [suggestFromRules], but increments the matched rule's `match_count`.
     *
     * Intended for batch flows that preload rules once via [preloadRules] (e.g. file imports).
     */
    fun suggestFromRulesRecordingMatch(
        rules: List<TransactionRule>,
        description: String?,
        creditDebitType: CreditDebitType,
    ): RuleSuggestion?

    /**
     * Creates or updates an implicit rule from a user's manual category/merchant assignment.
     * No-op if description is blank or both categoryId and merchantId are null.
     */
    fun learnFromManualAssignment(
        accountId: UUID,
        description: String?,
        categoryId: UUID?,
        merchantId: UUID?,
        descriptionOverride: String? = null,
        tagsOverride: List<String>? = null,
        tagsOverrideProvided: Boolean = false,
    )
}
