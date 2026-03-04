package io.github.salomax.neotool.banking.model.transaction

import java.io.Serializable
import java.util.UUID

data class TransactionTagId(
    var transactionId: UUID = UUID.randomUUID(),
    var tag: String = "",
) : Serializable
