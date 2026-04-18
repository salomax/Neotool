package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.*
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingAction
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionRepository
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionStatus
import io.github.salomax.neotool.banking.domain.pendingaction.PendingActionSeverity
import io.github.salomax.neotool.banking.domain.pendingaction.PendingActionType
import io.github.salomax.neotool.banking.domain.transaction.CreateTransactionCommand
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionSource
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.investment.InvestmentTransactionRepository
import io.github.salomax.neotool.banking.repo.attachment.HoldingAttachmentRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionCategoryEntityRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.github.salomax.neotool.banking.service.investment.recalculation.HoldingPositionEvolution
import io.github.salomax.neotool.banking.service.investment.recalculation.PositionEvolutionInput
import io.github.salomax.neotool.banking.service.transaction.TransactionService
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection.INFLOW
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection.OUTFLOW
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.AMORTIZATION
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.BONUS_SHARES
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.CANCELLATION
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.CUSTODY_TRANSFER
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.DIVIDEND
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.FINE
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.INTEREST_ON_EQUITY
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.INTEREST_PAYMENT
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.LATE_PAYMENT_INTEREST
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.MATURITY
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.OTHER
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.OWNERSHIP_TRANSFER
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.PRIZE
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.PURCHASE
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.QUOTA_TRANSFER
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.REVERSE_SPLIT
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.SALE
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.STOCK_LENDING
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.STOCK_SPLIT
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.SUBSCRIPTION
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.WITHHOLDING_TAX
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioFlowEventType
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Domain service for recording investment transactions and maintaining position state.
 *
 * ## Custo médio ponderado (Brazilian weighted average cost)
 *
 * On every PURCHASE/SUBSCRIPTION the holding's [Holding.averageCost] is recalculated:
 *   newCost = (existingQty × existingCost + purchasedQty × unitPrice) / newQty
 *
 * On SALE the average cost is unchanged — only the quantity decreases.
 * This follows the Brazilian Revenue Service (RFB) method for equities and most fixed income.
 *
 * Corporate events that change quantity without a purchase price (STOCK_SPLIT, BONUS_SHARES,
 * REVERSE_SPLIT) preserve the total cost basis and spread/concentrate it across new shares:
 *   newCost = existingQty × existingCost / newQty
 */
@Singleton
open class InvestmentTransactionService(
    private val investmentTransactionRepository: InvestmentTransactionRepository,
    private val holdingRepository: HoldingRepository,
    private val assetRepository: AssetRepository,
    private val financialAccountRepository: FinancialAccountRepository,
    private val pendingActionRepository: AccountPendingActionRepository,
    private val transactionService: TransactionService,
    private val transactionRepository: TransactionRepository,
    private val transactionCategoryEntityRepository: TransactionCategoryEntityRepository,
    private val portfolioFlowEventService: PortfolioFlowEventService,
    private val holdingAttachmentRepository: HoldingAttachmentRepository,
    private val couponScheduleService: CouponScheduleService,
) {
    private val logger = KotlinLogging.logger {}

    // ── Queries ───────────────────────────────────────────────────────────────

    fun listByHolding(
        holdingId: UUID,
        accountId: UUID,
    ): List<InvestmentTransaction> =
        investmentTransactionRepository.findByHolding(holdingId, accountId)

    fun listByFinancialAccount(
        financialAccountId: UUID,
        accountId: UUID,
    ): List<InvestmentTransaction> {
        requireAccountOwnsFinancialAccount(financialAccountId, accountId)
        return investmentTransactionRepository.findByFinancialAccount(financialAccountId, accountId)
    }

    fun findById(
        id: UUID,
        accountId: UUID,
    ): InvestmentTransaction =
        investmentTransactionRepository.findById(id, accountId)
            ?: throw NoSuchElementException("InvestmentTransaction $id not found")

    // ── Commands ──────────────────────────────────────────────────────────────

    /**
     * Records an investment transaction and updates the corresponding [Holding].
     *
     * Execution order:
     * 1. Validate inputs and account ownership.
     * 2. Dedup check for Open Finance transactions.
     * 3. Resolve the holding (upsert: find existing or create new on first PURCHASE).
     * 4. Apply the transaction effect on quantity and average cost.
     * 5. Save the holding (first, to obtain [Holding.id]).
     * 6. Save the transaction linked to the holding.
     *
     * Steps 4–7 run inside one `@Transactional` boundary (service + repositories).
     */
    @Transactional
    open fun record(
        command: RecordInvestmentTransactionCommand,
        accountId: UUID,
        userId: UUID,
    ): InvestmentTransaction {
        requireAccountOwnsFinancialAccount(command.financialAccountId, accountId)
        command.cashDestinationAccountId?.let { requireAccountOwnsFinancialAccount(it, accountId) }
        validateCommand(command)

        // Dedup: skip if already synced from the same provider
        if (command.providerTransactionId != null &&
            investmentTransactionRepository.existsByProviderTransactionId(
                command.financialAccountId,
                command.providerTransactionId,
            )
        ) {
            throw IllegalStateException(
                "Duplicate provider transaction: ${command.providerTransactionId} " +
                    "for account ${command.financialAccountId}",
            )
        }

        val asset =
            assetRepository.findById(command.assetId)
                ?: throw NoSuchElementException("Asset ${command.assetId} not found")

        // Find or create the holding (upsert — one holding per custodian account × asset)
        val existingHolding =
            command.providerInvestmentId?.let {
                holdingRepository.findByProviderInvestmentId(command.financialAccountId, it)
            } ?: holdingRepository.findByFinancialAccountAndAsset(
                command.financialAccountId,
                command.assetId,
            )

        val now = Instant.now()
        requireNotFixedIncomeDuplicatePurchase(existingHolding, asset, command)

        val holding = existingHolding ?: createHolding(command, asset, accountId, now)

        // Apply the transaction effect: update quantity and average cost
        val updatedHolding = applyEffect(holding, command, now, asset)

        // Persist the updated (or newly created) holding first to get its id
        val savedHolding =
            if (existingHolding == null) {
                holdingRepository.save(updatedHolding)
            } else {
                holdingRepository.update(updatedHolding)
            }

        val savedHoldingId = requireNotNull(savedHolding.id) { "Holding id must not be null after save" }

        val transaction =
            InvestmentTransaction(
                holdingId = savedHoldingId,
                financialAccountId = command.financialAccountId,
                accountId = accountId,
                createdByUserId = userId,
                direction = command.direction,
                type = command.type,
                typeAdditionalInfo = command.typeAdditionalInfo,
                quantity = command.quantity,
                unitPrice = command.unitPrice,
                grossValue = command.grossValue,
                netValue = command.netValue,
                incomeTax = command.incomeTax,
                iof = command.iof,
                indexerPercentage = command.indexerPercentage,
                fixedAnnualRate = command.fixedAnnualRate,
                priceFactor = command.priceFactor,
                tradeDate = command.tradeDate,
                settlementDate = command.settlementDate,
                source = command.source,
                providerTransactionId = command.providerTransactionId,
                importJobId = command.importJobId,
                brokerNote = command.brokerNote,
                createdAt = now,
                updatedAt = now,
            )

        var savedTx = investmentTransactionRepository.save(transaction)
        val linkedCashTx = syncInvestmentCashLedger(
            savedTx = savedTx,
            command = command,
            asset = asset,
            accountId = accountId,
            userId = userId,
        )
        syncMaturityPendingAction(savedHolding, accountId)
        syncCouponPendingAction(savedHolding, savedTx, accountId)
        syncPortfolioPerformance(savedHolding, savedTx, accountId)
        return savedTx
    }

    @Transactional
    open fun update(
        id: UUID,
        command: UpdateInvestmentTransactionCommand,
        accountId: UUID,
        userId: UUID,
    ): InvestmentTransaction {
        validateUpdateCommand(command)
        val existingTx =
            investmentTransactionRepository.findById(id, accountId)
                ?: throw NoSuchElementException("InvestmentTransaction $id not found")
        check(existingTx.source == InvestmentSource.MANUAL) {
            "Only MANUAL transactions can be updated; synced transactions must be managed via the provider"
        }
        val existingHolding =
            holdingRepository.findById(existingTx.holdingId, accountId)
                ?: throw NoSuchElementException("Holding ${existingTx.holdingId} not found")
        val now = Instant.now()
        val updatedTx =
            existingTx.copy(
                direction = command.direction,
                type = command.type,
                typeAdditionalInfo = command.typeAdditionalInfo,
                quantity = command.quantity,
                unitPrice = command.unitPrice,
                grossValue = command.grossValue,
                netValue = command.netValue,
                incomeTax = command.incomeTax,
                iof = command.iof,
                indexerPercentage = command.indexerPercentage,
                fixedAnnualRate = command.fixedAnnualRate,
                priceFactor = command.priceFactor,
                tradeDate = command.tradeDate,
                settlementDate = command.settlementDate,
                brokerNote = command.brokerNote,
                updatedAt = now,
            )
        var savedTx = investmentTransactionRepository.update(updatedTx)
        removePortfolioFlowSideEffects(existingTx)
        removeCustodianLedgerLegsLinkedToInvestmentTransactions(listOf(id), accountId)
        val rebuiltHolding =
            requireNotNull(recomputeHoldingState(existingHolding, accountId, command, now)) {
                "Holding ${existingHolding.id} cannot be empty after transaction update"
            }
        val asset =
            assetRepository.findById(existingHolding.assetId)
                ?: throw NoSuchElementException("Asset ${existingHolding.assetId} not found")
        val replayCommand = savedTx.toReplayCommand(rebuiltHolding)
        val linkedCashTx = syncInvestmentCashLedger(
            savedTx = savedTx,
            command = replayCommand,
            asset = asset,
            accountId = accountId,
            userId = userId,
        )
        syncCouponPendingAction(rebuiltHolding, savedTx, accountId)
        syncPortfolioPerformance(rebuiltHolding, savedTx, accountId)
        return savedTx
    }

    /**
     * Mirrors investment cash events into `banking.transactions` on the same custodian account,
     * linked via [io.github.salomax.neotool.banking.domain.transaction.Transaction.investmentTransactionId].
     *
     * **Purchases / subscriptions** post a **DEBIT** (cash leaves the custodian) so that
     * [PortfolioRecalculationService] sees `holdingsValue + cashBalance` ≈ NAV. Without this leg,
     * deposited cash stays in the ledger while the position is also marked-to-market → double count
     * and nonsensical TWR.
     *
     * The banking row [date][io.github.salomax.neotool.banking.domain.transaction.CreateTransactionCommand.date]
     * uses [RecordInvestmentTransactionCommand.settlementDate] when set (e.g. D+2 equities, D+1 RF),
     * otherwise [RecordInvestmentTransactionCommand.tradeDate], so cash movements align with
     * liquidation for all holding types.
     *
     * Skips when a row already exists (Open Finance dedup) or amount is non-positive.
     * Fails fast if the required system category is missing (seed data); logs a warn with context first.
     */
    private fun syncInvestmentCashLedger(
        savedTx: InvestmentTransaction,
        command: RecordInvestmentTransactionCommand,
        asset: Asset,
        accountId: UUID,
        userId: UUID,
    ): Transaction? {
        val linkId = savedTx.id ?: return null
        if (transactionRepository.existsByInvestmentTransactionId(linkId)) {
            return transactionRepository.findByInvestmentTransactionIds(listOf(linkId), accountId).firstOrNull()
        }

        val cashAmount = savedTx.netValue ?: savedTx.grossValue
        if (cashAmount.signum() <= 0) return null

        val cashLedgerDate = command.settlementDate ?: command.tradeDate

        when (command.type) {
            PURCHASE, SUBSCRIPTION -> {
                val categoryChild = investmentAppliedLedgerCategoryChild(asset.assetType)
                val categoryId =
                    transactionCategoryEntityRepository.findSystemSubcategoryId(
                        "Investimento aplicado",
                        categoryChild,
                    )
                        ?: run {
                            logger.warn {
                                "Investment purchase cash leg: system category not found " +
                                    "(parent=Investimento aplicado, child=$categoryChild); " +
                                    "investmentTransactionId=$linkId, accountId=$accountId"
                            }
                            throw IllegalStateException(
                                "System transaction category not found: '$categoryChild' under 'Investimento aplicado'. " +
                                    "Cannot create banking cash leg for investment transaction $linkId (type=${command.type}). " +
                                    "Run category seeds (e.g. V0_4__create_transaction_categories.sql).",
                            )
                        }
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
                    accountId = accountId,
                    userId = userId,
                )
            }
            in CASH_LEDGER_TYPES -> {
                val assetName = asset.name
                val (categoryParent, categoryChild) =
                    when (command.type) {
                        DIVIDEND, INTEREST_ON_EQUITY -> "Renda Extra" to "Dividendos"
                        SALE, MATURITY -> "Investimentos" to "Resgate"
                        INTEREST_PAYMENT, AMORTIZATION -> "Investimentos" to "Rendimento"
                        else ->
                            error(
                                "syncInvestmentCashLedger: type ${command.type} is in CASH_LEDGER_TYPES but has no category mapping",
                            )
                    }

                val categoryId =
                    transactionCategoryEntityRepository.findSystemSubcategoryId(categoryParent, categoryChild)
                        ?: run {
                            logger.warn {
                                "Investment cash leg: system category not found (parent=$categoryParent, child=$categoryChild); " +
                                    "investmentTransactionId=$linkId, investmentType=${command.type}, accountId=$accountId"
                            }
                            throw IllegalStateException(
                                "System transaction category not found: '$categoryChild' under '$categoryParent'. " +
                                    "Cannot create banking cash leg for investment transaction $linkId (type=${command.type}). " +
                                    "Run category seeds (e.g. V0_4__create_transaction_categories.sql).",
                            )
                        }

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
                    accountId = accountId,
                    userId = userId,
                )
            }
            else -> return null
        }
    }

    /** Child under system category "Investimento aplicado" (TRANSFER / custodian DEBIT). */
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

    /**
     * Keeps the INVESTMENT_MATURED pending action in sync after every holding mutation.
     *
     * - quantity > 0 with maturityDate  → upsert: create or update visibleFrom
     * - quantity = 0 with maturityDate  → cancel: early redemption/sale closed the position
     * - no maturityDate                 → cancel if one exists (maturityDate was removed)
     */
    private fun syncMaturityPendingAction(
        holding: Holding,
        accountId: UUID,
    ) {
        val holdingId = holding.id ?: return
        val maturityDate = holding.maturityDate

        if (maturityDate == null || holding.quantity <= java.math.BigDecimal.ZERO) {
            pendingActionRepository.cancelByEntityAndType(
                entityType = "HOLDING",
                entityId = holdingId,
                type = PendingActionType.INVESTMENT_MATURED,
            )
            return
        }

        val visibleFrom = maturityDate.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
        val now = Instant.now()

        val existing = pendingActionRepository.findPendingByEntityAndType(
            entityType = "HOLDING",
            entityId = holdingId,
            type = PendingActionType.INVESTMENT_MATURED,
        )

        if (existing != null) {
            if (existing.visibleFrom != visibleFrom) {
                pendingActionRepository.update(existing.copy(visibleFrom = visibleFrom, updatedAt = now))
            }
        } else {
            pendingActionRepository.save(
                AccountPendingAction(
                    accountId = accountId,
                    type = PendingActionType.INVESTMENT_MATURED,
                    severity = PendingActionSeverity.ACTION_REQUIRED,
                    status = AccountPendingActionStatus.PENDING,
                    entityType = "HOLDING",
                    entityId = holdingId,
                    relatedTransactionId = null,
                    payload = mapOf(
                        "assetName" to holding.assetId.toString(),
                        "maturityDate" to maturityDate.toString(),
                    ),
                    visibleFrom = visibleFrom,
                    notifiedAt = null,
                    dismissedAt = null,
                    completedAt = null,
                    cancelledAt = null,
                    expiresAt = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    private fun syncCouponPendingAction(
        holding: Holding,
        savedTx: InvestmentTransaction?,
        accountId: UUID,
    ) {
        val holdingId = holding.id ?: return
        if (!holding.supportsCouponPendingAction()) {
            pendingActionRepository.cancelByEntityAndType(
                entityType = "HOLDING",
                entityId = holdingId,
                type = PendingActionType.COUPON_PAYMENT_DUE,
            )
            return
        }

        if (savedTx?.type == INTEREST_PAYMENT) {
            completeCurrentCouponPendingAction(holding, savedTx.id)
        }

        upsertNextCouponPendingActionIfNeeded(
            holding = holding,
            today = savedTx?.tradeDate ?: LocalDate.now(),
        )
    }

    fun upsertNextCouponPendingActionIfNeeded(
        holding: Holding,
        today: LocalDate = LocalDate.now(),
    ) {
        val holdingId = holding.id ?: return
        if (!holding.supportsCouponPendingAction()) {
            pendingActionRepository.cancelByEntityAndType(
                entityType = "HOLDING",
                entityId = holdingId,
                type = PendingActionType.COUPON_PAYMENT_DUE,
            )
            return
        }

        val transactions = investmentTransactionRepository.findByHolding(holdingId, holding.accountId)
        val lastInterestPaymentDate =
            transactions
                .asSequence()
                .filter { tx -> tx.type == INTEREST_PAYMENT }
                .map { it.tradeDate }
                .maxOrNull()
        val asset =
            assetRepository.findById(holding.assetId)
                ?: return
        val schedule =
            couponScheduleService
                .generateSchedule(holding, asset.assetType, emptyList(), today)
                .map { it.scheduledDate }
        val nextCouponDate =
            if (lastInterestPaymentDate != null) {
                schedule.firstOrNull { date -> date.isAfter(lastInterestPaymentDate) }
            } else {
                schedule.firstOrNull()
            }

        if (nextCouponDate == null) {
            pendingActionRepository.cancelByEntityAndType(
                entityType = "HOLDING",
                entityId = holdingId,
                type = PendingActionType.COUPON_PAYMENT_DUE,
            )
            return
        }

        val visibleFrom = nextCouponDate.minusDays(7).atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
        val now = Instant.now()
        val payload =
            mapOf(
                "couponDate" to nextCouponDate.toString(),
                "couponFrequency" to holding.couponFrequency?.name,
            )

        val existing = pendingActionRepository.findPendingByEntityAndType("HOLDING", holdingId, PendingActionType.COUPON_PAYMENT_DUE)
        if (existing != null) {
            if (existing.visibleFrom != visibleFrom || existing.payload != payload) {
                pendingActionRepository.update(
                    existing.copy(
                        visibleFrom = visibleFrom,
                        payload = payload,
                        updatedAt = now,
                    ),
                )
            }
            return
        }

        pendingActionRepository.save(
            AccountPendingAction(
                accountId = holding.accountId,
                type = PendingActionType.COUPON_PAYMENT_DUE,
                severity = PendingActionSeverity.ACTION_REQUIRED,
                status = AccountPendingActionStatus.PENDING,
                entityType = "HOLDING",
                entityId = holdingId,
                relatedTransactionId = null,
                payload = payload,
                visibleFrom = visibleFrom,
                notifiedAt = null,
                dismissedAt = null,
                completedAt = null,
                cancelledAt = null,
                expiresAt = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    fun completeCurrentCouponPendingAction(holding: Holding) {
        completeCurrentCouponPendingAction(holding, null)
    }

    private fun completeCurrentCouponPendingAction(
        holding: Holding,
        relatedTransactionId: UUID?,
    ) {
        val holdingId = holding.id ?: return
        val existing =
            pendingActionRepository.findPendingByEntityAndType(
                entityType = "HOLDING",
                entityId = holdingId,
                type = PendingActionType.COUPON_PAYMENT_DUE,
            ) ?: return
        val now = Instant.now()
        pendingActionRepository.update(
            existing.copy(
                status = AccountPendingActionStatus.COMPLETED,
                relatedTransactionId = relatedTransactionId ?: existing.relatedTransactionId,
                completedAt = now,
                updatedAt = now,
            ),
        )
    }

    fun delete(
        id: UUID,
        accountId: UUID,
    ): Boolean {
        val tx =
            investmentTransactionRepository.findById(id, accountId)
                ?: throw NoSuchElementException("InvestmentTransaction $id not found")
        check(tx.source == InvestmentSource.MANUAL) {
            "Only MANUAL transactions can be deleted; synced transactions must be managed via the provider"
        }
        val holding =
            holdingRepository.findById(tx.holdingId, accountId)
                ?: throw NoSuchElementException("Holding ${tx.holdingId} not found")

        val deleted = investmentTransactionRepository.softDelete(id, accountId)
        if (!deleted) return false

        removePortfolioFlowSideEffects(tx)
        removeCustodianLedgerLegsLinkedToInvestmentTransactions(listOf(id), accountId)
        recomputeHoldingAfterDelete(holding, accountId)
        portfolioFlowEventService.requestRecalculation(
            financialAccountId = tx.financialAccountId,
            accountId = accountId,
            recalculateFrom = tx.tradeDate,
            triggeredBy = "investment_tx_delete:$id",
        )
        return true
    }

    /**
     * Soft-deletes the holding and all its manual transactions, removes portfolio flow events (including any
     * `related_holding_id` orphans), **soft-deletes custodian ledger rows** linked via `investment_transaction_id`
     * (reversing cash on the custodian account), deletes holding attachments, and requests quota recalculation.
     */
    fun deleteHolding(
        id: UUID,
        accountId: UUID,
    ): Boolean {
        val holding =
            holdingRepository.findById(id, accountId)
                ?: throw NoSuchElementException("Holding $id not found")
        check(holding.source == InvestmentSource.MANUAL) {
            "Only MANUAL holdings can be deleted; synced holdings must be managed via the provider"
        }

        val transactions = investmentTransactionRepository.findByHolding(id, accountId)
        check(transactions.all { it.source == InvestmentSource.MANUAL }) {
            "Only holdings with MANUAL transactions can be deleted as a whole"
        }

        transactions.forEach { removePortfolioFlowSideEffects(it) }

        val investmentTxIds = transactions.map { requireNotNull(it.id) { "Investment transaction id must not be null" } }
        removeCustodianLedgerLegsLinkedToInvestmentTransactions(investmentTxIds, accountId)

        transactions.forEach { transaction ->
            val transactionId = requireNotNull(transaction.id) { "Investment transaction id must not be null" }
            val deleted = investmentTransactionRepository.softDelete(transactionId, accountId)
            check(deleted) { "Failed to delete investment transaction $transactionId for holding $id" }
        }

        portfolioFlowEventService.deleteEventsByRelatedHolding(
            financialAccountId = holding.financialAccountId,
            relatedHoldingId = id,
        )

        holdingAttachmentRepository.deleteByHoldingIdAndAccountId(id, accountId)

        pendingActionRepository.cancelByEntityAndType(
            entityType = "HOLDING",
            entityId = id,
            type = PendingActionType.INVESTMENT_MATURED,
        )
        pendingActionRepository.cancelByEntityAndType(
            entityType = "HOLDING",
            entityId = id,
            type = PendingActionType.COUPON_PAYMENT_DUE,
        )

        val recalcFrom = transactions.minOfOrNull { it.tradeDate }
            ?: holding.issueDate
            ?: LocalDate.now()
        portfolioFlowEventService.requestRecalculation(
            financialAccountId = holding.financialAccountId,
            accountId = accountId,
            recalculateFrom = recalcFrom,
            triggeredBy = "holding_delete:$id",
        )

        return holdingRepository.softDelete(id, accountId)
    }

    // ── Custo médio ponderado ─────────────────────────────────────────────────

    /**
     * Applies the transaction effect on the holding's [quantity] and [averageCost].
     *
     * Rules by transaction type:
     * - **PURCHASE / SUBSCRIPTION**: quantity increases; averageCost recalculated (weighted avg).
     * - **SALE**: quantity decreases; averageCost unchanged (RFB custo médio rule).
     * - **MATURITY**: quantity reduced by [qty] (or zeroed when null = full maturity).
     * - **STOCK_SPLIT / BONUS_SHARES**: quantity increases; total cost basis preserved → averageCost diluted.
     * - **REVERSE_SPLIT**: quantity decreases; total cost basis preserved → averageCost concentrated.
     * - **CUSTODY_TRANSFER / OWNERSHIP_TRANSFER / QUOTA_TRANSFER**: INFLOW treated as PURCHASE;
     *   OUTFLOW treated as SALE.
     * - **Income / tax / penalty events**: no quantity or cost change.
     */
    private fun validateApplyEffectPreconditions(
        command: RecordInvestmentTransactionCommand,
        holding: Holding,
        asset: Asset,
    ) {
        val qty = command.quantity
        val price = command.unitPrice
        when (command.type) {
            PURCHASE, SUBSCRIPTION -> {
                requireNotNull(qty) { "quantity is required for ${command.type}" }
                requireNotNull(price) { "unitPrice is required for ${command.type}" }
            }

            SALE -> {
                requireNotNull(qty) { "quantity is required for SALE" }
                require(holding.quantity >= qty) {
                    "Insufficient quantity for sale: have ${holding.quantity}, selling $qty"
                }
            }

            MATURITY -> {
                val closingQty = qty ?: holding.quantity
                require(holding.quantity >= closingQty) {
                    "Maturity quantity ($closingQty) exceeds current holding quantity (${holding.quantity})"
                }
            }

            STOCK_SPLIT, BONUS_SHARES -> {
                requireNotNull(qty) { "quantity (new shares received) is required for ${command.type}" }
                require(qty > BigDecimal.ZERO) { "quantity must be positive for ${command.type}" }
            }

            REVERSE_SPLIT -> {
                requireNotNull(qty) { "quantity (shares removed) is required for REVERSE_SPLIT" }
                require(qty > BigDecimal.ZERO) { "quantity must be positive for REVERSE_SPLIT" }
                require(holding.quantity >= qty) {
                    "Reverse split quantity ($qty) exceeds current holding quantity (${holding.quantity})"
                }
            }

            CUSTODY_TRANSFER, OWNERSHIP_TRANSFER, QUOTA_TRANSFER -> {
                when {
                    command.direction == INFLOW && qty != null && price != null -> Unit
                    command.direction == OUTFLOW && qty != null -> {
                        require(holding.quantity >= qty) {
                            "Transfer quantity ($qty) exceeds current holding quantity (${holding.quantity})"
                        }
                    }
                    else -> Unit
                }
            }

            else -> Unit
        }
        CouponDayOfMonthRules.validate(asset.assetType, holding)
    }

    private fun applyEffect(
        holding: Holding,
        command: RecordInvestmentTransactionCommand,
        now: Instant,
        asset: Asset,
    ): Holding {
        val effectiveHolding = holding.withMissingFixedIncomeTerms(command)
        validateApplyEffectPreconditions(command, effectiveHolding, asset)
        return HoldingPositionEvolution.evolve(effectiveHolding, PositionEvolutionInput.from(command))
            .copy(updatedAt = now)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun createHolding(
        command: RecordInvestmentTransactionCommand,
        asset: Asset,
        accountId: UUID,
        now: Instant,
    ): Holding {
        validateManualFundValuation(command, asset)
        return Holding(
            financialAccountId = command.financialAccountId,
            accountId = accountId,
            assetId = command.assetId,
            quantity = BigDecimal.ZERO,
            averageCost = BigDecimal.ZERO,
            indexer = command.indexer,
            indexerPercentage = command.indexerPercentage,
            fixedAnnualRate = command.fixedAnnualRate,
            issueDate = command.issueDate ?: command.tradeDate,
            maturityDate = command.maturityDate,
            gracePeriodDate = command.gracePeriodDate,
            isTaxExempt = command.isTaxExempt,
            couponFrequency = command.couponFrequency,
            couponDayOfMonth = command.couponDayOfMonth,
            pricingMode = command.pricingMode,
            manualGrossValue = command.manualValuation?.grossValue,
            manualValuedAt = command.manualValuation?.valuedAt,
            source = command.source,
            providerInvestmentId = command.providerInvestmentId,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun requireAccountOwnsFinancialAccount(
        financialAccountId: UUID,
        accountId: UUID,
    ) {
        val accounts = financialAccountRepository.findByAccountId(accountId)
        require(accounts.any { it.id == financialAccountId }) {
            "Financial account $financialAccountId does not belong to account $accountId"
        }
    }

    private fun validateCommand(command: RecordInvestmentTransactionCommand) {
        require(command.grossValue >= BigDecimal.ZERO) { "grossValue must be >= 0" }
        if (command.type == InvestmentTransactionType.OTHER) {
            requireNotNull(command.typeAdditionalInfo) {
                "typeAdditionalInfo is required when type is OTHER"
            }
        }
    }

    private fun validateUpdateCommand(command: UpdateInvestmentTransactionCommand) {
        require(command.grossValue >= BigDecimal.ZERO) { "grossValue must be >= 0" }
        if (command.type == InvestmentTransactionType.OTHER) {
            requireNotNull(command.typeAdditionalInfo) {
                "typeAdditionalInfo is required when type is OTHER"
            }
        }
    }

    /**
     * Prevents adding a new PURCHASE to an existing fixed income holding.
     *
     * Each fixed income contract (CDB, LCA, LCI, etc.) is a unique instrument tied
     * to specific terms (rate, maturity, issuer). A second application — even from
     * the same issuer with the same rate — is a new contract and must be recorded as
     * a new asset + holding. Merging two contracts into one holding would corrupt the
     * IR regressive-table calculation (which depends on each contract's individual
     * holding period) and lose the per-contract terms.
     *
     * Allowed on existing RF holdings: SALE, MATURITY, INTEREST_PAYMENT, AMORTIZATION,
     * CANCELLATION, and all other non-purchase event types.
     */
    private fun requireNotFixedIncomeDuplicatePurchase(
        existingHolding: Holding?,
        asset: Asset,
        command: RecordInvestmentTransactionCommand,
    ) {
        if (existingHolding == null) return
        if (command.type != PURCHASE) return
        if (asset.assetType !in FIXED_INCOME_ASSET_TYPES) return
        throw IllegalArgumentException(
            "Cannot add a new purchase to an existing ${asset.assetType} holding. " +
                "Each fixed income contract must be recorded as a separate asset and holding, " +
                "because each contract has its own terms (rate, maturity, IR holding period). " +
                "Create a new asset for this contract instead.",
        )
    }

    private fun recomputeHoldingAfterDelete(
        existingHolding: Holding,
        accountId: UUID,
    ) {
        val now = Instant.now()
        val holdingId = requireNotNull(existingHolding.id) { "Holding id must not be null for delete recomputation" }
        val rebuiltHolding = recomputeHoldingState(existingHolding, accountId, null, now)
        if (rebuiltHolding == null) {
            pendingActionRepository.cancelByEntityAndType("HOLDING", holdingId, PendingActionType.INVESTMENT_MATURED, now)
            pendingActionRepository.cancelByEntityAndType("HOLDING", holdingId, PendingActionType.COUPON_PAYMENT_DUE, now)
            holdingRepository.softDelete(holdingId, accountId)
        }
    }

    private fun recomputeHoldingState(
        existingHolding: Holding,
        accountId: UUID,
        updateCommand: UpdateInvestmentTransactionCommand?,
        now: Instant,
    ): Holding? {
        val holdingId = requireNotNull(existingHolding.id) { "Holding id must not be null for recomputation" }
        val asset =
            assetRepository.findById(existingHolding.assetId)
                ?: throw NoSuchElementException("Asset ${existingHolding.assetId} not found for holding recomputation")
        val remainingTransactions =
            investmentTransactionRepository.findByHolding(holdingId, accountId)
                .sortedWith(compareBy<InvestmentTransaction>({ it.tradeDate }, { it.createdAt }))
        if (remainingTransactions.isEmpty()) return null
        val replayBase =
            existingHolding.copy(
                quantity = BigDecimal.ZERO,
                averageCost = BigDecimal.ZERO,
                indexer = updateCommand?.indexer ?: existingHolding.indexer,
                issueDate = updateCommand?.issueDate ?: existingHolding.issueDate,
                maturityDate = updateCommand?.maturityDate ?: existingHolding.maturityDate,
                gracePeriodDate = updateCommand?.gracePeriodDate ?: existingHolding.gracePeriodDate,
                isTaxExempt = updateCommand?.isTaxExempt ?: existingHolding.isTaxExempt,
                couponFrequency = updateCommand?.couponFrequency ?: existingHolding.couponFrequency,
                couponDayOfMonth = updateCommand?.couponDayOfMonth ?: existingHolding.couponDayOfMonth,
                pricingMode = updateCommand?.pricingMode ?: existingHolding.pricingMode,
                manualGrossValue = updateCommand?.manualValuation?.grossValue ?: existingHolding.manualGrossValue,
                manualValuedAt = updateCommand?.manualValuation?.valuedAt ?: existingHolding.manualValuedAt,
                updatedAt = now,
            )
        val rebuiltHolding =
            remainingTransactions.fold(replayBase) { holding, transaction ->
                applyEffect(
                    holding = holding,
                    command = transaction.toReplayCommand(replayBase),
                    now = now,
                    asset = asset,
                )
            }
        val saved = holdingRepository.update(rebuiltHolding.copy(updatedAt = now))
        syncMaturityPendingAction(saved, accountId)
        syncCouponPendingAction(saved, null, accountId)
        return saved
    }

    /**
     * Keeps `investment_portfolio_flow_event` aligned with cash and income so [PortfolioQuotaCalculationService]
     * can subtract external flows from daily returns. **PURCHASE/SUBSCRIPTION** use [PortfolioFlowEventType.DEPOSIT]
     * so a second application does not look like pure performance.
     */
    private fun syncPortfolioPerformance(
        savedHolding: Holding,
        savedTx: InvestmentTransaction,
        accountId: UUID,
    ) {
        val sourceReferenceId = savedTx.id ?: return
        val flowEventType = savedTx.toPortfolioFlowEventType()
        if (flowEventType != null) {
            portfolioFlowEventService.recordEvent(
                financialAccountId = savedTx.financialAccountId,
                accountId = accountId,
                eventDate = savedTx.tradeDate,
                eventType = flowEventType,
                grossAmount = savedTx.netValue ?: savedTx.grossValue,
                relatedHoldingId = savedHolding.id,
                sourceReferenceId = sourceReferenceId,
                sourceReferenceType = PORTFOLIO_EVENT_SOURCE_INVESTMENT_TRANSACTION,
            )
            return
        }

        portfolioFlowEventService.requestRecalculation(
            financialAccountId = savedTx.financialAccountId,
            accountId = accountId,
            recalculateFrom = savedTx.tradeDate,
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

    /**
     * Soft-deletes custodian ledger rows created for these investment transactions and removes any
     * portfolio flow rows keyed by those banking transaction ids (TRANSFER legs). Reverses custodian cash
     * as if the trades never posted to the ledger.
     */
    private fun removeCustodianLedgerLegsLinkedToInvestmentTransactions(
        investmentTransactionIds: Collection<UUID>,
        accountId: UUID,
    ) {
        if (investmentTransactionIds.isEmpty()) return
        val ledgerRows = transactionRepository.findByInvestmentTransactionIds(investmentTransactionIds, accountId)
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
            resolveLedgerPeerTransaction(ledgerTx, accountId)?.let { peer ->
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
    ): Transaction? {
        val directPeerId = tx.transferPeerTransactionId
        if (directPeerId != null) {
            return transactionRepository.findById(directPeerId, accountId)
        }
        val selfId = tx.id ?: return null
        return transactionRepository.findByTransferPeerTransactionId(selfId, accountId)
    }

    private fun InvestmentTransaction.toReplayCommand(existingHolding: Holding) =
        RecordInvestmentTransactionCommand(
            financialAccountId = financialAccountId,
            assetId = existingHolding.assetId,
            direction = direction,
            type = type,
            typeAdditionalInfo = typeAdditionalInfo,
            quantity = quantity,
            unitPrice = unitPrice,
            grossValue = grossValue,
            netValue = netValue,
            incomeTax = incomeTax,
            iof = iof,
            indexerPercentage = indexerPercentage,
            fixedAnnualRate = fixedAnnualRate,
            priceFactor = priceFactor,
            tradeDate = tradeDate,
            settlementDate = settlementDate,
            source = source,
            providerTransactionId = providerTransactionId,
            providerInvestmentId = existingHolding.providerInvestmentId,
            couponFrequency = existingHolding.couponFrequency,
            couponDayOfMonth = existingHolding.couponDayOfMonth,
            brokerNote = brokerNote,
            pricingMode = existingHolding.pricingMode,
            manualValuation =
                existingHolding.manualGrossValue?.let { gross ->
                    existingHolding.manualValuedAt?.let { valuedAt ->
                        ManualFundValuation(
                            grossValue = gross,
                            valuedAt = valuedAt,
                        )
                    }
                },
        )

    private fun validateManualFundValuation(
        command: RecordInvestmentTransactionCommand,
        asset: Asset,
    ) {
        val isFund = asset.assetType.isFundAsset()
        if (!isFund) {
            require(command.pricingMode == null) { "pricingMode is only supported for fund holdings" }
            require(command.manualValuation == null) { "manualValuation is only supported for fund holdings" }
            return
        }

        if (command.manualValuation != null) {
            require(command.type == PURCHASE) { "manualValuation is only supported on the first PURCHASE transaction" }
            require(command.pricingMode == HoldingPricingMode.MANUAL) {
                "manualValuation requires pricingMode = MANUAL"
            }
        }
    }

    companion object {
        /**
         * Asset types for which each contract is a unique, non-fungible instrument.
         * A second PURCHASE on an existing holding of these types is always a programming
         * or user error — each application must be its own asset + holding so that
         * per-contract IR holding-period calculations remain accurate.
         */
        val FIXED_INCOME_ASSET_TYPES: Set<AssetType> = setOf(
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

        private const val PORTFOLIO_EVENT_SOURCE_INVESTMENT_TRANSACTION = "INVESTMENT_TRANSACTION"

        private const val PORTFOLIO_EVENT_SOURCE_BANKING_TRANSACTION = "BANKING_TRANSACTION"
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
        -> PortfolioFlowEventType.DIVIDEND
        WITHHOLDING_TAX -> PortfolioFlowEventType.INCOME_TAX
        else -> null
    }

private fun InvestmentSource.toLedgerTransactionSource(): TransactionSource =
    when (this) {
        InvestmentSource.MANUAL -> TransactionSource.MANUAL
        InvestmentSource.OPEN_FINANCE -> TransactionSource.OPEN_FINANCE
        InvestmentSource.FILE_IMPORT -> TransactionSource.CSV
    }

private fun Holding.withMissingFixedIncomeTerms(command: RecordInvestmentTransactionCommand): Holding {
    val hasIncomingFixedIncomeTerms =
        indexer != null ||
            command.indexer != null ||
            command.indexerPercentage != null ||
            command.fixedAnnualRate != null

    return copy(
        indexer = indexer ?: command.indexer,
        indexerPercentage = indexerPercentage ?: command.indexerPercentage,
        fixedAnnualRate = fixedAnnualRate ?: command.fixedAnnualRate,
        issueDate = issueDate ?: command.issueDate ?: if (hasIncomingFixedIncomeTerms) command.tradeDate else null,
        maturityDate = maturityDate ?: command.maturityDate,
        gracePeriodDate = gracePeriodDate ?: command.gracePeriodDate,
        isTaxExempt = isTaxExempt || command.isTaxExempt,
        couponFrequency = couponFrequency ?: command.couponFrequency,
        couponDayOfMonth = couponDayOfMonth ?: command.couponDayOfMonth,
    )
}

private fun Holding.supportsCouponPendingAction(): Boolean =
    quantity > BigDecimal.ZERO &&
        maturityDate != null &&
        issueDate != null &&
        couponFrequency != null &&
        couponFrequency != CouponFrequency.AT_MATURITY

private fun AssetType.isFundAsset(): Boolean =
    this == AssetType.MUTUAL_FUND || this == AssetType.PGBL || this == AssetType.VGBL
