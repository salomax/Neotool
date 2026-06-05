package io.github.salomax.neotool.banking.service.transaction

import io.github.salomax.neotool.banking.domain.transaction.PatternType
import io.github.salomax.neotool.banking.model.domain.transaction.TransactionDescriptionNormalizer

internal object TransactionRuleMatcher {
    fun matches(
        sample: String,
        pattern: String,
        patternType: PatternType,
    ): Boolean {
        val normalizedSample = TransactionDescriptionNormalizer.normalize(sample)

        return when (patternType) {
            PatternType.CONTAINS -> containsWholeWord(normalizedSample, normalizePattern(pattern))
            PatternType.STARTS_WITH -> normalizedSample.startsWith(normalizePattern(pattern))
            PatternType.EXACT -> normalizedSample == normalizePattern(pattern)
            PatternType.REGEX ->
                try {
                    normalizedSample.matches(Regex(pattern, RegexOption.IGNORE_CASE))
                } catch (_: Exception) {
                    false
                }
        }
    }

    private fun normalizePattern(pattern: String): String = TransactionDescriptionNormalizer.normalize(pattern)

    /**
     * Checks if [text] contains [word] as a whole word — not as part of another word.
     * Both [text] and [word] must already be normalized and lowercased.
     */
    private fun containsWholeWord(
        text: String,
        word: String,
    ): Boolean = Regex("(?<![a-z])${Regex.escape(word)}(?![a-z])").containsMatchIn(text)
}
