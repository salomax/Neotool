package io.github.salomax.neotool.banking.service.investment.lifecycle

import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.AMORTIZATION
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.DIVIDEND
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.INTEREST_ON_EQUITY
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.INTEREST_PAYMENT
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.MATURITY
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.PURCHASE
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.SALE
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.SUBSCRIPTION
import io.github.salomax.neotool.banking.domain.investment.RecordInvestmentTransactionCommand
import io.github.salomax.neotool.banking.domain.transaction.CreateTransactionCommand
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionSource
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.repo.transaction.TransactionCategoryEntityRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.github.salomax.neotool.banking.service.investment.PortfolioFlowEventService
import io.github.salomax.neotool.banking.service.transaction.TransactionService
import io.micronaut.transaction.TransactionDefinition.Propagation
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Singleton
open class CustodianCashLedgerHandler(
    private val transactionService: TransactionService,
    private val transactionRepository: TransactionRepository,
    private val transactionCategoryEntityRepository: TransactionCategoryEntityRepository,
    private val portfolioFlowEventService: PortfolioFlowEventService,
) : InvestmentTransactionSideEffectHandler {
    private val logger = KotlinLogging.logger {}
    private val categoryIdCache = ConcurrentHashMap<Pair<String, String>, UUID>()

    @Transactional(propagation = Propagation.MANDATORY)
    override fun onRecord(context: InvestmentTransactionContext) {
        val command = context.command ?: return
        val transaction = context.transaction ?: return
        syncInvestmentCashLedger(
            savedTx = transaction,
            command = command,
            context = context,
        )
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun onUpdate(
        context: InvestmentTransactionContext,
        previous: InvestmentTransaction,
    ) {
        if (context.command == null) return
        previous.id?.let {
            removeCustodianLedgerLegsLinkedToInvestmentTransactions(
                listOf(it),
                context.accountId,
            )
        }
        onRecord(context)
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun onDelete(context: InvestmentTransactionContext) {
        context.transaction?.id?.let {
            removeCustodianLedgerLegsLinkedToInvestmentTransactions(
                listOf(it),
                context.accountId,
            )
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun onDeleteBatch(contexts: List<InvestmentTransactionContext>) {
        if (contexts.isEmpty()) return
        contexts
            .asSequence()
            .filter(::appliesTo)
            .groupBy(InvestmentTransactionContext::accountId)
            .forEach { (accountId, groupedContexts) ->
                val transactionIds =
                    groupedContexts
                        .mapNotNull { it.transaction?.id }
                        .distinct()
                removeCustodianLedgerLegsLinkedToInvestmentTransactions(transactionIds, accountId)
            }
    }

    private fun syncInvestmentCashLedger(
        savedTx: InvestmentTransaction,
        command: RecordInvestmentTransactionCommand,
        context: InvestmentTransactionContext,
    ): Transaction? {
        val linkId = savedTx.id ?: return null
        val existingLinkedLedgerTx =
            transactionRepository.findByInvestmentTransactionIds(listOf(linkId), context.accountId).firstOrNull()
        if (existingLinkedLedgerTx != null) {
            return existingLinkedLedgerTx
        }

        val cashAmount = savedTx.netValue ?: savedTx.grossValue
        if (cashAmount.signum() <= 0) return null

        val cashLedgerDate = command.settlementDate ?: command.tradeDate

        when (command.type) {
            PURCHASE, SUBSCRIPTION -> {
                val categoryChild = investmentAppliedLedgerCategoryChild(context.asset.assetType)
                val categoryId =
                    resolveSystemSubcategoryId(
                        parentName = "Investimento aplicado",
                        childName = categoryChild,
                        linkId = linkId,
                        commandType = command.type,
                        accountId = context.accountId,
                    )
                val description =
                    when (command.type) {
                        SUBSCRIPTION -> "Subscrição"
                        else -> "Compra / aplicação"
                    }
                return transactionService.create(
                    CreateTransactionCommand(
                        financialAccountId = command.financialAccountId,
                        creditDebitType = CreditDebitType.DEBIT,
                        type = TransactionType.TRANSFER,
                        amount = cashAmount,
                        date = cashLedgerDate,
                        description = description,
                        categoryId = categoryId,
                        transactionSource = command.source.toLedgerTransactionSource(),
                        investmentTransactionId = linkId,
                    ),
                    accountId = context.accountId,
                    userId = context.userId,
                )
            }

            in CASH_LEDGER_TYPES -> {
                val assetName = context.asset.name
                val (categoryParent, categoryChild) =
                    when (command.type) {
                        DIVIDEND, INTEREST_ON_EQUITY -> "Renda Extra" to "Dividendos"
                        SALE, MATURITY -> "Investimentos" to "Resgate"
                        INTEREST_PAYMENT, AMORTIZATION -> "Investimentos" to "Rendimento"
                        else ->
                            error(
                                "syncInvestmentCashLedger: type ${command.type} is in CASH_LEDGER_TYPES " +
                                    "but has no category mapping",
                            )
                    }

                val categoryId =
                    resolveSystemSubcategoryId(
                        parentName = categoryParent,
                        childName = categoryChild,
                        linkId = linkId,
                        commandType = command.type,
                        accountId = context.accountId,
                    )

                val destinationAccountId = command.cashDestinationAccountId ?: command.financialAccountId

                val description =
                    when (command.type) {
                        DIVIDEND -> "Dividendos — $assetName"
                        INTEREST_ON_EQUITY -> "JCP — $assetName"
                        SALE -> "Resgate / venda — $assetName"
                        MATURITY -> "Vencimento — $assetName"
                        INTEREST_PAYMENT -> "Rendimento (juros) — $assetName"
                        AMORTIZATION -> "Amortização — $assetName"
                        else -> "Investimentos — $assetName"
                    }

                return transactionService.create(
                    CreateTransactionCommand(
                        financialAccountId = destinationAccountId,
                        creditDebitType = CreditDebitType.CREDIT,
                        type = TransactionType.INVESTMENT_RETURN,
                        amount = cashAmount,
                        date = cashLedgerDate,
                        description = description,
                        categoryId = categoryId,
                        transactionSource = command.source.toLedgerTransactionSource(),
                        investmentTransactionId = linkId,
                    ),
                    accountId = context.accountId,
                    userId = context.userId,
                )
            }

            else -> return null
        }
    }

    private fun investmentAppliedLedgerCategoryChild(assetType: AssetType): String =
        when (assetType) {
            in FIXED_INCOME_ASSET_TYPES,
            AssetType.ETF_FIXED_INCOME,
            -> "Renda Fixa"
            AssetType.MUTUAL_FUND -> "Fundos"
            AssetType.PGBL, AssetType.VGBL -> "Previdência"
            AssetType.STOCK,
            AssetType.STOCK_RIGHTS,
            AssetType.BDR,
            AssetType.REAL_ESTATE_FUND,
            AssetType.AGRIBUSINESS_FUND,
            AssetType.ETF,
            AssetType.ETF_INTERNATIONAL,
            AssetType.COE,
            AssetType.OPTION,
            AssetType.DERIVATIVES,
            -> "Ações"
            AssetType.CRYPTO -> "Criptomoeda"
            AssetType.OTHER -> "Renda Fixa"
            else -> "Renda Fixa"
        }

    private fun removeCustodianLedgerLegsLinkedToInvestmentTransactions(
        investmentTransactionIds: Collection<UUID>,
        accountId: UUID,
    ) {
        if (investmentTransactionIds.isEmpty()) return
        val ledgerRows = transactionRepository.findByInvestmentTransactionIds(investmentTransactionIds, accountId)
        if (ledgerRows.isEmpty()) return
        val localById = ledgerRows.mapNotNull { tx -> tx.id?.let { id -> id to tx } }.toMap()
        val localByTransferPeer =
            ledgerRows
                .mapNotNull { tx -> tx.transferPeerTransactionId?.let { id -> id to tx } }
                .toMap()
        val removedBankingIds = mutableSetOf<UUID>()
        for (ledgerTx in ledgerRows) {
            val bankingId = requireNotNull(ledgerTx.id) { "banking transaction id must not be null" }
            if (bankingId in removedBankingIds) continue
            if (ledgerTx.type == TransactionType.TRANSFER) {
                portfolioFlowEventService.deleteEventBySourceReference(
                    bankingId,
                    PORTFOLIO_EVENT_SOURCE_BANKING_TRANSACTION,
                )
            }
            transactionService.delete(bankingId, accountId)
            removedBankingIds += bankingId
            resolveLedgerPeerTransaction(ledgerTx, accountId, localById, localByTransferPeer)?.let { peer ->
                val peerId = requireNotNull(peer.id) { "peer transaction id must not be null" }
                if (peerId !in removedBankingIds) {
                    if (peer.type == TransactionType.TRANSFER) {
                        portfolioFlowEventService.deleteEventBySourceReference(
                            peerId,
                            PORTFOLIO_EVENT_SOURCE_BANKING_TRANSACTION,
                        )
                    }
                    transactionService.delete(peerId, accountId)
                    removedBankingIds += peerId
                }
            }
        }
    }

    private fun resolveLedgerPeerTransaction(
        tx: Transaction,
        accountId: UUID,
        localById: Map<UUID, Transaction>,
        localByTransferPeer: Map<UUID, Transaction>,
    ): Transaction? {
        val directPeerId = tx.transferPeerTransactionId
        if (directPeerId != null) {
            return localById[directPeerId] ?: transactionRepository.findById(directPeerId, accountId)
        }
        val selfId = tx.id ?: return null
        return localByTransferPeer[selfId] ?: transactionRepository.findByTransferPeerTransactionId(selfId, accountId)
    }

    private fun resolveSystemSubcategoryId(
        parentName: String,
        childName: String,
        linkId: UUID,
        commandType: InvestmentTransactionType,
        accountId: UUID,
    ): UUID {
        val key = parentName to childName
        return categoryIdCache.computeIfAbsent(key) {
            transactionCategoryEntityRepository.findSystemSubcategoryId(parentName, childName)
                ?: run {
                    logger.warn {
                        "Investment cash leg: system category not found " +
                            "(parent=$parentName, child=$childName); " +
                            "investmentTransactionId=$linkId, investmentType=$commandType, " +
                            "accountId=$accountId"
                    }
                    throw IllegalStateException(
                        "System transaction category not found: '$childName' under '$parentName'. " +
                            "Cannot create banking cash leg for investment transaction $linkId " +
                            "(type=$commandType). Run category seeds " +
                            "(e.g. V0_4__create_transaction_categories.sql).",
                    )
                }
        }
    }

    companion object {
        private val FIXED_INCOME_ASSET_TYPES: Set<AssetType> =
            setOf(
                AssetType.CDB,
                AssetType.RDB,
                AssetType.LCI,
                AssetType.LCA,
                AssetType.LC,
                AssetType.LF,
                AssetType.LIG,
                AssetType.DEBENTURES,
                AssetType.CRI,
                AssetType.CRA,
                AssetType.TREASURY,
            )

        private val CASH_LEDGER_TYPES: Set<InvestmentTransactionType> =
            setOf(
                DIVIDEND,
                INTEREST_ON_EQUITY,
                SALE,
                MATURITY,
                INTEREST_PAYMENT,
                AMORTIZATION,
            )

        private const val PORTFOLIO_EVENT_SOURCE_BANKING_TRANSACTION = "BANKING_TRANSACTION"
    }
}

private fun InvestmentSource.toLedgerTransactionSource(): TransactionSource =
    when (this) {
        InvestmentSource.MANUAL -> TransactionSource.MANUAL
        InvestmentSource.OPEN_FINANCE -> TransactionSource.OPEN_FINANCE
        InvestmentSource.FILE_IMPORT -> TransactionSource.CSV
    }
