package io.github.salomax.neotool.banking.domain.investment

import io.github.salomax.neotool.banking.domain.account.FinancialAccountRepository
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
        val holding = existingHolding ?: createHolding(command, accountId, now)

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
                agreedRate = command.agreedRate,
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

        return investmentTransactionRepository.save(transaction)
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
        return investmentTransactionRepository.softDelete(id, accountId)
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
        val qty = command.quantity
        val price = command.unitPrice

        return when (command.type) {
            PURCHASE, SUBSCRIPTION -> {
                requireNotNull(qty) { "quantity is required for ${command.type}" }
                requireNotNull(price) { "unitPrice is required for ${command.type}" }
                val newQty = holding.quantity + qty
                val newCost =
                    if (holding.quantity.signum() == 0) {
                        price
                    } else {
                        (holding.quantity * holding.averageCost + qty * price)
                            .divide(newQty, COST_SCALE, HALF_UP)
                    }
                holding.copy(quantity = newQty, averageCost = newCost, updatedAt = now)
            }

            SALE -> {
                requireNotNull(qty) { "quantity is required for SALE" }
                require(holding.quantity >= qty) {
                    "Insufficient quantity for sale: have ${holding.quantity}, selling $qty"
                }
                // averageCost is NOT recalculated on sale (RFB custo médio rule)
                holding.copy(quantity = holding.quantity - qty, updatedAt = now)
            }

            MATURITY -> {
                // If quantity is provided, it's a partial maturity; null = full position matures
                val closingQty = qty ?: holding.quantity
                require(holding.quantity >= closingQty) {
                    "Maturity quantity ($closingQty) exceeds current holding quantity (${holding.quantity})"
                }
                holding.copy(quantity = holding.quantity - closingQty, updatedAt = now)
            }

            STOCK_SPLIT, BONUS_SHARES -> {
                // Shares received at zero cost — total cost basis is preserved, spread across more shares
                requireNotNull(qty) { "quantity (new shares received) is required for ${command.type}" }
                require(qty > BigDecimal.ZERO) { "quantity must be positive for ${command.type}" }
                val newQty = holding.quantity + qty
                val newCost =
                    if (newQty.signum() == 0) BigDecimal.ZERO
                    else (holding.quantity * holding.averageCost).divide(newQty, COST_SCALE, HALF_UP)
                holding.copy(quantity = newQty, averageCost = newCost, updatedAt = now)
            }

            REVERSE_SPLIT -> {
                // Shares consolidated — total cost basis is preserved, concentrated in fewer shares
                requireNotNull(qty) { "quantity (shares removed) is required for REVERSE_SPLIT" }
                require(qty > BigDecimal.ZERO) { "quantity must be positive for REVERSE_SPLIT" }
                require(holding.quantity >= qty) {
                    "Reverse split quantity ($qty) exceeds current holding quantity (${holding.quantity})"
                }
                val newQty = holding.quantity - qty
                val newCost =
                    if (newQty.signum() == 0) BigDecimal.ZERO
                    else (holding.quantity * holding.averageCost).divide(newQty, COST_SCALE, HALF_UP)
                holding.copy(quantity = newQty, averageCost = newCost, updatedAt = now)
            }

            CUSTODY_TRANSFER, OWNERSHIP_TRANSFER, QUOTA_TRANSFER -> {
                when {
                    command.direction == INFLOW && qty != null && price != null -> {
                        // Receiving transferred shares: treat as a purchase at the transferred basis
                        val newQty = holding.quantity + qty
                        val newCost =
                            if (holding.quantity.signum() == 0) {
                                price
                            } else {
                                (holding.quantity * holding.averageCost + qty * price)
                                    .divide(newQty, COST_SCALE, HALF_UP)
                            }
                        holding.copy(quantity = newQty, averageCost = newCost, updatedAt = now)
                    }

                    command.direction == OUTFLOW && qty != null -> {
                        // Sending shares: treat as a sale (average cost unchanged)
                        require(holding.quantity >= qty) {
                            "Transfer quantity ($qty) exceeds current holding quantity (${holding.quantity})"
                        }
                        holding.copy(quantity = holding.quantity - qty, updatedAt = now)
                    }

                    else -> holding.copy(updatedAt = now)
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
            -> holding.copy(updatedAt = now)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun createHolding(
        command: RecordInvestmentTransactionCommand,
        accountId: UUID,
        now: Instant,
    ): Holding =
        Holding(
            financialAccountId = command.financialAccountId,
            accountId = accountId,
            assetId = command.assetId,
            quantity = BigDecimal.ZERO,
            averageCost = BigDecimal.ZERO,
            indexer = command.indexer,
            indexerPercentage = command.indexerPercentage,
            preFixedRate = command.preFixedRate,
            issueDate = command.issueDate,
            maturityDate = command.maturityDate,
            gracePeriodDate = command.gracePeriodDate,
            isTaxExempt = command.isTaxExempt,
            source = command.source,
            providerInvestmentId = command.providerInvestmentId,
            createdAt = now,
            updatedAt = now,
        )

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

    companion object {
        /** Scale for averageCost arithmetic — 8 decimal places to handle crypto and fractional shares. */
        private const val COST_SCALE = 8
    }
}
