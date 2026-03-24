package io.github.salomax.neotool.banking.repo.investment

import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.repo.investment.InvestmentTransactionRepository
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

    @Transactional
    override fun save(transaction: InvestmentTransaction): InvestmentTransaction {
        val entity = InvestmentTransactionMapper.toEntity(transaction)
        return InvestmentTransactionMapper.toDomain(entityRepository.save(entity))
    }

    override fun softDelete(
        id: UUID,
        accountId: UUID,
    ): Boolean = entityRepository.softDelete(id, accountId, Instant.now()) > 0
}
