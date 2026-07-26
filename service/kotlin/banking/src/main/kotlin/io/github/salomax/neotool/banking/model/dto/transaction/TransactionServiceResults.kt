package io.github.salomax.neotool.banking.model.dto.transaction

import io.github.salomax.neotool.banking.graphql.pendingaction.AccountPendingActionDTO

enum class BulkOperationOutcome {
    EXECUTED,
    CONFIRMATION_REQUIRED,
}

data class BulkCategorizeResult(
    val affectedCount: Int,
    val errors: List<String>,
    val outcome: BulkOperationOutcome = BulkOperationOutcome.EXECUTED,
    val pendingAction: AccountPendingActionDTO? = null,
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
