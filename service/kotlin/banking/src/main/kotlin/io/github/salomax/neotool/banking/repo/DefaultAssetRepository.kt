package io.github.salomax.neotool.banking.repo

import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.AssetRepository
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

    override fun findByTicker(ticker: String): Asset? =
        entityRepository.findByTicker(ticker)?.let(AssetMapper::toDomain)

    override fun findByIsinCode(isinCode: String): Asset? =
        entityRepository.findByIsinCode(isinCode)?.let(AssetMapper::toDomain)

    override fun findByFundCnpj(fundCnpj: String): Asset? =
        entityRepository.findByFundCnpj(fundCnpj)?.let(AssetMapper::toDomain)

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
