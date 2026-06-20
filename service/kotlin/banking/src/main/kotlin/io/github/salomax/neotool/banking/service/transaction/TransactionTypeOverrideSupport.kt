package io.github.salomax.neotool.banking.service.transaction

import io.github.salomax.neotool.banking.domain.transaction.CategoryApplicableTo
import io.github.salomax.neotool.banking.domain.transaction.TransactionType

internal object TransactionTypeOverrideSupport {
    fun apply(
        baseType: TransactionType,
        typeOverride: CategoryApplicableTo?,
    ): TransactionType =
        when (typeOverride) {
            null,
            CategoryApplicableTo.ANY,
            -> baseType
            CategoryApplicableTo.TRANSFER -> TransactionType.TRANSFER
            CategoryApplicableTo.INCOME -> TransactionType.OTHER_INCOME
            CategoryApplicableTo.EXPENSE -> TransactionType.OTHER_EXPENSE
        }
}
