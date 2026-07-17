package io.github.salomax.neotool.banking.repo.investment

import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.model.investment.InvestmentTransactionMapper
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Singleton
open class DefaultInvestmentTransactionRepository(
    private val entityRepository: InvestmentTransactionEntityRepository,
) : InvestmentTransactionRepository {
    override fun findByHolding(
        holdingId: UUID,
        accountId: UUID,
    ): List<InvestmentTransaction> =
        entityRepository
            .findByHoldingIdAndAccountIdAndDeletedAtIsNullOrderByTradeDateDesc(holdingId, accountId)
            .map(InvestmentTransactionMapper::toDomain)

    override fun findByAccount(accountId: UUID): List<InvestmentTransaction> =
        entityRepository
            .findByAccountIdAndDeletedAtIsNullOrderByTradeDateDesc(accountId)
            .map(InvestmentTransactionMapper::toDomain)

    override fun findByFinancialAccount(
        financialAccountId: UUID,
        accountId: UUID,
    ): List<InvestmentTransaction> =
        entityRepository
            .findByFinancialAccountIdAndAccountIdAndDeletedAtIsNullOrderByTradeDateDesc(
                financialAccountId,
                accountId,
            )
            .map(InvestmentTransactionMapper::toDomain)

    override fun findByHoldingIds(
        holdingIds: Collection<UUID>,
        accountId: UUID,
    ): List<InvestmentTransaction> {
        if (holdingIds.isEmpty()) return emptyList()
        return entityRepository
            .findByHoldingIdInAndAccountIdAndDeletedAtIsNullOrderByTradeDateDesc(holdingIds, accountId)
            .map(InvestmentTransactionMapper::toDomain)
    }

    override fun findByTypeAndDateRange(
        accountId: UUID,
        type: InvestmentTransactionType,
        from: LocalDate,
        to: LocalDate,
    ): List<InvestmentTransaction> =
        entityRepository
            .findByTypeAndDateRange(accountId, type.name, from, to)
            .map(InvestmentTransactionMapper::toDomain)

    override fun findById(
        id: UUID,
        accountId: UUID,
    ): InvestmentTransaction? =
        entityRepository
            .findByIdAndAccountIdAndDeletedAtIsNull(id, accountId)
            ?.let(InvestmentTransactionMapper::toDomain)

    override fun existsByProviderTransactionId(
        financialAccountId: UUID,
        providerTransactionId: String,
    ): Boolean =
        entityRepository.existsByFinancialAccountIdAndProviderTransactionIdAndDeletedAtIsNull(
            financialAccountId,
            providerTransactionId,
        )

    override fun findByProviderTransactionId(
        financialAccountId: UUID,
        accountId: UUID,
        providerTransactionId: String,
    ): InvestmentTransaction? =
        entityRepository
            .findByFinancialAccountIdAndAccountIdAndProviderTransactionIdAndDeletedAtIsNull(
                financialAccountId,
                accountId,
                providerTransactionId,
            )
            ?.let(InvestmentTransactionMapper::toDomain)

    override fun existsWithholdingTaxForHoldingOnTradeDate(
        holdingId: UUID,
        accountId: UUID,
        tradeDate: LocalDate,
    ): Boolean = entityRepository.existsWithholdingTaxForHoldingOnTradeDate(holdingId, accountId, tradeDate)

    override fun existsWithholdingTaxForHoldingInDateRange(
        holdingId: UUID,
        accountId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): Boolean = entityRepository.existsWithholdingTaxForHoldingInDateRange(holdingId, accountId, from, to)

    @Transactional
    override fun save(transaction: InvestmentTransaction): InvestmentTransaction {
        val entity = InvestmentTransactionMapper.toEntity(transaction)
        return InvestmentTransactionMapper.toDomain(entityRepository.save(entity))
    }

    @Transactional
    override fun update(transaction: InvestmentTransaction): InvestmentTransaction {
        val id = requireNotNull(transaction.id) { "InvestmentTransaction id is required for update" }
        val existing =
            entityRepository.findById(id).orElseThrow {
                NoSuchElementException("InvestmentTransaction $id not found")
            }
        val updated = InvestmentTransactionMapper.toEntity(transaction)
        existing.holdingId = updated.holdingId
        existing.financialAccountId = updated.financialAccountId
        existing.accountId = updated.accountId
        existing.createdByUserId = updated.createdByUserId
        existing.direction = updated.direction
        existing.type = updated.type
        existing.typeAdditionalInfo = updated.typeAdditionalInfo
        existing.quantity = updated.quantity
        existing.unitPrice = updated.unitPrice
        existing.grossValue = updated.grossValue
        existing.netValue = updated.netValue
        existing.incomeTax = updated.incomeTax
        existing.iof = updated.iof
        existing.indexerPercentage = updated.indexerPercentage
        existing.fixedAnnualRate = updated.fixedAnnualRate
        existing.priceFactor = updated.priceFactor
        existing.tradeDate = updated.tradeDate
        existing.settlementDate = updated.settlementDate
        existing.source = updated.source
        existing.providerTransactionId = updated.providerTransactionId
        existing.importJobId = updated.importJobId
        existing.deletedAt = updated.deletedAt
        existing.createdAt = updated.createdAt
        existing.updatedAt = updated.updatedAt
        existing.brokerNote = updated.brokerNote
        existing.brokerNote?.investmentTransaction = existing
        return InvestmentTransactionMapper.toDomain(entityRepository.update(existing))
    }

    override fun softDelete(
        id: UUID,
        accountId: UUID,
    ): Boolean = entityRepository.softDelete(id, accountId, Instant.now()) > 0
}
