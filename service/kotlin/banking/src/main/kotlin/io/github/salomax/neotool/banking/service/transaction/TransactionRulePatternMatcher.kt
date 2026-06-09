package io.github.salomax.neotool.banking.service.transaction

import io.github.salomax.neotool.banking.domain.transaction.PatternType
import io.github.salomax.neotool.banking.model.domain.transaction.TransactionDescriptionNormalizer

/**
 * Shared pattern-matching rules for transaction rules.
 * Used by [DefaultTransactionRuleEngine] (import/create) and [TransactionRuleService] (preview/historical).
 */
internal object TransactionRulePatternMatcher {
    fun matchesDescription(
        description: String?,
        pattern: String,
        patternType: PatternType,
    ): Boolean {
        if (description.isNullOrBlank()) return false
        return matches(
            normalizedDescription = TransactionDescriptionNormalizer.normalize(description),
            pattern = pattern.trim(),
            patternType = patternType,
        )
    }

    fun matches(
        normalizedDescription: String,
        pattern: String,
        patternType: PatternType,
    ): Boolean {
        val normalizedPattern = TransactionDescriptionNormalizer.normalize(pattern)
        return when (patternType) {
            PatternType.CONTAINS ->
                containsWholeWord(normalizedDescription, normalizedPattern)
            PatternType.STARTS_WITH ->
                normalizedDescription.startsWith(normalizedPattern)
            PatternType.EXACT ->
                normalizedDescription == normalizedPattern
            PatternType.REGEX ->
                try {
                    normalizedDescription.matches(Regex(pattern, RegexOption.IGNORE_CASE))
                } catch (_: Exception) {
                    false
                }
        }
    }

    /**
     * Checks if [text] contains [word] as a whole word — not as part of another word.
     * Both [text] and [word] must already be normalized and lowercased.
     */
    private fun containsWholeWord(
        text: String,
        word: String,
    ): Boolean = Regex("(?<![a-z])${Regex.escape(word)}(?![a-z])").containsMatchIn(text)
}
