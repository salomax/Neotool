package io.github.salomax.neotool.banking.service.investment.lifecycle

import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.service.investment.TaxRecalculationService
import io.micronaut.transaction.TransactionDefinition.Propagation
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton

@Singleton
open class TaxRecalculationHandler(
    private val taxRecalculationService: TaxRecalculationService,
) : InvestmentTransactionSideEffectHandler {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun onRecord(context: InvestmentTransactionContext) = Unit

    @Transactional(propagation = Propagation.MANDATORY)
    override fun onUpdate(
        context: InvestmentTransactionContext,
        previous: InvestmentTransaction,
    ) {
        val transaction = context.transaction ?: return
        enqueue(context, previous)
        enqueue(context, transaction)
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun onDelete(context: InvestmentTransactionContext) {
        val transaction = context.transaction ?: return
        enqueue(context, transaction)
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun onDeleteBatch(contexts: List<InvestmentTransactionContext>) = Unit

    private fun enqueue(
        context: InvestmentTransactionContext,
        transaction: InvestmentTransaction,
    ) {
        val holdingId = context.holding.id ?: return
        val transactionId = transaction.id ?: return
        taxRecalculationService.requestRecalculationForTransaction(
            holdingId = holdingId,
            accountId = context.accountId,
            tradeDate = transaction.tradeDate,
            triggeredBy = "investment_tx:$transactionId",
        )
    }
}
