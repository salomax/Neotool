package io.github.salomax.neotool.banking.repo.investment

import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.model.investment.AssetMapper
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
open class DefaultAssetRepository(
    private val entityRepository: AssetEntityRepository,
) : AssetRepository {
    override fun findById(id: UUID): Asset? =
        entityRepository.findById(id).orElse(null)?.let(AssetMapper::toDomain)

    override fun findByIds(ids: Collection<UUID>): List<Asset> =
        if (ids.isEmpty()) emptyList()
        else entityRepository.findByIdIn(ids).map(AssetMapper::toDomain)

    override fun findAllWithTicker(): List<Asset> =
        entityRepository.findByTickerIsNotNull().map(AssetMapper::toDomain)

    override fun findAllWithFundCnpj(): List<Asset> =
        entityRepository.findByFundCnpjIsNotNull().map(AssetMapper::toDomain)

    override fun findByTicker(ticker: String): Asset? =
        entityRepository.findByTicker(ticker)?.let(AssetMapper::toDomain)

    override fun findByIsinCode(isinCode: String): Asset? =
        entityRepository.findByIsinCode(isinCode)?.let(AssetMapper::toDomain)

    override fun findByFundCnpj(fundCnpj: String): Asset? =
        entityRepository.findByFundCnpj(fundCnpj)?.let(AssetMapper::toDomain)

    override fun findByProviderId(providerId: String): Asset? =
        entityRepository.findByProviderId(providerId)?.let(AssetMapper::toDomain)

    override fun findByNameAndTypeAndIssuer(
        name: String,
        assetType: AssetType,
        issuerName: String?,
    ): Asset? =
        entityRepository.findByNameAndAssetTypeAndIssuerName(name, assetType, issuerName)?.let(AssetMapper::toDomain)

    @Transactional
    override fun save(asset: Asset): Asset {
        val entity = AssetMapper.toEntity(asset)
        return AssetMapper.toDomain(entityRepository.save(entity))
    }

    @Transactional
    override fun update(asset: Asset): Asset {
        val id = requireNotNull(asset.id) { "Asset id is required for update" }
        val entity = entityRepository.findById(id).orElseThrow {
            NoSuchElementException("Asset $id not found")
        }
        AssetMapper.updateEntity(entity, asset)
        return AssetMapper.toDomain(entityRepository.update(entity))
    }
}
