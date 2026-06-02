package io.github.salomax.neotool.banking.service.investment.lifecycle

import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction

interface InvestmentTransactionSideEffectHandler {
    fun appliesTo(context: InvestmentTransactionContext): Boolean = true

    fun onRecord(context: InvestmentTransactionContext)

    fun onUpdate(
        context: InvestmentTransactionContext,
        previous: InvestmentTransaction,
    )

    fun onDelete(context: InvestmentTransactionContext)

    fun onDeleteBatch(contexts: List<InvestmentTransactionContext>) {
        contexts.forEach { context ->
            if (appliesTo(context)) {
                onDelete(context)
            }
        }
    }
}
