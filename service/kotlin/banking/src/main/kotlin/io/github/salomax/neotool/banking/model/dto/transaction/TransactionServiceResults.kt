package io.github.salomax.neotool.banking.model.dto.transaction

data class BulkCategorizeResult(
    val affectedCount: Int,
    val errors: List<String>,
)

data class SimilarTransactionEditPreview(
    val similarCount: Int,
    val matchDescription: String,
    val supportsFuturePropagation: Boolean,
)

data class SimilarTransactionEditResult(
    val affectedCount: Int,
    val futureRuleCreated: Boolean,
)
