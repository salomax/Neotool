package io.github.salomax.neotool.banking.service.investment.lifecycle

import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import jakarta.inject.Singleton

@Singleton
class InvestmentTransactionLifecycleOrchestrator(
    private val handlers: List<InvestmentTransactionSideEffectHandler>,
) {
    fun onRecord(ctx: InvestmentTransactionContext) {
        handlers.forEach { handler ->
            if (handler.appliesTo(ctx)) {
                handler.onRecord(ctx)
            }
        }
    }

    fun onUpdate(
        ctx: InvestmentTransactionContext,
        previous: InvestmentTransaction,
    ) {
        handlers.forEach { handler ->
            if (handler.appliesTo(ctx)) {
                handler.onUpdate(ctx, previous)
            }
        }
    }

    fun onDelete(ctx: InvestmentTransactionContext) {
        handlers.forEach { handler ->
            if (handler.appliesTo(ctx)) {
                handler.onDelete(ctx)
            }
        }
    }

    fun onDeleteBatch(contexts: List<InvestmentTransactionContext>) {
        if (contexts.isEmpty()) return
        handlers.forEach { handler ->
            handler.onDeleteBatch(contexts)
        }
    }
}
