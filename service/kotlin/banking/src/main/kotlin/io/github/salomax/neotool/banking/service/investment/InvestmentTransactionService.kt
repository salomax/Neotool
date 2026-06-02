package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.CouponDayOfMonthRules
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.HoldingPricingMode
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection.INFLOW
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection.OUTFLOW
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.BONUS_SHARES
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.CUSTODY_TRANSFER
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.MATURITY
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.OWNERSHIP_TRANSFER
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.PURCHASE
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.QUOTA_TRANSFER
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.REVERSE_SPLIT
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.SALE
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.STOCK_LENDING
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.STOCK_SPLIT
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.SUBSCRIPTION
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.WITHHOLDING_TAX
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionRepository
import io.github.salomax.neotool.banking.domain.pendingaction.PendingActionType
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.banking.model.dto.investment.ManualFundValuation
import io.github.salomax.neotool.banking.model.dto.investment.RecordInvestmentTransactionCommand
import io.github.salomax.neotool.banking.model.dto.investment.UpdateInvestmentTransactionCommand
import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.repo.attachment.HoldingAttachmentRepository
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.investment.InvestmentTransactionRepository
import io.github.salomax.neotool.banking.service.investment.lifecycle.InvestmentTransactionContext
import io.github.salomax.neotool.banking.service.investment.lifecycle.InvestmentTransactionLifecycleOrchestrator
import io.github.salomax.neotool.banking.service.investment.recalculation.HoldingPositionEvolution
import io.github.salomax.neotool.banking.service.investment.recalculation.PositionEvolutionInput
import io.github.salomax.neotool.common.error.ConflictException
import io.github.salomax.neotool.common.error.NotFoundException
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Same eligibility as [RecordStockLendingService] for manual `recordInvestmentTransaction` paths. */
private val STOCK_LENDING_ELIGIBLE_ASSET_TYPES: Set<AssetType> =
    setOf(
        AssetType.STOCK,
        AssetType.ETF,
        AssetType.BDR,
        AssetType.REAL_ESTATE_FUND,
        AssetType.AGRIBUSINESS_FUND,
    )

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
    private val portfolioFlowEventService: PortfolioFlowEventService,
    private val holdingAttachmentRepository: HoldingAttachmentRepository,
    private val lifecycleOrchestrator: InvestmentTransactionLifecycleOrchestrator,
) {
    // ── Queries ───────────────────────────────────────────────────────────────

    fun listByHolding(
        holdingId: UUID,
        accountId: UUID,
    ): List<InvestmentTransaction> {
        return investmentTransactionRepository.findByHolding(holdingId, accountId)
    }

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
        val effectiveCommand = command.withInferredPurchaseUnitPrice()

        // Dedup: skip if already synced from the same provider
        if (effectiveCommand.providerTransactionId != null &&
            investmentTransactionRepository.existsByProviderTransactionId(
                effectiveCommand.financialAccountId,
                effectiveCommand.providerTransactionId,
            )
        ) {
            throw IllegalStateException(
                "Duplicate provider transaction: ${effectiveCommand.providerTransactionId} " +
                    "for account ${effectiveCommand.financialAccountId}",
            )
        }

        val asset =
            assetRepository.findById(effectiveCommand.assetId)
                ?: throw NoSuchElementException("Asset ${effectiveCommand.assetId} not found")

        // Find or create the holding (upsert — one holding per custodian account × asset)
        val existingHolding =
            effectiveCommand.providerInvestmentId?.let {
                holdingRepository.findByProviderInvestmentId(effectiveCommand.financialAccountId, it)
            } ?: holdingRepository.findByFinancialAccountAndAsset(
                effectiveCommand.financialAccountId,
                effectiveCommand.assetId,
            )

        val now = Instant.now()

        val holding = existingHolding ?: createHolding(effectiveCommand, asset, accountId, now)

        // Apply the transaction effect: update quantity and average cost
        val updatedHolding = applyEffect(holding, effectiveCommand, now, asset)

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
                financialAccountId = effectiveCommand.financialAccountId,
                accountId = accountId,
                createdByUserId = userId,
                direction = effectiveCommand.direction,
                type = effectiveCommand.type,
                typeAdditionalInfo = effectiveCommand.typeAdditionalInfo,
                quantity = effectiveCommand.quantity,
                unitPrice = effectiveCommand.unitPrice,
                grossValue = effectiveCommand.grossValue,
                netValue = effectiveCommand.netValue,
                incomeTax = effectiveCommand.incomeTax,
                iof = effectiveCommand.iof,
                indexerPercentage = effectiveCommand.indexerPercentage,
                fixedAnnualRate = effectiveCommand.fixedAnnualRate,
                priceFactor = effectiveCommand.priceFactor,
                tradeDate = effectiveCommand.tradeDate,
                settlementDate = effectiveCommand.settlementDate,
                source = effectiveCommand.source,
                providerTransactionId = effectiveCommand.providerTransactionId,
                importJobId = effectiveCommand.importJobId,
                brokerNote = effectiveCommand.brokerNote,
                createdAt = now,
                updatedAt = now,
            )

        val savedTx = investmentTransactionRepository.save(transaction)
        lifecycleOrchestrator.onRecord(
            InvestmentTransactionContext(
                transaction = savedTx,
                holding = savedHolding,
                asset = asset,
                command = effectiveCommand,
                accountId = accountId,
                userId = userId,
            ),
        )
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
        val effectiveCommand = command.withInferredPurchaseUnitPrice()
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
                direction = effectiveCommand.direction,
                type = effectiveCommand.type,
                typeAdditionalInfo = effectiveCommand.typeAdditionalInfo,
                quantity = effectiveCommand.quantity,
                unitPrice = effectiveCommand.unitPrice,
                grossValue = effectiveCommand.grossValue,
                netValue = effectiveCommand.netValue,
                incomeTax = effectiveCommand.incomeTax,
                iof = effectiveCommand.iof,
                indexerPercentage = effectiveCommand.indexerPercentage,
                fixedAnnualRate = effectiveCommand.fixedAnnualRate,
                priceFactor = effectiveCommand.priceFactor,
                tradeDate = effectiveCommand.tradeDate,
                settlementDate = effectiveCommand.settlementDate,
                brokerNote = effectiveCommand.brokerNote,
                updatedAt = now,
            )
        val savedTx = investmentTransactionRepository.update(updatedTx)
        val rebuiltHolding =
            requireNotNull(recomputeHoldingState(existingHolding, accountId, effectiveCommand, now)) {
                "Holding ${existingHolding.id} cannot be empty after transaction update"
            }
        val holdingAfterManualValuationUpdate =
            if (effectiveCommand.pricingMode != null || effectiveCommand.manualValuation != null) {
                holdingRepository.update(
                    rebuiltHolding.copy(
                        pricingMode = effectiveCommand.pricingMode ?: rebuiltHolding.pricingMode,
                        manualGrossValue =
                            effectiveCommand.manualValuation?.grossValue ?: rebuiltHolding.manualGrossValue,
                        manualValuedAt = effectiveCommand.manualValuation?.valuedAt ?: rebuiltHolding.manualValuedAt,
                        updatedAt = now,
                    ),
                )
            } else {
                rebuiltHolding
            }
        val asset =
            assetRepository.findById(existingHolding.assetId)
                ?: throw NoSuchElementException("Asset ${existingHolding.assetId} not found")
        val replayCommand = savedTx.toReplayCommand(holdingAfterManualValuationUpdate)
        lifecycleOrchestrator.onUpdate(
            InvestmentTransactionContext(
                transaction = savedTx,
                holding = holdingAfterManualValuationUpdate,
                asset = asset,
                command = replayCommand,
                accountId = accountId,
                userId = userId,
            ),
            existingTx,
        )
        return savedTx
    }

    @Transactional
    open fun delete(
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
        val asset =
            assetRepository.findById(holding.assetId)
                ?: throw NoSuchElementException("Asset ${holding.assetId} not found")

        val deleted = investmentTransactionRepository.softDelete(id, accountId)
        if (!deleted) return false

        lifecycleOrchestrator.onDelete(
            InvestmentTransactionContext(
                transaction = tx,
                holding = holding,
                asset = asset,
                command = null,
                accountId = accountId,
                userId = tx.createdByUserId,
            ),
        )
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
    @Transactional
    open fun deleteHolding(
        id: UUID,
        accountId: UUID,
    ): Boolean {
        val holding =
            holdingRepository.findById(id, accountId)
                ?: throw NotFoundException(
                    errorCode = BankingErrorCode.INVESTMENT_HOLDING_NOT_FOUND,
                    resourceType = "Holding",
                    resourceId = id,
                )
        if (holding.source != InvestmentSource.MANUAL) {
            throw ConflictException(
                errorCode = BankingErrorCode.INVESTMENT_HOLDING_DELETE_NOT_ALLOWED,
                field = "id",
            )
        }

        val transactions = investmentTransactionRepository.findByHolding(id, accountId)
        if (!transactions.all { it.source == InvestmentSource.MANUAL }) {
            throw ConflictException(
                errorCode = BankingErrorCode.INVESTMENT_HOLDING_DELETE_NOT_ALLOWED,
                field = "id",
            )
        }
        val asset =
            assetRepository.findById(holding.assetId)
                ?: throw NoSuchElementException("Asset ${holding.assetId} not found")

        val deleteContexts =
            transactions.map { transaction ->
                InvestmentTransactionContext(
                    transaction = transaction,
                    holding = holding,
                    asset = asset,
                    command = null,
                    accountId = accountId,
                    userId = transaction.createdByUserId,
                )
            }
        lifecycleOrchestrator.onDeleteBatch(deleteContexts)

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

        val recalcFrom =
            transactions.minOfOrNull { it.tradeDate }
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
     * - **WITHHOLDING_TAX**: quantity decreases by deducted quotas; averageCost unchanged.
     * - **Other income / tax / penalty events**: no quantity or cost change.
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

            WITHHOLDING_TAX -> {
                requireNotNull(qty) { "quantity (cotas deducted) is required for WITHHOLDING_TAX" }
                require(qty > BigDecimal.ZERO) { "quantity must be positive for WITHHOLDING_TAX" }
                require(holding.quantity >= qty) {
                    "Come-cotas quantity ($qty) exceeds current holding quantity (${holding.quantity})"
                }
                require(asset.assetType == AssetType.MUTUAL_FUND) {
                    "WITHHOLDING_TAX is only valid for MUTUAL_FUND holdings"
                }
            }

            STOCK_LENDING -> {
                require(asset.assetType in STOCK_LENDING_ELIGIBLE_ASSET_TYPES) {
                    "does not support stock lending for ${asset.assetType}"
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
        require(financialAccountRepository.existsByIdAndAccountId(financialAccountId, accountId)) {
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
     * When [unitPrice] is omitted on PURCHASE/SUBSCRIPTION, infers it from [grossValue] / [quantity]
     * using [HoldingPositionEvolution.COST_SCALE] so persisted rows replay correctly.
     * If the client sends [unitPrice], it is kept as-is.
     */
    private fun inferPurchaseUnitPriceIfAbsent(
        type: InvestmentTransactionType,
        quantity: BigDecimal?,
        unitPrice: BigDecimal?,
        grossValue: BigDecimal,
    ): BigDecimal? {
        if (unitPrice != null) return null
        if (type != PURCHASE && type != SUBSCRIPTION) return null
        val qty = quantity ?: return null
        if (qty <= BigDecimal.ZERO || grossValue <= BigDecimal.ZERO) return null
        return grossValue.divide(qty, HoldingPositionEvolution.COST_SCALE, RoundingMode.HALF_UP)
    }

    private fun RecordInvestmentTransactionCommand.withInferredPurchaseUnitPrice(): RecordInvestmentTransactionCommand =
        inferPurchaseUnitPriceIfAbsent(type, quantity, unitPrice, grossValue)?.let { copy(unitPrice = it) } ?: this

    private fun UpdateInvestmentTransactionCommand.withInferredPurchaseUnitPrice(): UpdateInvestmentTransactionCommand =
        inferPurchaseUnitPriceIfAbsent(type, quantity, unitPrice, grossValue)?.let { copy(unitPrice = it) } ?: this

    private fun recomputeHoldingAfterDelete(
        existingHolding: Holding,
        accountId: UUID,
    ) {
        val now = Instant.now()
        val holdingId = requireNotNull(existingHolding.id) { "Holding id must not be null for delete recomputation" }
        val rebuiltHolding = recomputeHoldingState(existingHolding, accountId, null, now)
        if (rebuiltHolding == null) {
            pendingActionRepository.cancelByEntityAndType(
                "HOLDING",
                holdingId,
                PendingActionType.INVESTMENT_MATURED,
                now,
            )
            pendingActionRepository.cancelByEntityAndType(
                "HOLDING",
                holdingId,
                PendingActionType.COUPON_PAYMENT_DUE,
                now,
            )
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
        val latestTransaction = remainingTransactions.last()
        lifecycleOrchestrator.onUpdate(
            InvestmentTransactionContext(
                transaction = null,
                holding = saved,
                asset = asset,
                command = null,
                accountId = accountId,
                userId = latestTransaction.createdByUserId,
            ),
            latestTransaction,
        )
        return saved
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

private fun AssetType.isFundAsset(): Boolean =
    this == AssetType.MUTUAL_FUND || this == AssetType.PGBL || this == AssetType.VGBL
