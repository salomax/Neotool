package io.github.salomax.neotool.banking.repo.investment

import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.HoldingRepository
import io.github.salomax.neotool.banking.model.investment.HoldingMapper
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.time.Instant
import java.util.UUID

@Singleton
open class DefaultHoldingRepository(
    private val entityRepository: HoldingEntityRepository,
) : HoldingRepository {
    override fun findByFinancialAccount(
        financialAccountId: UUID,
        accountId: UUID,
    ): List<Holding> =
        entityRepository
            .findByFinancialAccountIdAndAccountIdAndDeletedAtIsNull(financialAccountId, accountId)
            .map(HoldingMapper::toDomain)

    override fun findByAccount(accountId: UUID): List<Holding> =
        entityRepository
            .findByAccountIdAndDeletedAtIsNull(accountId)
            .map(HoldingMapper::toDomain)

    override fun findById(
        id: UUID,
        accountId: UUID,
    ): Holding? =
        entityRepository
            .findByIdAndAccountIdAndDeletedAtIsNull(id, accountId)
            ?.let(HoldingMapper::toDomain)

    override fun findByFinancialAccountAndAsset(
        financialAccountId: UUID,
        assetId: UUID,
    ): Holding? =
        entityRepository
            .findByFinancialAccountIdAndAssetIdAndDeletedAtIsNull(financialAccountId, assetId)
            ?.let(HoldingMapper::toDomain)

    override fun findByProviderInvestmentId(
        financialAccountId: UUID,
        providerInvestmentId: String,
    ): Holding? =
        entityRepository
            .findByFinancialAccountIdAndProviderInvestmentIdAndDeletedAtIsNull(
                financialAccountId,
                providerInvestmentId,
            )
            ?.let(HoldingMapper::toDomain)

    @Transactional
    override fun save(holding: Holding): Holding {
        val entity = HoldingMapper.toEntity(holding)
        return HoldingMapper.toDomain(entityRepository.save(entity))
    }

    @Transactional
    override fun update(holding: Holding): Holding {
        val id = requireNotNull(holding.id) { "Holding id is required for update" }
        val entity =
            entityRepository.findByIdAndAccountIdAndDeletedAtIsNull(id, holding.accountId)
                ?: throw NoSuchElementException("Holding $id not found")
        HoldingMapper.updateEntity(entity, holding)
        return HoldingMapper.toDomain(entityRepository.update(entity))
    }

    override fun softDelete(
        id: UUID,
        accountId: UUID,
    ): Boolean = entityRepository.softDelete(id, accountId, Instant.now()) > 0
}
