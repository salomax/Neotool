package io.github.salomax.neotool.banking.graphql.transaction

import io.github.salomax.neotool.banking.graphql.pendingaction.AccountPendingActionDTO

data class DeleteTransactionResultDTO(
    val deleted: Boolean,
    val pendingAction: AccountPendingActionDTO? = null,
)
