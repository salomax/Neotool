package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.*
import io.github.salomax.neotool.banking.domain.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingAction
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionRepository
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionStatus
import io.github.salomax.neotool.banking.domain.pendingaction.PendingActionSeverity
import io.github.salomax.neotool.banking.domain.pendingaction.PendingActionType
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
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.math.RoundingMode.HALF_UP
import java.time.Instant
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
) {
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
     * Steps 4–6 run inside a single `@Transactional` boundary in the repositories.
     */
    fun record(
        command: RecordInvestmentTransactionCommand,
        accountId: UUID,
        userId: UUID,
    ): InvestmentTransaction {
        requireAccountOwnsFinancialAccount(command.financialAccountId, accountId)
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
        val updatedHolding = applyEffect(holding, command, now)

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

        val savedTx = investmentTransactionRepository.save(transaction)
        syncMaturityPendingAction(savedHolding, accountId)
        return savedTx
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

        recomputeHoldingAfterDelete(holding, accountId)
        return true
    }

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

        transactions.forEach { transaction ->
            val transactionId = requireNotNull(transaction.id) { "Investment transaction id must not be null" }
            val deleted = investmentTransactionRepository.softDelete(transactionId, accountId)
            check(deleted) { "Failed to delete investment transaction $transactionId for holding $id" }
        }

        pendingActionRepository.cancelByEntityAndType(
            entityType = "HOLDING",
            entityId = id,
            type = PendingActionType.INVESTMENT_MATURED,
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
    private fun applyEffect(
        holding: Holding,
        command: RecordInvestmentTransactionCommand,
        now: Instant,
    ): Holding {
        val effectiveHolding = holding.withMissingFixedIncomeTerms(command)
        val qty = command.quantity
        val price = command.unitPrice

        return when (command.type) {
            PURCHASE, SUBSCRIPTION -> {
                requireNotNull(qty) { "quantity is required for ${command.type}" }
                requireNotNull(price) { "unitPrice is required for ${command.type}" }
                val newQty = effectiveHolding.quantity + qty
                val newCost =
                    if (effectiveHolding.quantity.signum() == 0) {
                        price
                    } else {
                        (effectiveHolding.quantity * effectiveHolding.averageCost + qty * price)
                            .divide(newQty, COST_SCALE, HALF_UP)
                    }
                effectiveHolding.copy(quantity = newQty, averageCost = newCost, updatedAt = now)
            }

            SALE -> {
                requireNotNull(qty) { "quantity is required for SALE" }
                require(effectiveHolding.quantity >= qty) {
                    "Insufficient quantity for sale: have ${effectiveHolding.quantity}, selling $qty"
                }
                // averageCost is NOT recalculated on sale (RFB custo médio rule)
                effectiveHolding.copy(quantity = effectiveHolding.quantity - qty, updatedAt = now)
            }

            MATURITY -> {
                // If quantity is provided, it's a partial maturity; null = full position matures
                val closingQty = qty ?: effectiveHolding.quantity
                require(effectiveHolding.quantity >= closingQty) {
                    "Maturity quantity ($closingQty) exceeds current holding quantity (${effectiveHolding.quantity})"
                }
                effectiveHolding.copy(quantity = effectiveHolding.quantity - closingQty, updatedAt = now)
            }

            STOCK_SPLIT, BONUS_SHARES -> {
                // Shares received at zero cost — total cost basis is preserved, spread across more shares
                requireNotNull(qty) { "quantity (new shares received) is required for ${command.type}" }
                require(qty > BigDecimal.ZERO) { "quantity must be positive for ${command.type}" }
                val newQty = effectiveHolding.quantity + qty
                val newCost =
                    if (newQty.signum() == 0) BigDecimal.ZERO
                    else (effectiveHolding.quantity * effectiveHolding.averageCost).divide(newQty, COST_SCALE, HALF_UP)
                effectiveHolding.copy(quantity = newQty, averageCost = newCost, updatedAt = now)
            }

            REVERSE_SPLIT -> {
                // Shares consolidated — total cost basis is preserved, concentrated in fewer shares
                requireNotNull(qty) { "quantity (shares removed) is required for REVERSE_SPLIT" }
                require(qty > BigDecimal.ZERO) { "quantity must be positive for REVERSE_SPLIT" }
                require(effectiveHolding.quantity >= qty) {
                    "Reverse split quantity ($qty) exceeds current holding quantity (${effectiveHolding.quantity})"
                }
                val newQty = effectiveHolding.quantity - qty
                val newCost =
                    if (newQty.signum() == 0) BigDecimal.ZERO
                    else (effectiveHolding.quantity * effectiveHolding.averageCost).divide(newQty, COST_SCALE, HALF_UP)
                effectiveHolding.copy(quantity = newQty, averageCost = newCost, updatedAt = now)
            }

            CUSTODY_TRANSFER, OWNERSHIP_TRANSFER, QUOTA_TRANSFER -> {
                when {
                    command.direction == INFLOW && qty != null && price != null -> {
                        // Receiving transferred shares: treat as a purchase at the transferred basis
                        val newQty = effectiveHolding.quantity + qty
                        val newCost =
                            if (effectiveHolding.quantity.signum() == 0) {
                                price
                            } else {
                                (effectiveHolding.quantity * effectiveHolding.averageCost + qty * price)
                                    .divide(newQty, COST_SCALE, HALF_UP)
                            }
                        effectiveHolding.copy(quantity = newQty, averageCost = newCost, updatedAt = now)
                    }

                    command.direction == OUTFLOW && qty != null -> {
                        // Sending shares: treat as a sale (average cost unchanged)
                        require(effectiveHolding.quantity >= qty) {
                            "Transfer quantity ($qty) exceeds current holding quantity (${effectiveHolding.quantity})"
                        }
                        effectiveHolding.copy(quantity = effectiveHolding.quantity - qty, updatedAt = now)
                    }

                    else -> effectiveHolding.copy(updatedAt = now)
                }
            }

            // Pure monetary events — no quantity or cost basis change
            DIVIDEND,
            INTEREST_ON_EQUITY,
            INTEREST_PAYMENT,
            AMORTIZATION,
            PRIZE,
            STOCK_LENDING,
            WITHHOLDING_TAX,
            FINE,
            LATE_PAYMENT_INTEREST,
            CANCELLATION,
            OTHER,
            -> effectiveHolding.copy(updatedAt = now)
        }
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
        val holdingId = requireNotNull(existingHolding.id) { "Holding id must not be null for delete recomputation" }
        val now = Instant.now()
        val remainingTransactions =
            investmentTransactionRepository.findByHolding(holdingId, accountId)
                .sortedWith(compareBy<InvestmentTransaction>({ it.tradeDate }, { it.createdAt }))

        if (remainingTransactions.isEmpty()) {
            pendingActionRepository.cancelByEntityAndType("HOLDING", holdingId, PendingActionType.INVESTMENT_MATURED, now)
            holdingRepository.softDelete(holdingId, accountId)
            return
        }

        val replayBase =
            existingHolding.copy(
                quantity = BigDecimal.ZERO,
                averageCost = BigDecimal.ZERO,
                updatedAt = now,
            )

        val rebuiltHolding =
            remainingTransactions.fold(replayBase) { holding, transaction ->
                applyEffect(
                    holding = holding,
                    command = transaction.toReplayCommand(existingHolding),
                    now = now,
                )
            }

        val saved = holdingRepository.update(rebuiltHolding.copy(updatedAt = now))
        syncMaturityPendingAction(saved, accountId)
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
        /** Scale for averageCost arithmetic — 8 decimal places to handle crypto and fractional shares. */
        private const val COST_SCALE = 8

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
    )
}

private fun AssetType.isFundAsset(): Boolean =
    this == AssetType.MUTUAL_FUND || this == AssetType.PGBL || this == AssetType.VGBL
