package io.github.salomax.neotool.banking.domain.transaction

import java.text.Normalizer

object TransactionDescriptionNormalizer {
    fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("[^\\p{ASCII}]"), "")
            // Keep '*' as it can be present in provider-generated descriptors (e.g. "NETFLIX.COM*BR").
            // Strip other punctuation to keep CONTAINS matching robust.
            .replace(Regex("[^a-zA-Z0-9\\s*]"), " ")
            .lowercase()
            .trim()
            .replace(Regex("\\s+"), " ")
}
