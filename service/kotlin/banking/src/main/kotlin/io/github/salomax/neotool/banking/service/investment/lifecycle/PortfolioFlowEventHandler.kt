package io.github.salomax.neotool.banking.service.investment.lifecycle

import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.AMORTIZATION
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.DIVIDEND
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.INTEREST_ON_EQUITY
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.INTEREST_PAYMENT
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.MATURITY
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.PRIZE
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.PURCHASE
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.SALE
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.STOCK_LENDING
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.SUBSCRIPTION
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.WITHHOLDING_TAX
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioFlowEventType
import io.github.salomax.neotool.banking.service.investment.PortfolioFlowEventService
import io.micronaut.transaction.TransactionDefinition.Propagation
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton

@Singleton
open class PortfolioFlowEventHandler(
    private val portfolioFlowEventService: PortfolioFlowEventService,
) : InvestmentTransactionSideEffectHandler {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun onRecord(context: InvestmentTransactionContext) {
        val transaction = context.transaction ?: return
        syncPortfolioPerformance(context, transaction)
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun onUpdate(
        context: InvestmentTransactionContext,
        previous: InvestmentTransaction,
    ) {
        if (context.command == null) return
        val transaction = context.transaction ?: return
        removePortfolioFlowSideEffects(previous)
        syncPortfolioPerformance(context, transaction)
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun onDelete(context: InvestmentTransactionContext) {
        context.transaction?.let { removePortfolioFlowSideEffects(it) }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun onDeleteBatch(contexts: List<InvestmentTransactionContext>) {
        contexts.forEach { context ->
            if (appliesTo(context)) {
                context.transaction?.let(::removePortfolioFlowSideEffects)
            }
        }
    }

    private fun syncPortfolioPerformance(
        context: InvestmentTransactionContext,
        transaction: InvestmentTransaction,
    ) {
        val sourceReferenceId = transaction.id ?: return
        val flowEventType = transaction.toPortfolioFlowEventType()
        if (flowEventType != null) {
            portfolioFlowEventService.recordEvent(
                financialAccountId = transaction.financialAccountId,
                accountId = context.accountId,
                eventDate = transaction.tradeDate,
                eventType = flowEventType,
                grossAmount = transaction.netValue ?: transaction.grossValue,
                relatedHoldingId = context.holding.id,
                sourceReferenceId = sourceReferenceId,
                sourceReferenceType = PORTFOLIO_EVENT_SOURCE_INVESTMENT_TRANSACTION,
            )
            return
        }

        portfolioFlowEventService.requestRecalculation(
            financialAccountId = transaction.financialAccountId,
            accountId = context.accountId,
            recalculateFrom = transaction.tradeDate,
            triggeredBy = "investment_tx:$sourceReferenceId",
        )
    }

    private fun removePortfolioFlowSideEffects(transaction: InvestmentTransaction) {
        val sourceReferenceId = transaction.id ?: return
        if (transaction.toPortfolioFlowEventType() != null) {
            portfolioFlowEventService.deleteEventBySourceReference(
                sourceReferenceId = sourceReferenceId,
                sourceReferenceType = PORTFOLIO_EVENT_SOURCE_INVESTMENT_TRANSACTION,
            )
        }
    }

    companion object {
        private const val PORTFOLIO_EVENT_SOURCE_INVESTMENT_TRANSACTION = "INVESTMENT_TRANSACTION"
    }
}

private fun InvestmentTransaction.toPortfolioFlowEventType(): PortfolioFlowEventType? =
    when (type) {
        PURCHASE,
        SUBSCRIPTION,
        -> PortfolioFlowEventType.DEPOSIT
        SALE,
        MATURITY,
        -> PortfolioFlowEventType.WITHDRAWAL
        DIVIDEND,
        INTEREST_ON_EQUITY,
        INTEREST_PAYMENT,
        AMORTIZATION,
        PRIZE,
        STOCK_LENDING,
        -> PortfolioFlowEventType.DIVIDEND
        WITHHOLDING_TAX -> PortfolioFlowEventType.INCOME_TAX
        else -> null
    }
